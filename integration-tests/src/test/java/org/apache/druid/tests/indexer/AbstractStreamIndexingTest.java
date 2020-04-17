/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.tests.indexer;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.apache.druid.indexing.overlord.supervisor.SupervisorStateManager;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.testing.utils.DruidClusterAdminClient;
import org.apache.druid.testing.utils.ITRetryUtil;
import org.apache.druid.testing.utils.StreamAdminClient;
import org.apache.druid.testing.utils.StreamEventWriter;
import org.apache.druid.testing.utils.WikipediaStreamEventStreamGenerator;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.io.Closeable;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public abstract class AbstractStreamIndexingTest extends AbstractITBatchIndexTest
{
  static final DateTime FIRST_EVENT_TIME = DateTimes.of(1994, 4, 29, 1, 0);
  // format for the querying interval
  static final DateTimeFormatter INTERVAL_FMT = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:'00Z'");
  // format for the expected timestamp in a query response
  static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'.000Z'");
  static final int EVENTS_PER_SECOND = 6;
  static final int TOTAL_NUMBER_OF_SECOND = 10;
  static final Logger LOG = new Logger(AbstractStreamIndexingTest.class);
  // Since this integration test can terminates or be killed un-expectedly, this tag is added to all streams created
  // to help make stream clean up easier. (Normally, streams should be cleanup automattically by the teardown method)
  // The value to this tag is a timestamp that can be used by a lambda function to remove unused stream.
  private static final String STREAM_EXPIRE_TAG = "druid-ci-expire-after";
  private static final int STREAM_SHARD_COUNT = 2;
  private static final long WAIT_TIME_MILLIS = 3 * 60 * 1000L;
  private static final String INDEXER_FILE_LEGACY_PARSER = "/indexer/stream_supervisor_spec_legacy_parser.json";
  private static final String INDEXER_FILE_INPUT_FORMAT = "/indexer/stream_supervisor_spec_input_format.json";
  private static final String QUERIES_FILE = "/indexer/stream_index_queries.json";
  private static final long CYCLE_PADDING_MS = 100;

  @Inject
  private DruidClusterAdminClient druidClusterAdminClient;

  String streamName;
  String fullDatasourceName;

  private StreamAdminClient streamAdminClient;
  private StreamEventWriter streamEventWriter;
  private WikipediaStreamEventStreamGenerator wikipediaStreamEventGenerator;
  private Function<String, String> streamIngestionPropsTransform;
  private Function<String, String> streamQueryPropsTransform;
  private String supervisorId;
  private int secondsToGenerateRemaining;

  abstract StreamAdminClient getStreamAdminClient() throws Exception;
  abstract StreamEventWriter getStreamEventWriter() throws Exception;
  abstract Function<String, String> getStreamIngestionPropsTransform();
  abstract Function<String, String> getStreamQueryPropsTransform();

  @BeforeClass
  public void beforeClass() throws Exception
  {
    streamAdminClient = getStreamAdminClient();
    streamEventWriter = getStreamEventWriter();
    wikipediaStreamEventGenerator = new WikipediaStreamEventStreamGenerator(EVENTS_PER_SECOND, CYCLE_PADDING_MS);
  }

  @AfterClass
  public void tearDown()
  {
    wikipediaStreamEventGenerator.shutdown();
    streamEventWriter.shutdown();
  }

  @BeforeMethod
  public void before() throws Exception
  {
    streamName = "kinesis_index_test_" + UUID.randomUUID();
    String datasource = "kinesis_indexing_service_test_" + UUID.randomUUID();
    Map<String, String> tags = ImmutableMap.of(STREAM_EXPIRE_TAG, Long.toString(DateTimes.nowUtc().plusMinutes(30).getMillis()));
    streamAdminClient.createStream(streamName, STREAM_SHARD_COUNT, tags);
    ITRetryUtil.retryUntil(
        () -> streamAdminClient.isStreamActive(streamName),
        true,
        10000,
        30,
        "Wait for stream active"
    );
    secondsToGenerateRemaining = TOTAL_NUMBER_OF_SECOND;
    fullDatasourceName = datasource + config.getExtraDatasourceNameSuffix();
    streamIngestionPropsTransform = getStreamIngestionPropsTransform();
    streamQueryPropsTransform = getStreamQueryPropsTransform();
  }

  @AfterMethod
  public void teardown()
  {
    try {
      streamEventWriter.flush();
      indexer.shutdownSupervisor(supervisorId);
    }
    catch (Exception e) {
      // Best effort cleanup as the supervisor may have already went Bye-Bye
    }
    try {
      unloader(fullDatasourceName);
    }
    catch (Exception e) {
      // Best effort cleanup as the datasource may have already went Bye-Bye
    }
    try {
      streamAdminClient.deleteStream(streamName);
    }
    catch (Exception e) {
      // Best effort cleanup as the stream may have already went Bye-Bye
    }
  }

  void doTestIndexDataWithLegacyParserStableState() throws Exception
  {
    try (
        final Closeable ignored1 = unloader(fullDatasourceName)
    ) {
      final String taskSpec = streamIngestionPropsTransform.apply(getResourceAsString(INDEXER_FILE_LEGACY_PARSER));
      LOG.info("supervisorSpec: [%s]\n", taskSpec);
      // Start supervisor
      supervisorId = indexer.submitSupervisor(taskSpec);
      LOG.info("Submitted supervisor");
      // Start data generator
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, TOTAL_NUMBER_OF_SECOND, FIRST_EVENT_TIME);
      verifyIngestedData(supervisorId);
    }
  }

  void doTestIndexDataWithInputFormatStableState() throws Exception
  {
    try (
        final Closeable ignored1 = unloader(fullDatasourceName)
    ) {
      final String taskSpec = streamIngestionPropsTransform.apply(getResourceAsString(INDEXER_FILE_INPUT_FORMAT));
      LOG.info("supervisorSpec: [%s]\n", taskSpec);
      // Start supervisor
      supervisorId = indexer.submitSupervisor(taskSpec);
      LOG.info("Submitted supervisor");
      // Start data generator
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, TOTAL_NUMBER_OF_SECOND, FIRST_EVENT_TIME);
      verifyIngestedData(supervisorId);
    }
  }

  void doTestIndexDataWithLosingCoordinator() throws Exception
  {
    testIndexWithLosingNodeHelper(() -> druidClusterAdminClient.restartCoordinatorContainer(), () -> druidClusterAdminClient.waitUntilCoordinatorReady());
  }

  void doTestIndexDataWithLosingOverlord() throws Exception
  {
    testIndexWithLosingNodeHelper(() -> druidClusterAdminClient.restartIndexerContainer(), () -> druidClusterAdminClient.waitUntilIndexerReady());
  }

  void doTestIndexDataWithLosingHistorical() throws Exception
  {
    testIndexWithLosingNodeHelper(() -> druidClusterAdminClient.restartHistoricalContainer(), () -> druidClusterAdminClient.waitUntilHistoricalReady());
  }

  void doTestIndexDataWithStartStopSupervisor() throws Exception
  {
    try (
        final Closeable ignored1 = unloader(fullDatasourceName)
    ) {
      final String taskSpec = streamIngestionPropsTransform.apply(getResourceAsString(INDEXER_FILE_INPUT_FORMAT));
      LOG.info("supervisorSpec: [%s]\n", taskSpec);
      // Start supervisor
      supervisorId = indexer.submitSupervisor(taskSpec);
      LOG.info("Submitted supervisor");
      // Start generating half of the data
      int secondsToGenerateFirstRound = TOTAL_NUMBER_OF_SECOND / 2;
      secondsToGenerateRemaining = secondsToGenerateRemaining - secondsToGenerateFirstRound;
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateFirstRound, FIRST_EVENT_TIME);
      // Verify supervisor is healthy before suspension
      ITRetryUtil.retryUntil(
          () -> SupervisorStateManager.BasicState.RUNNING.equals(indexer.getSupervisorStatus(supervisorId)),
          true,
          10000,
          30,
          "Waiting for supervisor to be healthy"
      );
      // Suspend the supervisor
      indexer.suspendSupervisor(supervisorId);
      // Start generating remainning half of the data
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateRemaining, FIRST_EVENT_TIME.plusSeconds(secondsToGenerateFirstRound));
      // Resume the supervisor
      indexer.resumeSupervisor(supervisorId);
      // Verify supervisor is healthy after suspension
      ITRetryUtil.retryUntil(
          () -> SupervisorStateManager.BasicState.RUNNING.equals(indexer.getSupervisorStatus(supervisorId)),
          true,
          10000,
          30,
          "Waiting for supervisor to be healthy"
      );
      // Verify that supervisor can catch up with the stream
      verifyIngestedData(supervisorId);
    }
  }

  void doTestIndexDataWithStreamReshardSplit() throws Exception
  {
    // Reshard the stream from STREAM_SHARD_COUNT to STREAM_SHARD_COUNT * 2
    testIndexWithStreamReshardHelper(STREAM_SHARD_COUNT * 2);
  }

  void doTestIndexDataWithStreamReshardMerge() throws Exception
  {
    // Reshard the stream from STREAM_SHARD_COUNT to STREAM_SHARD_COUNT / 2
    testIndexWithStreamReshardHelper(STREAM_SHARD_COUNT / 2);
  }

  private void testIndexWithLosingNodeHelper(Runnable restartRunnable, Runnable waitForReadyRunnable) throws Exception
  {
    try (
        final Closeable ignored1 = unloader(fullDatasourceName)
    ) {
      final String taskSpec = streamIngestionPropsTransform.apply(getResourceAsString(INDEXER_FILE_INPUT_FORMAT));
      LOG.info("supervisorSpec: [%s]\n", taskSpec);
      // Start supervisor
      supervisorId = indexer.submitSupervisor(taskSpec);
      LOG.info("Submitted supervisor");
      // Start generating one third of the data (before restarting)
      int secondsToGenerateFirstRound = TOTAL_NUMBER_OF_SECOND / 3;
      secondsToGenerateRemaining = secondsToGenerateRemaining - secondsToGenerateFirstRound;
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateFirstRound, FIRST_EVENT_TIME);
      // Verify supervisor is healthy before restart
      ITRetryUtil.retryUntil(
          () -> SupervisorStateManager.BasicState.RUNNING.equals(indexer.getSupervisorStatus(supervisorId)),
          true,
          10000,
          30,
          "Waiting for supervisor to be healthy"
      );
      // Restart Druid process
      LOG.info("Restarting Druid process");
      restartRunnable.run();
      LOG.info("Restarted Druid process");
      // Start generating one third of the data (while restarting)
      int secondsToGenerateSecondRound = TOTAL_NUMBER_OF_SECOND / 3;
      secondsToGenerateRemaining = secondsToGenerateRemaining - secondsToGenerateSecondRound;
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateSecondRound, FIRST_EVENT_TIME.plusSeconds(secondsToGenerateFirstRound));
      // Wait for Druid process to be available
      LOG.info("Waiting for Druid process to be available");
      waitForReadyRunnable.run();
      LOG.info("Druid process is now available");
      // Start generating remainding data (after restarting)
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateRemaining, FIRST_EVENT_TIME.plusSeconds(secondsToGenerateFirstRound + secondsToGenerateSecondRound));
      // Verify supervisor is healthy
      ITRetryUtil.retryUntil(
          () -> SupervisorStateManager.BasicState.RUNNING.equals(indexer.getSupervisorStatus(supervisorId)),
          true,
          10000,
          30,
          "Waiting for supervisor to be healthy"
      );
      // Verify that supervisor ingested all data
      verifyIngestedData(supervisorId);
    }
  }

  private void testIndexWithStreamReshardHelper(int newShardCount) throws Exception
  {
    try (
        final Closeable ignored1 = unloader(fullDatasourceName)
    ) {
      final String taskSpec = streamIngestionPropsTransform.apply(getResourceAsString(INDEXER_FILE_INPUT_FORMAT));
      LOG.info("supervisorSpec: [%s]\n", taskSpec);
      // Start supervisor
      supervisorId = indexer.submitSupervisor(taskSpec);
      LOG.info("Submitted supervisor");
      // Start generating one third of the data (before resharding)
      int secondsToGenerateFirstRound = TOTAL_NUMBER_OF_SECOND / 3;
      secondsToGenerateRemaining = secondsToGenerateRemaining - secondsToGenerateFirstRound;
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateFirstRound, FIRST_EVENT_TIME);
      // Verify supervisor is healthy before resahrding
      ITRetryUtil.retryUntil(
          () -> SupervisorStateManager.BasicState.RUNNING.equals(indexer.getSupervisorStatus(supervisorId)),
          true,
          10000,
          30,
          "Waiting for supervisor to be healthy"
      );
      // Reshard the supervisor by split from STREAM_SHARD_COUNT to newShardCount and waits until the resharding starts
      streamAdminClient.updateShardCount(streamName, newShardCount, true);
      // Start generating one third of the data (while resharding)
      int secondsToGenerateSecondRound = TOTAL_NUMBER_OF_SECOND / 3;
      secondsToGenerateRemaining = secondsToGenerateRemaining - secondsToGenerateSecondRound;
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateSecondRound, FIRST_EVENT_TIME.plusSeconds(secondsToGenerateFirstRound));
      // Wait for stream to finish resharding
      ITRetryUtil.retryUntil(
          () -> streamAdminClient.isStreamActive(streamName),
          true,
          10000,
          30,
          "Waiting for stream to finish resharding"
      );
      ITRetryUtil.retryUntil(
          () -> streamAdminClient.getStreamShardCount(streamName) == newShardCount,
          true,
          10000,
          30,
          "Waiting for stream to finish resharding"
      );
      // Start generating remainding data (after resharding)
      wikipediaStreamEventGenerator.start(streamName, streamEventWriter, secondsToGenerateRemaining, FIRST_EVENT_TIME.plusSeconds(secondsToGenerateFirstRound + secondsToGenerateSecondRound));
      // Verify supervisor is healthy after resahrding
      ITRetryUtil.retryUntil(
          () -> SupervisorStateManager.BasicState.RUNNING.equals(indexer.getSupervisorStatus(supervisorId)),
          true,
          10000,
          30,
          "Waiting for supervisor to be healthy"
      );
      // Verify that supervisor can catch up with the stream
      verifyIngestedData(supervisorId);
    }
  }

  private void verifyIngestedData(String supervisorId) throws Exception
  {
    // Wait for supervisor to consume events
    LOG.info("Waiting for [%s] millis for stream indexing tasks to consume events", WAIT_TIME_MILLIS);
    Thread.sleep(WAIT_TIME_MILLIS);
    // Query data
    final String querySpec = streamQueryPropsTransform.apply(getResourceAsString(QUERIES_FILE));
    // this query will probably be answered from the indexing tasks but possibly from 2 historical segments / 2 indexing
    this.queryHelper.testQueriesFromString(querySpec, 2);
    LOG.info("Shutting down supervisor");
    indexer.shutdownSupervisor(supervisorId);
    // wait for all indexing tasks to finish
    LOG.info("Waiting for all indexing tasks to finish");
    ITRetryUtil.retryUntilTrue(
        () -> (indexer.getPendingTasks().size()
               + indexer.getRunningTasks().size()
               + indexer.getWaitingTasks().size()) == 0,
        "Waiting for Tasks Completion"
    );
    // wait for segments to be handed off
    ITRetryUtil.retryUntil(
        () -> coordinator.areSegmentsLoaded(fullDatasourceName),
        true,
        10000,
        30,
        "Real-time generated segments loaded"
    );

    // this query will be answered by at least 1 historical segment, most likely 2, and possibly up to all 4
    this.queryHelper.testQueriesFromString(querySpec, 2);
  }
  long getSumOfEventSequence(int numEvents)
  {
    return (numEvents * (1 + numEvents)) / 2;
  }
}

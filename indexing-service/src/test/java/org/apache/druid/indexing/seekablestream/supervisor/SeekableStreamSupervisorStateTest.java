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

package org.apache.druid.indexing.seekablestream.supervisor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.druid.data.input.impl.DimensionSchema;
import org.apache.druid.data.input.impl.DimensionsSpec;
import org.apache.druid.data.input.impl.JSONParseSpec;
import org.apache.druid.data.input.impl.StringDimensionSchema;
import org.apache.druid.data.input.impl.StringInputRowParser;
import org.apache.druid.data.input.impl.TimestampSpec;
import org.apache.druid.indexing.common.TestUtils;
import org.apache.druid.indexing.common.stats.RowIngestionMetersFactory;
import org.apache.druid.indexing.common.task.Task;
import org.apache.druid.indexing.common.task.TaskResource;
import org.apache.druid.indexing.overlord.DataSourceMetadata;
import org.apache.druid.indexing.overlord.IndexerMetadataStorageCoordinator;
import org.apache.druid.indexing.overlord.TaskMaster;
import org.apache.druid.indexing.overlord.TaskQueue;
import org.apache.druid.indexing.overlord.TaskRunner;
import org.apache.druid.indexing.overlord.TaskRunnerListener;
import org.apache.druid.indexing.overlord.TaskStorage;
import org.apache.druid.indexing.seekablestream.SeekableStreamDataSourceMetadata;
import org.apache.druid.indexing.seekablestream.SeekableStreamEndSequenceNumbers;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTask;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTaskClient;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTaskClientFactory;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTaskIOConfig;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTaskRunner;
import org.apache.druid.indexing.seekablestream.SeekableStreamIndexTaskTuningConfig;
import org.apache.druid.indexing.seekablestream.SeekableStreamStartSequenceNumbers;
import org.apache.druid.indexing.seekablestream.common.OrderedSequenceNumber;
import org.apache.druid.indexing.seekablestream.common.RecordSupplier;
import org.apache.druid.indexing.seekablestream.common.StreamException;
import org.apache.druid.indexing.seekablestream.common.StreamPartition;
import org.apache.druid.indexing.seekablestream.supervisor.SeekableStreamSupervisorStateManager.State;
import org.apache.druid.java.util.common.granularity.Granularities;
import org.apache.druid.java.util.common.parsers.JSONPathSpec;
import org.apache.druid.query.aggregation.AggregatorFactory;
import org.apache.druid.query.aggregation.CountAggregatorFactory;
import org.apache.druid.segment.TestHelper;
import org.apache.druid.segment.indexing.DataSchema;
import org.apache.druid.segment.indexing.granularity.UniformGranularitySpec;
import org.apache.druid.segment.realtime.firehose.ChatHandlerProvider;
import org.apache.druid.server.security.AuthorizerMapper;
import org.easymock.EasyMockSupport;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;

public class SeekableStreamSupervisorStateTest extends EasyMockSupport
{
  private static final ObjectMapper objectMapper = TestHelper.makeJsonMapper();
  private static final String DATASOURCE = "testDS";
  private static final String STREAM = "stream";
  private static final String SHARD_ID = "0";
  private static final StreamPartition<String> shard0Partition = StreamPartition.of(STREAM, SHARD_ID);
  private static final String EXCEPTION_MSG = "I had an exception";

  private TaskStorage taskStorage;
  private TaskMaster taskMaster;
  private TaskRunner taskRunner;
  private TaskQueue taskQueue;
  private IndexerMetadataStorageCoordinator indexerMetadataStorageCoordinator;
  private SeekableStreamIndexTaskClientFactory taskClientFactory;
  private SeekableStreamSupervisorSpec spec;
  private SeekableStreamIndexTaskClient indexTaskClient;
  private RecordSupplier<String, String> recordSupplier;

  private RowIngestionMetersFactory rowIngestionMetersFactory;
  private SeekableStreamSupervisorConfig supervisorConfig;

  @Before
  public void setupTest()
  {
    taskStorage = createMock(TaskStorage.class);
    taskMaster = createMock(TaskMaster.class);
    taskRunner = createMock(TaskRunner.class);
    taskQueue = createMock(TaskQueue.class);
    indexerMetadataStorageCoordinator = createMock(IndexerMetadataStorageCoordinator.class);
    taskClientFactory = createMock(SeekableStreamIndexTaskClientFactory.class);
    spec = createMock(SeekableStreamSupervisorSpec.class);
    indexTaskClient = createMock(SeekableStreamIndexTaskClient.class);
    recordSupplier = (RecordSupplier<String, String>) createMock(RecordSupplier.class);

    rowIngestionMetersFactory = new TestUtils().getRowIngestionMetersFactory();

    supervisorConfig = new SeekableStreamSupervisorConfig();

    expect(spec.getSupervisorConfig()).andReturn(supervisorConfig).anyTimes();

    expect(spec.getDataSchema()).andReturn(getDataSchema()).anyTimes();
    expect(spec.getIoConfig()).andReturn(getIOConfig()).anyTimes();
    expect(spec.getTuningConfig()).andReturn(getTuningConfig()).anyTimes();

    expect(taskClientFactory.build(anyObject(), anyString(), anyInt(), anyObject(), anyLong())).andReturn(
        indexTaskClient).anyTimes();
    expect(taskMaster.getTaskRunner()).andReturn(Optional.of(taskRunner)).anyTimes();
    expect(taskMaster.getTaskQueue()).andReturn(Optional.of(taskQueue)).anyTimes();

    taskRunner.registerListener(anyObject(TaskRunnerListener.class), anyObject(Executor.class));

    expect(indexerMetadataStorageCoordinator.getDataSourceMetadata(DATASOURCE)).andReturn(null).anyTimes();
    expect(recordSupplier.getAssignment()).andReturn(ImmutableSet.of(shard0Partition)).anyTimes();
    expect(recordSupplier.getLatestSequenceNumber(anyObject())).andReturn("10").anyTimes();
  }

  @Test
  public void testRunning() throws Exception
  {
    expect(spec.isSuspended()).andReturn(false).anyTimes();
    expect(recordSupplier.getPartitionIds(STREAM)).andReturn(ImmutableSet.of(SHARD_ID)).anyTimes();
    expect(taskStorage.getActiveTasks()).andReturn(ImmutableList.of()).anyTimes();
    expect(taskQueue.add(anyObject())).andReturn(true).anyTimes();

    replayAll();

    SeekableStreamSupervisor supervisor = new TestSeekableStreamSupervisor();

    supervisor.start();

    Assert.assertEquals(State.WAITING_TO_RUN, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    verifyAll();
  }

  @Test
  public void testConnectingToStreamFail() throws Exception
  {
    expect(spec.isSuspended()).andReturn(false).anyTimes();
    expect(recordSupplier.getPartitionIds(STREAM)).andThrow(new StreamException(new IllegalStateException(EXCEPTION_MSG)))
                                                  .anyTimes();
    expect(taskStorage.getActiveTasks()).andReturn(ImmutableList.of()).anyTimes();
    expect(taskQueue.add(anyObject())).andReturn(true).anyTimes();

    replayAll();

    SeekableStreamSupervisor supervisor = new TestSeekableStreamSupervisor();

    supervisor.start();

    Assert.assertEquals(State.WAITING_TO_RUN, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.CONNECTING_TO_STREAM, supervisor.stateManager.getSupervisorState());
    List<SeekableStreamSupervisorStateManager.ExceptionEvent> exceptionEvents = supervisor.stateManager.getExceptionEvents();
    Assert.assertEquals(1, exceptionEvents.size());
    Assert.assertTrue(exceptionEvents.get(0).isStreamException());
    Assert.assertEquals(IllegalStateException.class.getName(), exceptionEvents.get(0).getExceptionClass());
    Assert.assertEquals(
        String.format("%s: %s", IllegalStateException.class.getName(), EXCEPTION_MSG),
        exceptionEvents.get(0).getMessage()
    );
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.CONNECTING_TO_STREAM, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(2, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.UNABLE_TO_CONNECT_TO_STREAM, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    verifyAll();
  }

  @Test
  public void testConnectingToStreamFailRecoveryFailRecovery() throws Exception
  {
    expect(spec.isSuspended()).andReturn(false).anyTimes();
    expect(recordSupplier.getPartitionIds(STREAM)).andThrow(new StreamException(new IllegalStateException())).times(3);
    expect(recordSupplier.getPartitionIds(STREAM)).andReturn(ImmutableSet.of(SHARD_ID)).times(3);
    expect(recordSupplier.getPartitionIds(STREAM)).andThrow(new StreamException(new IllegalStateException())).times(3);
    expect(recordSupplier.getPartitionIds(STREAM)).andReturn(ImmutableSet.of(SHARD_ID)).times(3);
    expect(taskStorage.getActiveTasks()).andReturn(ImmutableList.of()).anyTimes();
    expect(taskQueue.add(anyObject())).andReturn(true).anyTimes();

    replayAll();

    SeekableStreamSupervisor supervisor = new TestSeekableStreamSupervisor();

    supervisor.start();

    Assert.assertEquals(State.WAITING_TO_RUN, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.CONNECTING_TO_STREAM, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.CONNECTING_TO_STREAM, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.UNABLE_TO_CONNECT_TO_STREAM, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNABLE_TO_CONNECT_TO_STREAM, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.UNABLE_TO_CONNECT_TO_STREAM, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.LOST_CONTACT_WITH_STREAM, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.LOST_CONTACT_WITH_STREAM, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.LOST_CONTACT_WITH_STREAM, supervisor.stateManager.getSupervisorState());
    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    verifyAll();
  }

  @Test
  public void testDiscoveringInitialTasksFailRecoveryFail() throws Exception
  {
    expect(spec.isSuspended()).andReturn(false).anyTimes();
    expect(recordSupplier.getPartitionIds(STREAM)).andReturn(ImmutableSet.of(SHARD_ID)).anyTimes();
    expect(taskStorage.getActiveTasks()).andThrow(new IllegalStateException(EXCEPTION_MSG)).times(3);
    expect(taskStorage.getActiveTasks()).andReturn(ImmutableList.of()).times(3);
    expect(taskStorage.getActiveTasks()).andThrow(new IllegalStateException(EXCEPTION_MSG)).times(3);
    expect(taskQueue.add(anyObject())).andReturn(true).anyTimes();

    replayAll();

    SeekableStreamSupervisor supervisor = new TestSeekableStreamSupervisor();
    supervisor.start();

    supervisor.runInternal();
    Assert.assertEquals(State.DISCOVERING_INITIAL_TASKS, supervisor.stateManager.getSupervisorState());
    List<SeekableStreamSupervisorStateManager.ExceptionEvent> exceptionEvents = supervisor.stateManager.getExceptionEvents();
    Assert.assertEquals(1, exceptionEvents.size());
    Assert.assertFalse(exceptionEvents.get(0).isStreamException());
    Assert.assertEquals(IllegalStateException.class.getName(), exceptionEvents.get(0).getExceptionClass());
    Assert.assertEquals(EXCEPTION_MSG, exceptionEvents.get(0).getMessage());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.DISCOVERING_INITIAL_TASKS, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(2, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    verifyAll();
  }

  @Test
  public void testCreatingTasksFailRecoveryFail() throws Exception
  {
    expect(spec.isSuspended()).andReturn(false).anyTimes();
    expect(recordSupplier.getPartitionIds(STREAM)).andReturn(ImmutableSet.of(SHARD_ID)).anyTimes();
    expect(taskStorage.getActiveTasks()).andReturn(ImmutableList.of()).anyTimes();
    expect(taskQueue.add(anyObject())).andThrow(new IllegalStateException(EXCEPTION_MSG)).times(3);
    expect(taskQueue.add(anyObject())).andReturn(true).times(3);
    expect(taskQueue.add(anyObject())).andThrow(new IllegalStateException(EXCEPTION_MSG)).times(3);

    replayAll();

    SeekableStreamSupervisor supervisor = new TestSeekableStreamSupervisor();
    supervisor.start();

    supervisor.runInternal();
    Assert.assertEquals(State.CREATING_TASKS, supervisor.stateManager.getSupervisorState());
    List<SeekableStreamSupervisorStateManager.ExceptionEvent> exceptionEvents = supervisor.stateManager.getExceptionEvents();
    Assert.assertEquals(1, exceptionEvents.size());
    Assert.assertFalse(exceptionEvents.get(0).isStreamException());
    Assert.assertEquals(IllegalStateException.class.getName(), exceptionEvents.get(0).getExceptionClass());
    Assert.assertEquals(EXCEPTION_MSG, exceptionEvents.get(0).getMessage());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.CREATING_TASKS, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(2, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertEquals(3, supervisor.stateManager.getExceptionEvents().size());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();
    Assert.assertEquals(State.UNHEALTHY_SUPERVISOR, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    verifyAll();
  }

  @Test
  public void testSuspended() throws Exception
  {
    expect(spec.isSuspended()).andReturn(true).anyTimes();
    expect(recordSupplier.getPartitionIds(STREAM)).andReturn(ImmutableSet.of(SHARD_ID)).anyTimes();
    expect(taskStorage.getActiveTasks()).andReturn(ImmutableList.of()).anyTimes();
    expect(taskQueue.add(anyObject())).andReturn(true).anyTimes();

    replayAll();

    SeekableStreamSupervisor supervisor = new TestSeekableStreamSupervisor();

    supervisor.start();

    Assert.assertEquals(State.WAITING_TO_RUN, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.SUSPENDED, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.SUSPENDED, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    verifyAll();
  }

  @Test
  public void testShuttingDown() throws Exception
  {
    expect(spec.isSuspended()).andReturn(false).anyTimes();
    expect(recordSupplier.getPartitionIds(STREAM)).andReturn(ImmutableSet.of(SHARD_ID)).anyTimes();
    expect(taskStorage.getActiveTasks()).andReturn(ImmutableList.of()).anyTimes();
    expect(taskQueue.add(anyObject())).andReturn(true).anyTimes();

    taskRunner.unregisterListener("testSupervisorId");
    indexTaskClient.close();

    replayAll();

    SeekableStreamSupervisor supervisor = new TestSeekableStreamSupervisor();

    supervisor.start();

    Assert.assertEquals(State.WAITING_TO_RUN, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertFalse(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.runInternal();

    Assert.assertEquals(State.RUNNING, supervisor.stateManager.getSupervisorState());
    Assert.assertTrue(supervisor.stateManager.getExceptionEvents().isEmpty());
    Assert.assertTrue(supervisor.stateManager.isAtLeastOneSuccessfulRun());

    supervisor.stop(false);

    Assert.assertEquals(State.SHUTTING_DOWN, supervisor.stateManager.getSupervisorState());

    verifyAll();
  }

  private static DataSchema getDataSchema()
  {
    List<DimensionSchema> dimensions = new ArrayList<>();
    dimensions.add(StringDimensionSchema.create("dim1"));
    dimensions.add(StringDimensionSchema.create("dim2"));

    return new DataSchema(
        DATASOURCE,
        objectMapper.convertValue(
            new StringInputRowParser(
                new JSONParseSpec(
                    new TimestampSpec("timestamp", "iso", null),
                    new DimensionsSpec(
                        dimensions,
                        null,
                        null
                    ),
                    new JSONPathSpec(true, ImmutableList.of()),
                    ImmutableMap.of()
                ),
                StandardCharsets.UTF_8.name()
            ),
            Map.class
        ),
        new AggregatorFactory[]{new CountAggregatorFactory("rows")},
        new UniformGranularitySpec(
            Granularities.HOUR,
            Granularities.NONE,
            ImmutableList.of()
        ),
        null,
        objectMapper
    );
  }

  private static SeekableStreamSupervisorIOConfig getIOConfig()
  {
    return new SeekableStreamSupervisorIOConfig(
        "stream",
        1,
        1,
        new Period("PT1H"),
        new Period("P1D"),
        new Period("PT30S"),
        false,
        new Period("PT30M"),
        null,
        null
    )
    {
    };
  }

  private static SeekableStreamSupervisorTuningConfig getTuningConfig()
  {
    return new SeekableStreamSupervisorTuningConfig()
    {
      @Override
      public Integer getWorkerThreads()
      {
        return 1;
      }

      @Override
      public Integer getChatThreads()
      {
        return 1;
      }

      @Override
      public Long getChatRetries()
      {
        return 1L;
      }

      @Override
      public Duration getHttpTimeout()
      {
        return new Period("PT1M").toStandardDuration();
      }

      @Override
      public Duration getShutdownTimeout()
      {
        return new Period("PT1S").toStandardDuration();
      }

      @Override
      public SeekableStreamIndexTaskTuningConfig convertToTaskTuningConfig()
      {
        return new SeekableStreamIndexTaskTuningConfig(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        )
        {
          @Override
          public SeekableStreamIndexTaskTuningConfig withBasePersistDirectory(File dir)
          {
            return null;
          }

          @Override
          public String toString()
          {
            return null;
          }
        };
      }
    };
  }

  private class TestSeekableStreamIndexTask extends SeekableStreamIndexTask<String, String>
  {
    public TestSeekableStreamIndexTask(
        String id,
        @Nullable TaskResource taskResource,
        DataSchema dataSchema,
        SeekableStreamIndexTaskTuningConfig tuningConfig,
        SeekableStreamIndexTaskIOConfig<String, String> ioConfig,
        @Nullable Map<String, Object> context,
        @Nullable ChatHandlerProvider chatHandlerProvider,
        AuthorizerMapper authorizerMapper,
        RowIngestionMetersFactory rowIngestionMetersFactory,
        @Nullable String groupId
    )
    {
      super(
          id,
          taskResource,
          dataSchema,
          tuningConfig,
          ioConfig,
          context,
          chatHandlerProvider,
          authorizerMapper,
          rowIngestionMetersFactory,
          groupId
      );
    }

    @Override
    protected SeekableStreamIndexTaskRunner<String, String> createTaskRunner()
    {
      return null;
    }

    @Override
    protected RecordSupplier<String, String> newTaskRecordSupplier()
    {
      return recordSupplier;
    }

    @Override
    public String getType()
    {
      return "test";
    }
  }

  private class TestSeekableStreamSupervisor extends SeekableStreamSupervisor<String, String>
  {
    private TestSeekableStreamSupervisor()
    {
      super(
          "testSupervisorId",
          taskStorage,
          taskMaster,
          indexerMetadataStorageCoordinator,
          taskClientFactory,
          objectMapper,
          spec,
          rowIngestionMetersFactory,
          false
      );
    }

    @Override
    protected String baseTaskName()
    {
      return "test";
    }

    @Override
    protected void updateLatestSequenceFromStream(
        RecordSupplier<String, String> recordSupplier, Set<StreamPartition<String>> streamPartitions
    )
    {
      // do nothing
    }

    @Override
    protected SeekableStreamIndexTaskIOConfig createTaskIoConfig(
        int groupId,
        Map<String, String> startPartitions,
        Map<String, String> endPartitions,
        String baseSequenceName,
        DateTime minimumMessageTime,
        DateTime maximumMessageTime,
        Set<String> exclusiveStartSequenceNumberPartitions,
        SeekableStreamSupervisorIOConfig ioConfig
    )
    {
      return new SeekableStreamIndexTaskIOConfig<String, String>(
          groupId,
          baseSequenceName,
          new SeekableStreamStartSequenceNumbers<>(STREAM, startPartitions, exclusiveStartSequenceNumberPartitions),
          new SeekableStreamEndSequenceNumbers<>(STREAM, endPartitions),
          true,
          minimumMessageTime,
          maximumMessageTime
      )
      {
      };
    }

    @Override
    protected List<SeekableStreamIndexTask<String, String>> createIndexTasks(
        int replicas,
        String baseSequenceName,
        ObjectMapper sortingMapper,
        TreeMap<Integer, Map<String, String>> sequenceOffsets,
        SeekableStreamIndexTaskIOConfig taskIoConfig,
        SeekableStreamIndexTaskTuningConfig taskTuningConfig,
        RowIngestionMetersFactory rowIngestionMetersFactory
    )
    {
      return ImmutableList.of(new TestSeekableStreamIndexTask(
          "id",
          null,
          getDataSchema(),
          taskTuningConfig,
          taskIoConfig,
          null,
          null,
          null,
          rowIngestionMetersFactory,
          null
      ));
    }

    @Override
    protected int getTaskGroupIdForPartition(String partition)
    {
      return 0;
    }

    @Override
    protected boolean checkSourceMetadataMatch(DataSourceMetadata metadata)
    {
      return true;
    }

    @Override
    protected boolean doesTaskTypeMatchSupervisor(Task task)
    {
      return true;
    }

    @Override
    protected SeekableStreamDataSourceMetadata<String, String> createDataSourceMetaDataForReset(
        String stream,
        Map<String, String> map
    )
    {
      return null;
    }

    @Override
    protected OrderedSequenceNumber<String> makeSequenceNumber(String seq, boolean isExclusive)
    {
      return new OrderedSequenceNumber<String>(seq, isExclusive)
      {
        @Override
        public int compareTo(OrderedSequenceNumber<String> o)
        {
          return new BigInteger(this.get()).compareTo(new BigInteger(o.get()));
        }
      };
    }

    @Override
    protected void scheduleReporting(ScheduledExecutorService reportingExec)
    {
      // do nothing
    }

    @Override
    protected Map<String, String> getLagPerPartition(Map<String, String> currentOffsets)
    {
      return null;
    }

    @Override
    protected RecordSupplier<String, String> setupRecordSupplier()
    {
      return recordSupplier;
    }

    @Override
    protected SeekableStreamSupervisorReportPayload<String, String> createReportPayload(
        int numPartitions,
        boolean includeOffsets
    )
    {
      return new SeekableStreamSupervisorReportPayload<String, String>(
          DATASOURCE,
          STREAM,
          1,
          1,
          1L,
          null,
          null,
          null,
          null,
          false,
          null,
          null
      )
      {
      };
    }

    @Override
    protected String getNotSetMarker()
    {
      return "NOT_SET";
    }

    @Override
    protected String getEndOfPartitionMarker()
    {
      return "EOF";
    }

    @Override
    protected boolean isEndOfShard(String seqNum)
    {
      return false;
    }

    @Override
    protected boolean useExclusiveStartSequenceNumberForNonFirstSequence()
    {
      return false;
    }
  }
}

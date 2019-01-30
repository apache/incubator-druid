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

package org.apache.druid.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import org.apache.druid.client.coordinator.Coordinator;
import org.apache.druid.discovery.DruidLeaderClient;
import org.apache.druid.guice.ManageLifecycle;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.concurrent.Execs;
import org.apache.druid.java.util.common.lifecycle.LifecycleStart;
import org.apache.druid.java.util.common.lifecycle.LifecycleStop;
import org.apache.druid.java.util.emitter.EmittingLogger;
import org.apache.druid.java.util.http.client.Request;
import org.apache.druid.server.coordinator.BytesAccumulatingResponseHandler;
import org.apache.druid.timeline.DataSegment;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.joda.time.DateTime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class polls the coordinator in background to keep the latest published segments.
 * Provides {@link #getPublishedSegments()} for others to get segments in metadata store.
 */
@ManageLifecycle
public class MetadataSegmentView
{

  private static final int DEFAULT_POLL_PERIOD_IN_MS = 60000;
  private static final EmittingLogger log = new EmittingLogger(MetadataSegmentView.class);

  private final DruidLeaderClient coordinatorDruidLeaderClient;
  private final ObjectMapper jsonMapper;
  private final BytesAccumulatingResponseHandler responseHandler;
  private final BrokerSegmentWatcherConfig segmentWatcherConfig;

  private final ConcurrentMap<DataSegment, DateTime> publishedSegments = new ConcurrentHashMap<>();
  private ScheduledExecutorService scheduledExec;

  @Inject
  public MetadataSegmentView(
      final @Coordinator DruidLeaderClient druidLeaderClient,
      final ObjectMapper jsonMapper,
      final BytesAccumulatingResponseHandler responseHandler,
      final BrokerSegmentWatcherConfig segmentWatcherConfig
  )
  {
    this.coordinatorDruidLeaderClient = druidLeaderClient;
    this.jsonMapper = jsonMapper;
    this.responseHandler = responseHandler;
    this.segmentWatcherConfig = segmentWatcherConfig;
  }

  @LifecycleStart
  public void start()
  {
    scheduledExec = Execs.scheduledSingleThreaded("MetadataSegmentView-Cache--%d");
    scheduledExec.scheduleWithFixedDelay(
        () -> {
          try {
            poll();
          }
          catch (JsonProcessingException e) {
            log.makeAlert(e, "Problem polling Coordinator.").emit();
          }
        },
        0,
        DEFAULT_POLL_PERIOD_IN_MS,
        TimeUnit.MILLISECONDS
    );
  }

  @LifecycleStop
  public void stop()
  {
    scheduledExec.shutdownNow();
    scheduledExec = null;
  }

  private void poll() throws JsonProcessingException
  {
    log.info("polling published segments from coordinator");
    //get authorized published segments from coordinator
    final JsonParserIterator<DataSegment> metadataSegments = getMetadataSegments(
        coordinatorDruidLeaderClient,
        jsonMapper,
        responseHandler,
        segmentWatcherConfig.getWatchedDataSources()
    );

    final DateTime timestamp = DateTimes.nowUtc();
    while (metadataSegments.hasNext()) {
      final DataSegment interned = DataSegmentInterner.intern(metadataSegments.next());
      // timestamp is used to filter deleted segments
      publishedSegments.put(interned, timestamp);
    }
    // filter the segments from cache whose timestamp is not equal to latest timestamp stored,
    // since the presence of a segment with an earlier timestamp indicates that
    // "that" segment is not returned by coordinator in latest poll, so it's
    // likely deleted and therefore we remove it from publishedSegments
    publishedSegments.values().removeIf(v -> v != timestamp);

    if (segmentWatcherConfig.getWatchedDataSources() != null) {
      log.debug(
          "filtering datasources[%s] in published segments based on broker's watchedDataSources",
          segmentWatcherConfig.getWatchedDataSources()
      );
      publishedSegments.keySet()
                       .removeIf(key -> !segmentWatcherConfig.getWatchedDataSources().contains(key.getDataSource()));
    }
  }

  public Iterator<DataSegment> getPublishedSegments()
  {
    return publishedSegments.keySet().iterator();
  }

  // Note that coordinator must be up to get segments
  private static JsonParserIterator<DataSegment> getMetadataSegments(
      DruidLeaderClient coordinatorClient,
      ObjectMapper jsonMapper,
      BytesAccumulatingResponseHandler responseHandler,
      Set<String> watchedDataSources
  ) throws JsonProcessingException
  {
    String query = "/druid/coordinator/v1/metadata/segments";
    if (watchedDataSources != null && !watchedDataSources.isEmpty()) {
      final String datasourcesJson = jsonMapper.writeValueAsString(watchedDataSources);
      query = "/druid/coordinator/v1/metadata/segments?" + datasourcesJson;
    }
    Request request;
    try {
      request = coordinatorClient.makeRequest(
          HttpMethod.GET,
          StringUtils.format(query),
          false
      );
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    ListenableFuture<InputStream> future = coordinatorClient.goAsync(
        request,
        responseHandler
    );

    final JavaType typeRef = jsonMapper.getTypeFactory().constructType(new TypeReference<DataSegment>()
    {
    });
    return new JsonParserIterator<>(
        typeRef,
        future,
        request.getUrl().toString(),
        null,
        request.getUrl().getHost(),
        jsonMapper,
        responseHandler
    );
  }

}

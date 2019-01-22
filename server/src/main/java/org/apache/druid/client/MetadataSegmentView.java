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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Interner;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ManageLifecycle
public class MetadataSegmentView
{

  private static final Interner<DataSegment> DATASEGMENT_INTERNER = DataSegmentInterner.INTERNER;
  private static final int DEFAULT_POLL_PERIOD_IN_MS = 60000;
  private static final EmittingLogger log = new EmittingLogger(MetadataSegmentView.class);

  private final DruidLeaderClient coordinatorDruidLeaderClient;
  private final ObjectMapper jsonMapper;
  private final BytesAccumulatingResponseHandler responseHandler;
  private final BrokerSegmentWatcherConfig segmentWatcherConfig;

  private final Map<DataSegment, DateTime> publishedSegments = new ConcurrentHashMap<>();
  private ScheduledExecutorService scheduledExec;

  @Inject
  public MetadataSegmentView(
      final @Coordinator DruidLeaderClient druidLeaderClient,
      ObjectMapper jsonMapper,
      BytesAccumulatingResponseHandler responseHandler,
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
        () -> poll(),
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

  private void poll()
  {
    log.debug("Start polling published segments from coordinator");
    //get authorized published segments from coordinator
    final JsonParserIterator<DataSegment> metadataSegments = getMetadataSegments(
        coordinatorDruidLeaderClient,
        jsonMapper,
        responseHandler
    );

    final DateTime ts = DateTimes.nowUtc();
    while (metadataSegments.hasNext()) {
      final DataSegment interned = DATASEGMENT_INTERNER.intern(metadataSegments.next());
      publishedSegments.put(interned, ts);
    }
    // filter the segments from cache which may not be present in subsequent polling
    publishedSegments.values().removeIf(v -> v != ts);

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
      BytesAccumulatingResponseHandler responseHandler
  )
  {
    Request request;
    try {
      request = coordinatorClient.makeRequest(
          HttpMethod.GET,
          StringUtils.format("/druid/coordinator/v1/metadata/segments"),
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

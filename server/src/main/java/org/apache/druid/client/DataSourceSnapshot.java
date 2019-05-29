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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import org.apache.druid.timeline.DataSegment;
import org.apache.druid.timeline.SegmentId;
import org.apache.druid.timeline.VersionedIntervalTimeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An immutable snapshot of fields from {@link org.apache.druid.metadata.SQLMetadataSegmentManager} (dataSources and
 * overshadowedSegments). Getters of {@link org.apache.druid.metadata.MetadataSegmentManager} should use this snapshot
 * to return dataSources and overshadowedSegments.
 */
public class DataSourceSnapshot
{
  private final Collection<ImmutableDruidDataSource> dataSources;

  public DataSourceSnapshot(
      Collection<ImmutableDruidDataSource> dataSources
  )
  {
    this.dataSources = dataSources;
  }

  public Collection<ImmutableDruidDataSource> getDataSources()
  {
    return dataSources;
  }

  public ImmutableSet<SegmentId> getOvershadowedSegments()
  {
    return ImmutableSet.copyOf(determineOvershadowedSegments());
  }

  /**
   * This method builds timelines from all dataSources and finds the overshadowed segments list
   *
   * @return overshadowed segment Ids list
   */
  private List<SegmentId> determineOvershadowedSegments()
  {

    final List<DataSegment> segments = dataSources.stream()
                                                  .flatMap(ds -> ds.getSegments().stream())
                                                  .collect(Collectors.toList());
    final Map<String, VersionedIntervalTimeline<String, DataSegment>> timelines = new HashMap<>();
    segments.forEach(segment -> timelines
        .computeIfAbsent(segment.getDataSource(), dataSource -> new VersionedIntervalTimeline<>(Ordering.natural()))
        .add(segment.getInterval(), segment.getVersion(), segment.getShardSpec().createChunk(segment)));

    // It's fine to add all overshadowed segments to a single collection because only
    // a small fraction of the segments in the cluster are expected to be overshadowed,
    // so building this collection shouldn't generate a lot of garbage.
    final List<SegmentId> overshadowedSegments = new ArrayList<>();
    for (DataSegment dataSegment : segments) {
      final VersionedIntervalTimeline<String, DataSegment> timeline = timelines.get(dataSegment.getDataSource());
      if (timeline != null && timeline.isOvershadowed(dataSegment.getInterval(), dataSegment.getVersion())) {
        overshadowedSegments.add(dataSegment.getId());
      }
    }
    return overshadowedSegments;
  }

}

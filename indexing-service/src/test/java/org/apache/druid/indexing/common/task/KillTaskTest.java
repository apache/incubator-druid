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

package org.apache.druid.indexing.common.task;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.druid.indexer.TaskState;
import org.apache.druid.indexing.overlord.Segments;
import org.apache.druid.indexing.overlord.TaskRunner;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.segment.loading.FailingDataSegmentKiller;
import org.apache.druid.segment.loading.SegmentLoadingException;
import org.apache.druid.timeline.DataSegment;
import org.joda.time.Interval;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class KillTaskTest extends IngestionTestBase
{
  private static final String DATA_SOURCE = "dataSource";
  private static final String VERSION = DateTimes.nowUtc().toString();

  private TaskRunner taskRunner;

  @Before
  public void setup()
  {
    taskRunner = new TestTaskRunner();
  }

  @Test
  public void testKill() throws Exception
  {
    announceSegmentsWithIntervals(VERSION,
            "2019-01-01/2019-02-01", "2019-02-01/2019-03-01", "2019-03-01/2019-04-01", "2019-04-01/2019-05-01");

    Assert.assertTrue(
        getMetadataSegmentManager().markSegmentAsUnused(
            newSegment(Intervals.of("2019-02-01/2019-03-01"), VERSION).getId().toString()
        )
    );
    Assert.assertTrue(
        getMetadataSegmentManager().markSegmentAsUnused(
            newSegment(Intervals.of("2019-03-01/2019-04-01"), VERSION).getId().toString()
        )
    );

    final KillTask task = new KillTask(null, DATA_SOURCE, Intervals.of("2019-03-01/2019-04-01"), null);

    Assert.assertEquals(TaskState.SUCCESS, taskRunner.run(task).get().getStatusCode());

    final List<DataSegment> unusedSegments =
        getMetadataStorageCoordinator().getUnusedSegmentsForInterval(DATA_SOURCE, Intervals.of("2019/2020"));

    Assert.assertEquals(ImmutableList.of(newSegment(Intervals.of("2019-02-01/2019-03-01"), VERSION)), unusedSegments);
    Assert.assertEquals(
        ImmutableSet.of(
            newSegment(Intervals.of("2019-01-01/2019-02-01"), VERSION),
            newSegment(Intervals.of("2019-04-01/2019-05-01"), VERSION)
        ),
        ImmutableSet.copyOf(
            getMetadataStorageCoordinator()
                .getUsedSegmentsForInterval(DATA_SOURCE, Intervals.of("2019/2020"), Segments.ONLY_VISIBLE)
        )
    );
  }

  @Test(expected = SegmentLoadingException.class)
  public void testKillWithSegmentLoadingExceptionShouldNotNukeSegment() throws Exception
  {
    announceSegmentsWithIntervals(VERSION,
            "2019-01-01/2019-02-01", "2019-02-01/2019-03-01", "2019-03-01/2019-04-01", "2019-04-01/2019-05-01");
    DataSegment febSegment = newSegment(Intervals.of("2019-02-01/2019-03-01"), VERSION);
    DataSegment marchSegment = newSegment(Intervals.of("2019-03-01/2019-04-01"), VERSION);
    Assert.assertTrue(
            getMetadataSegmentManager().markSegmentAsUnused(febSegment.getId().toString())
    );
    Assert.assertTrue(
            getMetadataSegmentManager().markSegmentAsUnused(marchSegment.getId().toString())
    );
    dataSegmentKiller = new FailingDataSegmentKiller(marchSegment);

    final KillTask task = new KillTask(null, DATA_SOURCE, Intervals.of("2019-02-01/2019-04-01"), null);

    try {
      taskRunner.run(task).get();
    }
    catch (RuntimeException e) {
      assert e.getCause() instanceof SegmentLoadingException;
      final List<DataSegment> unusedSegments =
              getMetadataStorageCoordinator().getUnusedSegmentsForInterval(DATA_SOURCE, Intervals.of("2019/2020"));

      Assert.assertEquals(ImmutableList.of(marchSegment), unusedSegments);
      Assert.assertEquals(
              ImmutableSet.of(
                      newSegment(Intervals.of("2019-01-01/2019-02-01"), VERSION),
                      newSegment(Intervals.of("2019-04-01/2019-05-01"), VERSION)
              ),
              ImmutableSet.copyOf(
                      getMetadataStorageCoordinator()
                              .getUsedSegmentsForInterval(DATA_SOURCE, Intervals.of("2019/2020"), Segments.ONLY_VISIBLE)
              )
      );
      throw (SegmentLoadingException) e.getCause();
    }
  }

  private void announceSegmentsWithIntervals(String version, String... intervals) throws IOException
  {
    ImmutableSet.Builder<DataSegment> segmentsBuilder = ImmutableSet.builder();
    Arrays.asList(intervals).forEach(interval -> segmentsBuilder.add(newSegment(Intervals.of(interval), version)));
    ImmutableSet<DataSegment> segments = segmentsBuilder.build();
    final Set<DataSegment> announced = getMetadataStorageCoordinator().announceHistoricalSegments(segments);

    Assert.assertEquals(segments, announced);
  }

  private static DataSegment newSegment(Interval interval, String version)
  {
    return new DataSegment(
        DATA_SOURCE,
        interval,
        version,
        null,
        null,
        null,
        null,
        9,
        10L
    );
  }
}

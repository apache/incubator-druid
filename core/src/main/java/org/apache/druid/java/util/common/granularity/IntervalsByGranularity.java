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

package org.apache.druid.java.util.common.granularity;

import com.google.common.collect.FluentIterable;
import org.apache.druid.java.util.common.JodaUtils;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Produce a stream of intervals generated by a given set of intervals as input and a given
 * granularity. This class avoids materializing the granularity intervals whenever possible.
 */
public class IntervalsByGranularity
{
  private final List<Interval> sortedNonOverlappingIntervals;
  private final Granularity granularity;

  public IntervalsByGranularity(Collection<Interval> intervals, Granularity granularity)
  {
    // eliminate dups, sort intervals:
    List<Interval> inputIntervals = new ArrayList<>(intervals.size());
    inputIntervals.addAll(intervals);
    inputIntervals.sort(JodaUtils.INTERVALS_COMPARATOR_INCREASING_ORDER);
    // now condense them to avoid overlapping (and eliminate dups--done inside condense)
    this.sortedNonOverlappingIntervals = JodaUtils.condenseIntervals(inputIntervals);

    this.granularity = granularity;
  }

  public Iterator<Interval> granularityIntervalsIterator()
  {
    Iterator<Interval> ite;
    if (sortedNonOverlappingIntervals.isEmpty()) {
      ite = Collections.emptyIterator();
    } else {
      // The filter after transform & concat is to remove duplicats.
      // This can happen when condense left intervals that did not overlap but
      // when a larger granularity is applied then they become equal
      // imagine input are 2013-01-01T00Z/2013-01-10T00Z, 2013-01-15T00Z/2013-01-20T00Z.
      // Condense will leave these two intervals as is but when
      // the iterator for the two intervals is called, say with MONTH granularity,  two
      // intervals will be returned, both with the same value 2013-01-01T00:00:00.000Z/2013-02-01T00:00:00.000Z.
      // Thus dups can be created given the right conditions....
      final AtomicReference previous = new AtomicReference();
      ite = FluentIterable.from(sortedNonOverlappingIntervals).transformAndConcat(granularity::getIterable).
          filter(interval -> {
            if (previous.get() != null && previous.get().equals(interval)) {
              return false;
            }
            previous.set(interval);
            return true;
          }).iterator();
    }
    return ite;
  }

}

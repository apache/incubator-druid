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

package org.apache.druid.segment.indexing.granularity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.druid.java.util.common.granularity.Granularities;
import org.apache.druid.java.util.common.granularity.Granularity;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

public class UniformGranularitySpec implements GranularitySpec
{
  private static final Granularity DEFAULT_SEGMENT_GRANULARITY = Granularities.DAY;
  private static final Granularity DEFAULT_QUERY_GRANULARITY = Granularities.NONE;

  private final Granularity segmentGranularity;
  private final Granularity queryGranularity;
  private final Boolean rollup;
  private final List<Interval> inputIntervals;
  private ArbitraryGranularitySpec wrappedSpec;

  @JsonCreator
  public UniformGranularitySpec(
      @JsonProperty("segmentGranularity") Granularity segmentGranularity,
      @JsonProperty("queryGranularity") Granularity queryGranularity,
      @JsonProperty("rollup") Boolean rollup,
      @JsonProperty("intervals") List<Interval> inputIntervals
  )
  {
    this.queryGranularity = queryGranularity == null ? DEFAULT_QUERY_GRANULARITY : queryGranularity;
    this.rollup = rollup == null ? Boolean.TRUE : rollup;
    this.segmentGranularity = segmentGranularity == null ? DEFAULT_SEGMENT_GRANULARITY : segmentGranularity;

    if (inputIntervals != null) {
      this.inputIntervals = ImmutableList.copyOf(inputIntervals);
    } else {
      this.inputIntervals = Collections.emptyList();
    }
  }


  public UniformGranularitySpec(
      Granularity segmentGranularity,
      Granularity queryGranularity,
      List<Interval> inputIntervals
  )
  {
    this(segmentGranularity, queryGranularity, true, inputIntervals);
  }

  @Override
  public Optional<SortedSet<Interval>> bucketIntervals()
  {
    if (getWrappedSpec() == null) {
      return Optional.absent();
    } else {
      return getWrappedSpec().bucketIntervals();
    }
  }

  @Override
  public List<Interval> inputIntervals()
  {
    return inputIntervals == null ? ImmutableList.of() : ImmutableList.copyOf(inputIntervals);
  }

  @Override
  public Optional<Interval> bucketInterval(DateTime dt)
  {
    if (getWrappedSpec() == null) {
      return Optional.absent();
    } else {
      return getWrappedSpec().bucketInterval(dt);
    }
  }

  @Override
  @JsonProperty("segmentGranularity")
  public Granularity getSegmentGranularity()
  {
    return segmentGranularity;
  }

  @Override
  @JsonProperty("rollup")
  public boolean isRollup()
  {
    return rollup;
  }

  @Override
  @JsonProperty("queryGranularity")
  public Granularity getQueryGranularity()
  {
    return queryGranularity;
  }

  @JsonProperty("intervals")
  public Optional<List<Interval>> getIntervals()
  {
    return Optional.fromNullable(inputIntervals);
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    UniformGranularitySpec that = (UniformGranularitySpec) o;

    if (!segmentGranularity.equals(that.segmentGranularity)) {
      return false;
    }
    if (!queryGranularity.equals(that.queryGranularity)) {
      return false;
    }
    if (!rollup.equals(that.rollup)) {
      return false;
    }

    if (inputIntervals != null ? !inputIntervals.equals(that.inputIntervals) : that.inputIntervals != null) {
      return false;
    }

    return true;

  }

  @Override
  public int hashCode()
  {
    int result = segmentGranularity.hashCode();
    result = 31 * result + queryGranularity.hashCode();
    result = 31 * result + rollup.hashCode();
    result = 31 * result + (inputIntervals != null ? inputIntervals.hashCode() : 0);
    return result;
  }

  @Override
  public String toString()
  {
    return "UniformGranularitySpec{" +
           "segmentGranularity=" + segmentGranularity +
           ", queryGranularity=" + queryGranularity +
           ", rollup=" + rollup +
           ", inputIntervals=" + inputIntervals +
           '}';
  }

  @Override
  public GranularitySpec withIntervals(List<Interval> inputIntervals)
  {
    return new UniformGranularitySpec(segmentGranularity, queryGranularity, rollup, inputIntervals);
  }

  private ArbitraryGranularitySpec getWrappedSpec()
  {
    if (!inputIntervals.isEmpty() && wrappedSpec == null) {
      List<Interval> granularIntervals = new ArrayList<>();
      for (Interval inputInterval : inputIntervals) {
        Iterables.addAll(granularIntervals, this.segmentGranularity.getIterable(inputInterval));
      }
      this.wrappedSpec = new ArbitraryGranularitySpec(queryGranularity, rollup, granularIntervals);
    }
    return wrappedSpec;
  }

}

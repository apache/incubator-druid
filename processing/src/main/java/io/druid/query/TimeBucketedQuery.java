/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import io.druid.guice.annotations.ExtensionPoint;
import io.druid.java.util.common.granularity.Granularity;
import io.druid.java.util.common.granularity.PeriodGranularity;
import io.druid.query.spec.QuerySegmentSpec;
import org.joda.time.DateTimeZone;

import java.util.Map;
import java.util.Objects;

@ExtensionPoint
public abstract class TimeBucketedQuery<T extends Comparable<T>> extends BaseQuery<T>
{
  private final Granularity granularity;

  public TimeBucketedQuery(
      DataSource dataSource,
      QuerySegmentSpec querySegmentSpec,
      boolean descending,
      Map<String, Object> context,
      Granularity granularity
  )
  {
    super(dataSource, querySegmentSpec, descending, context);
    Preconditions.checkNotNull(granularity, "Must specify a granularity");
    this.granularity = granularity;
  }

  @JsonProperty
  public Granularity getGranularity()
  {
    return granularity;
  }

  public DateTimeZone getTimezone() {
    return granularity instanceof PeriodGranularity ? ((PeriodGranularity)granularity).getTimeZone() : DateTimeZone.UTC;
  }

  @Override
  public boolean equals(final Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final TimeBucketedQuery that = (TimeBucketedQuery) o;
    return Objects.equals(granularity, that.granularity);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(
        super.hashCode(),
        granularity
    );
  }
}

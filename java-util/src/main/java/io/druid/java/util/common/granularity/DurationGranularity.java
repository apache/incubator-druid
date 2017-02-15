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

package io.druid.java.util.common.granularity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Longs;
import io.druid.java.util.common.RE;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;

import java.nio.ByteBuffer;

public class DurationGranularity extends Granularity
{
  private final long length;
  private final long origin;

  @JsonCreator
  public DurationGranularity(
      @JsonProperty("duration") long duration,
      @JsonProperty("origin") DateTime origin
  )
  {
    this(duration, origin == null ? 0 : origin.getMillis());
  }

  public DurationGranularity(long millis, long origin)
  {
    Preconditions.checkArgument(millis > 0, "duration should be greater than 0!");
    this.length = millis;
    this.origin = origin % length;
  }

  @JsonProperty("duration")
  public long getDuration()
  {
    return length;
  }

  @JsonProperty("origin")
  public DateTime getOrigin()
  {
    return new DateTime(origin);
  }

  public long getOriginMillis()
  {
    return origin;
  }

  @Override
  public DateTimeFormatter getFormatter(Formatter type)
  {
    throw new RE("This method should not be invoked for this granularity type");
  }

  @Override
  public DateTime increment(DateTime time)
  {
    return new DateTime(time.getMillis() + getDurationMillis());
  }

  @Override
  public DateTime decrement(DateTime time)
  {
    return new DateTime(time.getMillis() - getDurationMillis());
  }

  @Override
  public DateTime truncate(DateTime time)
  {
    long t = time.getMillis();
    final long duration = getDurationMillis();
    long offset = t % duration - origin % duration;
    if (offset < 0) {
      offset += duration;
    }
    return new DateTime(t - offset);
  }

  @Override
  public DateTime toDate(String filePath, Formatter formatter)
  {
    throw new RE("This method should not be invoked for this granularity type");
  }

  @Override
  public byte[] getCacheKey()
  {
    return ByteBuffer.allocate(2 * Longs.BYTES).putLong(length).putLong(origin).array();
  }

  @Override
  public DateTime toDateTime(long offset)
  {
    throw new RE("This method should not be invoked for this granularity type");
  }

  public long getDurationMillis()
  {
    return length;
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

    DurationGranularity that = (DurationGranularity) o;

    if (length != that.length) {
      return false;
    }
    if (origin != that.origin) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    int result = (int) (length ^ (length >>> 32));
    result = 31 * result + (int) (origin ^ (origin >>> 32));
    return result;
  }

  @Override
  public String toString()
  {
    return "DurationGranularity{" +
           "length=" + length +
           ", origin=" + origin +
           '}';
  }
}

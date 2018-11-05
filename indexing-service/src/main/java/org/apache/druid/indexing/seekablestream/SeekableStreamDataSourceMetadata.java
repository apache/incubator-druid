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

package org.apache.druid.indexing.seekablestream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.druid.indexing.overlord.DataSourceMetadata;
import org.apache.druid.java.util.common.IAE;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class SeekableStreamDataSourceMetadata<partitionType, sequenceType> implements DataSourceMetadata
{
  private final SeekableStreamPartitions<partitionType, sequenceType> seekableStreamPartitions;

  @JsonCreator
  public SeekableStreamDataSourceMetadata(
      @JsonProperty("partitions") SeekableStreamPartitions<partitionType, sequenceType> seekableStreamPartitions
  )
  {
    this.seekableStreamPartitions = seekableStreamPartitions;
  }

  @JsonProperty("partitions")
  public SeekableStreamPartitions<partitionType, sequenceType> getSeekableStreamPartitions()
  {
    return seekableStreamPartitions;
  }

  @Override
  public boolean isValidStart()
  {
    return true;
  }

  @Override
  public boolean matches(DataSourceMetadata other)
  {
    if (!getClass().equals(other.getClass())) {
      return false;
    }

    return plus(other).equals(other.plus(this));
  }


  @Override
  public DataSourceMetadata plus(DataSourceMetadata other)
  {
    if (!(this.getClass().isInstance(other))) {
      throw new IAE(
          "Expected instance of %s, got %s",
          this.getClass().getCanonicalName(),
          other.getClass().getCanonicalName()
      );
    }

    @SuppressWarnings("unchecked")
    final SeekableStreamDataSourceMetadata<partitionType, sequenceType> that = (SeekableStreamDataSourceMetadata<partitionType, sequenceType>) other;

    if (that.getSeekableStreamPartitions().getStream().equals(seekableStreamPartitions.getStream())) {
      // Same topic, merge offsets.
      final Map<partitionType, sequenceType> newMap = new HashMap<>();

      for (Map.Entry<partitionType, sequenceType> entry : seekableStreamPartitions.getPartitionSequenceNumberMap().entrySet()) {
        newMap.put(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<partitionType, sequenceType> entry : that.getSeekableStreamPartitions().getPartitionSequenceNumberMap().entrySet()) {
        newMap.put(entry.getKey(), entry.getValue());
      }

      return createConcreteDataSourceMetaData(seekableStreamPartitions.getStream(), newMap);
    } else {
      // Different topic, prefer "other".
      return other;
    }
  }


  @Override
  public DataSourceMetadata minus(DataSourceMetadata other)
  {
    if (!(this.getClass().isInstance(other))) {
      throw new IAE(
          "Expected instance of %s, got %s",
          this.getClass().getCanonicalName(),
          other.getClass().getCanonicalName()
      );
    }

    @SuppressWarnings("unchecked")
    final SeekableStreamDataSourceMetadata<partitionType, sequenceType> that = (SeekableStreamDataSourceMetadata<partitionType, sequenceType>) other;

    if (that.getSeekableStreamPartitions().getStream().equals(seekableStreamPartitions.getStream())) {
      // Same stream, remove partitions present in "that" from "this"
      final Map<partitionType, sequenceType> newMap = new HashMap<>();

      for (Map.Entry<partitionType, sequenceType> entry : seekableStreamPartitions.getPartitionSequenceNumberMap().entrySet()) {
        if (!that.getSeekableStreamPartitions().getPartitionSequenceNumberMap().containsKey(entry.getKey())) {
          newMap.put(entry.getKey(), entry.getValue());
        }
      }

      return createConcreteDataSourceMetaData(seekableStreamPartitions.getStream(), newMap);
    } else {
      // Different stream, prefer "this".
      return this;
    }
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }
    SeekableStreamDataSourceMetadata that = (SeekableStreamDataSourceMetadata) o;
    return Objects.equals(getSeekableStreamPartitions(), that.getSeekableStreamPartitions());
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(getSeekableStreamPartitions());
  }

  @Override
  public String toString()
  {
    return "SeekableStreamDataSourceMetadata{" +
           "SeekableStreamPartitions=" + getSeekableStreamPartitions() +
           '}';
  }

  protected abstract SeekableStreamDataSourceMetadata<partitionType, sequenceType> createConcreteDataSourceMetaData(
      String streamId,
      Map<partitionType, sequenceType> newMap
  );
}

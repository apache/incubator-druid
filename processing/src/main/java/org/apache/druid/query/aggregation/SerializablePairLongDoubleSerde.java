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

package org.apache.druid.query.aggregation;

import com.google.common.primitives.Longs;
import org.apache.druid.collections.SerializablePair;
import org.apache.druid.data.input.InputRow;
import org.apache.druid.segment.GenericColumnSerializer;
import org.apache.druid.segment.column.ColumnBuilder;
import org.apache.druid.segment.data.GenericIndexed;
import org.apache.druid.segment.data.ObjectStrategy;
import org.apache.druid.segment.serde.ComplexColumnPartSupplier;
import org.apache.druid.segment.serde.ComplexMetricExtractor;
import org.apache.druid.segment.serde.ComplexMetricSerde;
import org.apache.druid.segment.serde.LargeColumnSupportedComplexColumnSerializer;
import org.apache.druid.segment.writeout.SegmentWriteOutMedium;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * The class serializes a Long-Double pair (SerializablePair<Long, Double>).
 * The serialization structure is: Long:Double
 * <p>
 * The class is used on first/last Double aggregators to store the time and the first/last Double.
 * Long:Long -> Timestamp:Long
 */
public class SerializablePairLongDoubleSerde extends ComplexMetricSerde
{
  public static final String TYPE_NAME = "serializablePairLongDouble";

  @Override
  public String getTypeName()
  {
    return TYPE_NAME;
  }

  @Override
  public ComplexMetricExtractor getExtractor()
  {
    SerializablePair<Long, Double> pair = new SerializablePair<>(null, null);
    final Class<SerializablePair<Long, Double>> pairClass = (Class<SerializablePair<Long, Double>>) pair.getClass();
    return new ComplexMetricExtractor()
    {
      @Override
      public Class<SerializablePair<Long, Double>> extractedClass()
      {
        return pairClass;
      }

      @Override
      public Object extractValue(InputRow inputRow, String metricName)
      {
        return inputRow.getRaw(metricName);
      }
    };
  }

  @Override
  public void deserializeColumn(ByteBuffer buffer, ColumnBuilder columnBuilder)
  {
    final GenericIndexed column = GenericIndexed.read(buffer, getObjectStrategy(), columnBuilder.getFileMapper());
    columnBuilder.setComplexColumnSupplier(new ComplexColumnPartSupplier(getTypeName(), column));
  }

  @Override
  public ObjectStrategy getObjectStrategy()
  {
    SerializablePair<Long, Double> pair = new SerializablePair<>(null, null);
    final Class<SerializablePair<Long, Double>> pairClass = (Class<SerializablePair<Long, Double>>) pair.getClass();

    return new ObjectStrategy<SerializablePair<Long, Double>>()
    {
      @Override
      public int compare(@Nullable SerializablePair<Long, Double> o1, @Nullable SerializablePair<Long, Double> o2)
      {
        return Longs.compare(o1.lhs, o2.lhs);
      }

      @Override
      public Class<SerializablePair<Long, Double>> getClazz()
      {
        return pairClass;
      }

      @Override
      public SerializablePair<Long, Double> fromByteBuffer(ByteBuffer buffer, int numBytes)
      {
        final ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();
        long lhs = readOnlyBuffer.getLong();
        double rhs = readOnlyBuffer.getDouble();
        return new SerializablePair<Long, Double>(lhs, rhs);
      }

      @Override
      public byte[] toBytes(SerializablePair<Long, Double> val)
      {
        ByteBuffer bbuf = ByteBuffer.allocate(Long.BYTES + Double.BYTES);
        bbuf.putLong(val.lhs);
        bbuf.putDouble(val.rhs);
        return bbuf.array();
      }
    };
  }

  @Override
  public GenericColumnSerializer getSerializer(SegmentWriteOutMedium segmentWriteOutMedium, String column)
  {
    return LargeColumnSupportedComplexColumnSerializer.create(segmentWriteOutMedium, column, this.getObjectStrategy());
  }
}

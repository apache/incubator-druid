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

package io.druid.segment;

import io.druid.segment.column.ColumnDescriptor;
import io.druid.segment.column.ValueType;
import io.druid.segment.serde.ColumnPartSerde;
import io.druid.segment.writeout.SegmentWriteOutMedium;

public class FloatDimensionMergerV9 extends NumericDimensionMergerV9
{

  FloatDimensionMergerV9(String dimensionName, IndexSpec indexSpec, SegmentWriteOutMedium segmentWriteOutMedium)
  {
    super(dimensionName, indexSpec, segmentWriteOutMedium);
  }

  @Override
  GenericColumnSerializer setupEncodedValueWriter()
  {
    return IndexMergerV9.createFloatColumnSerializer(segmentWriteOutMedium, dimensionName, indexSpec);
  }

  @Override
  public ColumnDescriptor makeColumnDescriptor()
  {
    final ColumnDescriptor.Builder builder = ColumnDescriptor.builder();
    builder.setValueType(ValueType.FLOAT);
    ColumnPartSerde serde = IndexMergerV9.createFloatColumnPartSerde(serializer, indexSpec);
    builder.addSerde(serde);
    return builder.build();
  }
}

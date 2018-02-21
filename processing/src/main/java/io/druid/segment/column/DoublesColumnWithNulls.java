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

package io.druid.segment.column;

import io.druid.collections.bitmap.ImmutableBitmap;
import io.druid.query.monomorphicprocessing.RuntimeShapeInspector;
import io.druid.segment.ColumnValueSelector;
import io.druid.segment.data.ColumnarDoubles;
import io.druid.segment.data.ReadableOffset;

/**
 * DoublesColumn with null values.
 */
class DoublesColumnWithNulls extends DoublesColumn
{
  private final ImmutableBitmap nullValueBitmap;

  DoublesColumnWithNulls(ColumnarDoubles columnarDoubles, ImmutableBitmap nullValueBitmap)
  {
    super(columnarDoubles);
    this.nullValueBitmap = nullValueBitmap;
  }

  @Override
  public ColumnValueSelector makeColumnValueSelector(ReadableOffset offset)
  {
    return column.makeColumnValueSelector(offset, nullValueBitmap);
  }

  @Override
  public boolean isNull(int rowNum)
  {
    return nullValueBitmap.get(rowNum);
  }

  @Override
  public void inspectRuntimeShape(RuntimeShapeInspector inspector)
  {
    super.inspectRuntimeShape(inspector);
    inspector.visit("nullValueBitmap", nullValueBitmap);
  }

  @Override
  public long getLongSingleValueRow(int rowNum)
  {
    assert !isNull(rowNum);
    return super.getLongSingleValueRow(rowNum);
  }

}

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

package io.druid.query.aggregation.unique;

import io.druid.java.util.common.UOE;
import io.druid.query.aggregation.Aggregator;
import io.druid.segment.ColumnValueSelector;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;

public class UniqueBuildAggregator implements Aggregator
{
  private final ColumnValueSelector<Object> selector;
  private MutableRoaringBitmap mutableRoaringBitmap;

  public UniqueBuildAggregator(ColumnValueSelector<Object> selector)
  {
    this.selector = selector;
    this.mutableRoaringBitmap = new MutableRoaringBitmap();
  }

  @Override
  public void aggregate()
  {
    final Object value = selector.getObject();
    if (value == null) {
      return;
    }
    if (value instanceof Integer) {
      // POC 验证，直接使用int测试
      mutableRoaringBitmap.add((int) value);
    } else if (value instanceof ImmutableRoaringBitmap) {
      ImmutableRoaringBitmap bitmap = (ImmutableRoaringBitmap) value;
      if (bitmap.getCardinality() <= 10) {
        IntIterator iterator = bitmap.getIntIterator();
        while (iterator.hasNext()) {
          mutableRoaringBitmap.add(iterator.next());
        }
      } else {
        mutableRoaringBitmap.or(bitmap);
      }
    } else {
      throw new UOE("Not implemented");
    }
  }

  @Override
  public ImmutableRoaringBitmap get()
  {
    return mutableRoaringBitmap;
  }

  @Override
  public float getFloat()
  {
    throw new UOE("Not implemented");
  }

  @Override
  public long getLong()
  {
    throw new UOE("Not implemented");
  }

  @Override
  public void close()
  {
    mutableRoaringBitmap = null;
  }
}

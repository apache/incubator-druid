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

package io.druid.segment.data;

import io.druid.java.util.common.IAE;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;

import java.io.IOException;

/**
 */
public final class ArrayBasedIndexedInts implements IndexedInts
{
  private static final ArrayBasedIndexedInts EMPTY = new ArrayBasedIndexedInts(IntArrays.EMPTY_ARRAY);

  /**
   * Returns empty ArrayBasedIndexedInts, size = 0. This is useful instead of {@link EmptyIndexedInts} where
   * monomorphism is a concern.
   */
  public static ArrayBasedIndexedInts empty()
  {
    return EMPTY;
  }

  private final int[] expansion;
  private final int size;

  public ArrayBasedIndexedInts(int[] expansion)
  {
    this.expansion = expansion;
    this.size = expansion.length;
  }

  public ArrayBasedIndexedInts(int[] expansion, int size)
  {
    this.expansion = expansion;
    if (size < 0 || size > expansion.length) {
      throw new IAE("Size[%s] should be between 0 and %s", size, expansion.length);
    }
    this.size = size;
  }

  @Override
  public int size()
  {
    return size;
  }

  @Override
  public int get(int index)
  {
    if (index >= size) {
      throw new IndexOutOfBoundsException("index: " + index + ", size: " + size);
    }
    return expansion[index];
  }

  @Override
  public IntIterator iterator()
  {
    return IntIterators.wrap(expansion, 0, size);
  }

  @Override
  public void fill(int index, int[] toFill)
  {
    throw new UnsupportedOperationException("fill not supported");
  }

  @Override
  public void close() throws IOException
  {

  }
}

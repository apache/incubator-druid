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

package io.druid.query.dimension;

import io.druid.segment.DimensionSelector;
import io.druid.segment.column.ColumnCapabilities;
import io.druid.segment.data.ArrayBasedIndexedInts;
import io.druid.segment.data.IndexedInts;

import java.util.List;

/**
 * Test dimension selector that has cardinality=26
 * encoding 0 -> a, 1 -> b, ...
 * row -> [c,e,g]
 */
class TestDimensionSelector implements DimensionSelector
{
  public final static TestDimensionSelector instance = new TestDimensionSelector();

  private TestDimensionSelector()
  {

  }

  @Override
  public IndexedInts getRow()
  {
    return new ArrayBasedIndexedInts(new int[]{2, 4, 6});
  }

  @Override
  public int getValueCardinality()
  {
    return 26;
  }

  @Override
  public String lookupName(int id)
  {
    return String.valueOf((char) (id + 'a'));
  }

  @Override
  public int lookupId(String name)
  {
    return name.charAt(0) - 'a';
  }

  @Override
  public List<Comparable> getUnencodedRow()
  {
    throw new UnsupportedOperationException("getUnencodedRow() is not supported");
  }

  @Override
  public Comparable getExtractedValueFromUnencoded(Comparable rowVal)
  {
    throw new UnsupportedOperationException("getExtractedValueFromUnencoded() is not supported.");
  }

  @Override
  public ColumnCapabilities getDimCapabilities()
  {
    throw new UnsupportedOperationException("getDimCapabilities not supprted yet");
  }

}

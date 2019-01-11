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

package org.apache.druid.query.aggregation.bloom.types;

import org.apache.druid.common.config.NullHandling;
import org.apache.druid.query.filter.BloomKFilter;
import org.apache.druid.segment.BaseFloatColumnValueSelector;

import java.nio.ByteBuffer;

public class FloatBloomFilterAggregatorColumnSelectorStrategy
    implements BloomFilterAggregatorColumnSelectorStrategy<BaseFloatColumnValueSelector>
{
  @Override
  public void add(BaseFloatColumnValueSelector selector, BloomKFilter bloomFilter)
  {
    if (NullHandling.replaceWithDefault() || !selector.isNull()) {
      bloomFilter.addFloat(selector.getFloat());
    } else {
      bloomFilter.addBytes(null, 0, 0);
    }
  }

  @Override
  public void bufferAdd(BaseFloatColumnValueSelector selector, ByteBuffer buffer)
  {
    if (NullHandling.replaceWithDefault() || !selector.isNull()) {
      BloomKFilter.addFloat(buffer, selector.getFloat());
    } else {
      BloomKFilter.addBytes(buffer, null, 0, 0);
    }
  }
}

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

package io.druid.query.aggregation.cardinality.types;

import com.google.common.hash.Hasher;
import io.druid.hll.HyperLogLogCollector;
import io.druid.query.aggregation.cardinality.CardinalityAggregator;
import io.druid.segment.BaseLongColumnValueSelector;
import io.druid.segment.NullHandlingHelper;

public class LongCardinalityAggregatorColumnSelectorStrategy
    implements CardinalityAggregatorColumnSelectorStrategy<BaseLongColumnValueSelector>
{
  @Override
  public void hashRow(BaseLongColumnValueSelector dimSelector, Hasher hasher)
  {
    if (NullHandlingHelper.useDefaultValuesForNull() || !dimSelector.isNull()) {
      hasher.putLong(dimSelector.getLong());
    }
  }

  @Override
  public void hashValues(BaseLongColumnValueSelector dimSelector, HyperLogLogCollector collector)
  {
    if (NullHandlingHelper.useDefaultValuesForNull() || !dimSelector.isNull()) {
      collector.add(CardinalityAggregator.hashFn.hashLong(dimSelector.getLong()).asBytes());
    }
  }
}

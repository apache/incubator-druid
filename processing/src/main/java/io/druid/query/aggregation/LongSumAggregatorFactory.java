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

package io.druid.query.aggregation;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.druid.java.util.common.StringUtils;
import io.druid.math.expr.ExprMacroTable;
import io.druid.segment.ColumnSelectorFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 */
public class LongSumAggregatorFactory extends SimpleLongAggregatorFactory
{
  @JsonCreator
  public LongSumAggregatorFactory(
      @JsonProperty("name") String name,
      @JsonProperty("fieldName") String fieldName,
      @JsonProperty("expression") String expression,
      @JacksonInject ExprMacroTable macroTable
  )
  {
    super(macroTable, name, fieldName, expression);
  }

  public LongSumAggregatorFactory(String name, String fieldName)
  {
    this(name, fieldName, null, ExprMacroTable.nil());
  }

  @Override
  public Aggregator factorize(ColumnSelectorFactory metricFactory)
  {
    return new LongSumAggregator(makeColumnValueSelectorWithLongDefault(metricFactory, 0L));
  }

  @Override
  public BufferAggregator factorizeBuffered(ColumnSelectorFactory metricFactory)
  {
    return new LongSumBufferAggregator(makeColumnValueSelectorWithLongDefault(metricFactory, 0L));
  }

  @Override
  public Object combine(Object lhs, Object rhs)
  {
    return LongSumAggregator.combineValues(lhs, rhs);
  }

  @Override
  public AggregateCombiner makeAggregateCombiner()
  {
    return new LongSumAggregateCombiner();
  }

  @Override
  public AggregatorFactory getCombiningFactory()
  {
    return new LongSumAggregatorFactory(name, name, null, macroTable);
  }

  @Override
  public List<AggregatorFactory> getRequiredColumns()
  {
    return Arrays.<AggregatorFactory>asList(new LongSumAggregatorFactory(fieldName, fieldName, expression, macroTable));
  }

  @JsonProperty
  public String getFieldName()
  {
    return fieldName;
  }

  @JsonProperty
  public String getExpression()
  {
    return expression;
  }

  @Override
  @JsonProperty
  public String getName()
  {
    return name;
  }

  @Override
  public byte[] getCacheKey()
  {
    byte[] fieldNameBytes = StringUtils.toUtf8WithNullToEmpty(fieldName);
    byte[] expressionBytes = StringUtils.toUtf8WithNullToEmpty(expression);

    return ByteBuffer.allocate(2 + fieldNameBytes.length + expressionBytes.length)
                     .put(AggregatorUtil.LONG_SUM_CACHE_TYPE_ID)
                     .put(fieldNameBytes)
                     .put(AggregatorUtil.STRING_SEPARATOR)
                     .put(expressionBytes)
                     .array();
  }

  @Override
  public String toString()
  {
    return "LongSumAggregatorFactory{" +
           "fieldName='" + fieldName + '\'' +
           ", expression='" + expression + '\'' +
           ", name='" + name + '\'' +
           '}';
  }
}

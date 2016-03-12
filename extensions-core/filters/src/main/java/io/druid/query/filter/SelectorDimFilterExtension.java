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

package io.druid.query.filter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.druid.query.ordering.StringComparators;

import java.nio.ByteBuffer;

/**
 */
public class SelectorDimFilterExtension extends SelectorDimFilter implements DimFilterExtension
{
  private final String compareType;
  private final BinaryOperator operator;

  @JsonCreator
  public SelectorDimFilterExtension(
      @JsonProperty("dimension") String dimension,
      @JsonProperty("value") String value,
      @JsonProperty("operator") String operator,
      @JsonProperty("compareType") String compareType
  )
  {
    super(dimension, value);
    this.operator = BinaryOperator.get(operator);
    this.compareType = compareType == null ? StringComparators.LEXICOGRAPHIC_NAME : compareType;

    // don't allow null comparison, for now
    Preconditions.checkArgument(
        !(this.operator != BinaryOperator.EQ && this.operator != BinaryOperator.NE && Strings.isNullOrEmpty(value)),
        "null comparison is not allowed, except equals/not-equals"
    );
    Preconditions.checkArgument(StringComparators.validate(this.compareType), "Invalid compare type " + compareType);
  }

  public SelectorDimFilterExtension(String dimension, String value, String operator)
  {
    this(dimension, value, operator, null);
  }

  public SelectorDimFilterExtension(String dimension, String value)
  {
    this(dimension, value, null, null);
  }

  @Override
  public byte[] getCacheKey()
  {
    byte[] cacheKey = super.getCacheKey();
    byte[] dimensionBytes = com.metamx.common.StringUtils.toUtf8(compareType);

    return ByteBuffer.allocate(cacheKey.length + dimensionBytes.length + 1)
                     .put(cacheKey)
                     .put(dimensionBytes)
                     .put((byte) operator.ordinal())
                     .array();
  }

  @JsonProperty
  public String getOperator()
  {
    return operator.name();
  }

  @JsonProperty
  public String getCompareType()
  {
    return compareType;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (super.equals(o)) {
      SelectorDimFilterExtension that = (SelectorDimFilterExtension) o;
      return operator.equals(that.operator) && compareType.equals(that.compareType);
    }

    return false;
  }

  @Override
  public int hashCode()
  {
    int result = super.hashCode();
    result = 31 * result + operator.ordinal();
    result = 31 * result + compareType.hashCode();
    return result;
  }

  @Override
  public String toString()
  {
    return String.format("%s %s %s %s", dimension, operator.name(), value, compareType);
  }

  @Override
  public Filter toFilter()
  {
    return new SelectorFilterExtension(dimension, value, compareType, operator);
  }
}

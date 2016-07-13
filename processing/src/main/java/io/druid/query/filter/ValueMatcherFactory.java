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

import com.google.common.base.Predicate;
import io.druid.segment.column.ValueType;

/**
 * A ValueMatcherFactory is an object associated with a collection of rows (e.g., an IncrementalIndexStorageAdapter)
 * that generates ValueMatchers for filtering on the associated collection of rows.
 *
 * A ValueMatcher is an object that decides whether a row value matches a value or predicate
 * associated with the ValueMatcher.
 *
 * The ValueMatcher is expected to track the current row to be matched with a stateful
 * object (e.g., a ColumnSelectorFactory). The ValueMatcher has no responsibility for moving the current
 * "row pointer", this is handled outside of the ValueMatcher.
 *
 * The ValueMatcherFactory/ValueMatcher classes are used for filtering rows during column scans.
 */
public interface ValueMatcherFactory
{
  /**
   * Create a ValueMatcher that compares row values to the provided value.
   *
   * An implementation of this method should be able to handle dimensions of various types.
   *
   * @param dimension The dimension to filter.
   * @param value     The value to match against.
   *
   * @return An object that matches row values on the provided value.
   */
  public ValueMatcher makeValueMatcher(String dimension, Comparable value);


  /**
   * Create a ValueMatcher that applies a predicate to row values.
   *
   * The input predicate should accept String and Object inputs.
   *
   * This predicate matcher is intended to be used for String dimensions and any dimensions with non-primitive types.
   *
   * @param dimension The dimension to filter.
   * @param predicate Predicate to apply to row values
   * @return An object that applies a predicate to String or non-primitive Object values
   */
  public ValueMatcher makeValueMatcher(String dimension, Predicate<Object> predicate);


  /**
   * Create a ValueMatcher that applies a predicate to row values.
   *
   * The input predicate should accept long primitives.
   *
   * This predicate matcher is intended for long columns.
   * A separate method/predicate type is used to avoid primitive boxing.
   *
   * @param dimension The dimension to filter.
   * @param predicate Predicate to apply to long row values
   * @return An object that applies a predicate to long values
   */
  public ValueMatcher makeLongValueMatcher(String dimension, DruidLongPredicate predicate);


  /**
   * Gets the ValueType of a dimension.
   *
   * Used by Filters to determine whether they should create a String/Object predicate
   * or a primitive-matching predicate.
   *
   * @param dimension Get the type of this dimension
   * @return The ValueType of the dimension.
   */
  public ValueType getTypeForDimension(String dimension);
}

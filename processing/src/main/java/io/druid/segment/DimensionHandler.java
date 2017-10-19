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

package io.druid.segment;

import io.druid.segment.writeout.SegmentWriteOutMedium;
import io.druid.segment.column.Column;
import io.druid.segment.column.ColumnCapabilities;
import io.druid.segment.data.Indexed;

import java.io.Closeable;
import java.io.IOException;

/**
 * Processing related interface
 *
 * A DimensionHandler is an object that encapsulates indexing, column merging/building, and querying operations
 * for a given dimension type (e.g., dict-encoded String, Long).
 *
 * These operations are handled by sub-objects created through a DimensionHandler's methods:
 *   DimensionIndexer, DimensionMerger, and DimensionColumnReader, respectively.
 *
 * Each DimensionHandler object is associated with a single dimension.
 *
 * This interface allows type-specific behavior column logic, such as choice of indexing structures and disk formats.
 * to be contained within a type-specific set of handler objects, simplifying processing classes
 * such as IncrementalIndex and IndexMerger and allowing for abstracted development of additional dimension types.
 *
 * A dimension may have two representations, an encoded representation and a actual representation.
 * For example, a value for a String dimension has an integer dictionary encoding, and an actual String representation.
 *
 * A DimensionHandler is a stateless object, and thus thread-safe; its methods should be pure functions.
 *
 * The EncodedType and ActualType are Comparable because columns used as dimensions must have sortable values.
 *
 * @param <EncodedType> class of a single encoded value
 * @param <EncodedKeyComponentType> A row key contains a component for each dimension, this param specifies the
 *                                 class of this dimension's key component. A column type that supports multivalue rows
 *                                 should use an array type (Strings would use int[]). Column types without multivalue
 *                                 row support should use single objects (e.g., Long, Float).
 * @param <ActualType> class of a single actual value
 */
public interface DimensionHandler
    <EncodedType extends Comparable<EncodedType>, EncodedKeyComponentType, ActualType extends Comparable<ActualType>>
{
  /**
   * Get the name of the column associated with this handler.
   *
   * This string would be the output name of the column during ingestion, and the name of an input column when querying.
   *
   * @return Dimension name
   */
  String getDimensionName();


  /**
   * Creates a new DimensionIndexer, a per-dimension object responsible for processing ingested rows in-memory, used
   * by the IncrementalIndex. See {@link DimensionIndexer} interface for more information.
   *
   * @return A new DimensionIndexer object.
   */
  DimensionIndexer<EncodedType, EncodedKeyComponentType, ActualType> makeIndexer();


  /**
   * Creates a new DimensionMergerV9, a per-dimension object responsible for merging indexes/row data across segments
   * and building the on-disk representation of a dimension. For use with IndexMergerV9 only.
   *
   * See {@link DimensionMergerV9} interface for more information.
   *
   * @param indexSpec     Specification object for the index merge
   * @param segmentWriteOutMedium  this SegmentWriteOutMedium object could be used internally in the created merger, if needed
   * @param capabilities  The ColumnCapabilities of the dimension represented by this DimensionHandler
   * @param progress      ProgressIndicator used by the merging process

   * @return A new DimensionMergerV9 object.
   */
  DimensionMergerV9<EncodedKeyComponentType> makeMerger(
      IndexSpec indexSpec,
      SegmentWriteOutMedium segmentWriteOutMedium,
      ColumnCapabilities capabilities,
      ProgressIndicator progress
  ) throws IOException;


  /**
   * Given an key component representing a single set of row value(s) for this dimension as an Object,
   * return the length of the key component after appropriate type-casting.
   *
   * For example, a dictionary encoded String dimension would receive an int[] as input to this method,
   * while a Long numeric dimension would receive a single Long object (no multivalue support)
   *
   * @param dimVals Values for this dimension from a row
   * @return Size of dimVals
   */
  int getLengthOfEncodedKeyComponent(EncodedKeyComponentType dimVals);


  /**
   * Given two key components representing sorted encoded row value(s), return the result of their comparison.
   *
   * If the two key components have different lengths, the shorter component should be ordered first in the comparison.
   *
   * Otherwise, this function should iterate through the key components and return the comparison of the
   * first difference.
   *
   * For dimensions that do not support multivalue rows, lhs and rhs can be compared directly.
   *
   * @param lhs key component from a row
   * @param rhs key component from a row
   *
   * @return integer indicating comparison result of key components
   */
  int compareSortedEncodedKeyComponents(EncodedKeyComponentType lhs, EncodedKeyComponentType rhs);


  /**
   * Given two key components representing sorted encoded row value(s), check that the two key components
   * have the same encoded values, or if the encoded values differ, that they translate into the same actual values,
   * using the mappings provided by lhsEncodings and rhsEncodings (if applicable).
   *
   * If validation fails, this method should throw a SegmentValidationException.
   *
   * Used by IndexIO for validating segments.
   *
   * See StringDimensionHandler.validateSortedEncodedKeyComponents() for a reference implementation.
   *
   * @param lhs key component from a row
   * @param rhs key component from a row
   * @param lhsEncodings encoding lookup from lhs's segment, null if not applicable for this dimension's type
   * @param rhsEncodings encoding lookup from rhs's segment, null if not applicable for this dimension's type
   */
  void validateSortedEncodedKeyComponents(
      EncodedKeyComponentType lhs,
      EncodedKeyComponentType rhs,
      Indexed<ActualType> lhsEncodings,
      Indexed<ActualType> rhsEncodings
  ) throws SegmentValidationException;


  /**
   * Given a Column, return a type-specific object that can be used to retrieve row values.
   *
   * For example:
   * - A String-typed implementation would return the result of column.getDictionaryEncoding()
   * - A long-typed implemention would return the result of column.getGenericColumn().
   *
   * @param column Column for this dimension from a QueryableIndex
   * @return The type-specific column subobject for this dimension.
   */
  Closeable getSubColumn(Column column);


  /**
   * Given a subcolumn from getSubColumn, and the index of the current row, retrieve a dimension's values
   * from a row as an EncodedKeyComponentType.
   *
   * For example:
   * - A String-typed implementation would read the current row from a DictionaryEncodedColumn as an int[].
   * - A long-typed implemention would read the current row from a GenericColumn and return a Long.
   *
   * @param column Column for this dimension from a QueryableIndex
   * @param currRow The index of the row to retrieve
   * @return The key component for this dimension from the current row of the column.
   */
  EncodedKeyComponentType getEncodedKeyComponentFromColumn(Closeable column, int currRow);
}

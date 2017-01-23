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

import com.google.common.collect.ImmutableList;
import io.druid.data.input.impl.DimensionSchema.MultiValueHandling;
import io.druid.java.util.common.IAE;
import io.druid.java.util.common.parsers.ParseException;
import io.druid.query.ColumnSelectorPlus;
import io.druid.query.dimension.ColumnSelectorStrategy;
import io.druid.query.dimension.ColumnSelectorStrategyFactory;
import io.druid.query.dimension.DimensionSpec;
import io.druid.segment.column.ColumnCapabilities;
import io.druid.segment.column.ColumnCapabilitiesImpl;
import io.druid.segment.column.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DimensionHandlerUtils
{
  private DimensionHandlerUtils() {}

  public final static ColumnCapabilities DEFAULT_STRING_CAPABILITIES =
      new ColumnCapabilitiesImpl().setType(ValueType.STRING)
                                  .setDictionaryEncoded(true)
                                  .setHasBitmapIndexes(true);

  public static DimensionHandler getHandlerFromCapabilities(
      String dimensionName,
      ColumnCapabilities capabilities,
      MultiValueHandling multiValueHandling
  )
  {
    if (capabilities == null) {
      return new StringDimensionHandler(dimensionName, multiValueHandling);
    }

    multiValueHandling = multiValueHandling == null ? MultiValueHandling.ofDefault() : multiValueHandling;

    if (capabilities.getType() == ValueType.STRING) {
      if (!capabilities.isDictionaryEncoded() || !capabilities.hasBitmapIndexes()) {
        throw new IAE("String column must have dictionary encoding and bitmap index.");
      }
      return new StringDimensionHandler(dimensionName, multiValueHandling);
    }

    // Return a StringDimensionHandler by default (null columns will be treated as String typed)
    return new StringDimensionHandler(dimensionName, multiValueHandling);
  }

  public static List<ValueType> getValueTypesFromDimensionSpecs(List<DimensionSpec> dimSpecs)
  {
    List<ValueType> types = new ArrayList<>(dimSpecs.size());
    for (DimensionSpec dimSpec : dimSpecs) {
      types.add(dimSpec.getOutputType());
    }
    return types;
  }

  /**
   * Convenience function equivalent to calling
   * {@link #createColumnSelectorPluses(ColumnSelectorStrategyFactory, List, ColumnSelectorFactory)} with a singleton
   * list of dimensionSpecs and then retrieving the only element in the returned array.
   *
   * @param <ColumnSelectorStrategyClass> The strategy type created by the provided strategy factory.
   * @param strategyFactory               A factory provided by query engines that generates type-handling strategies
   * @param dimensionSpec                 column to generate a ColumnSelectorPlus object for
   * @param cursor                        Used to create value selectors for columns.
   *
   * @return A ColumnSelectorPlus object
   */
  public static <ColumnSelectorStrategyClass extends ColumnSelectorStrategy> ColumnSelectorPlus<ColumnSelectorStrategyClass> createColumnSelectorPlus(
      ColumnSelectorStrategyFactory<ColumnSelectorStrategyClass> strategyFactory,
      DimensionSpec dimensionSpec,
      ColumnSelectorFactory cursor
  )
  {
    return createColumnSelectorPluses(strategyFactory, ImmutableList.of(dimensionSpec), cursor)[0];
  }

  /**
   * Creates an array of ColumnSelectorPlus objects, selectors that handle type-specific operations within
   * query processing engines, using a strategy factory provided by the query engine. One ColumnSelectorPlus
   * will be created for each column specified in dimensionSpecs.
   *
   * The ColumnSelectorPlus provides access to a type strategy (e.g., how to group on a float column)
   * and a value selector for a single column.
   *
   * A caller should define a strategy factory that provides an interface for type-specific operations
   * in a query engine. See GroupByStrategyFactory for a reference.
   *
   * @param <ColumnSelectorStrategyClass> The strategy type created by the provided strategy factory.
   * @param strategyFactory A factory provided by query engines that generates type-handling strategies
   * @param dimensionSpecs The set of columns to generate ColumnSelectorPlus objects for
   * @param cursor Used to create value selectors for columns.
   * @return An array of ColumnSelectorPlus objects, in the order of the columns specified in dimensionSpecs
   */
  public static <ColumnSelectorStrategyClass extends ColumnSelectorStrategy> ColumnSelectorPlus<ColumnSelectorStrategyClass>[] createColumnSelectorPluses(
      ColumnSelectorStrategyFactory<ColumnSelectorStrategyClass> strategyFactory,
      List<DimensionSpec> dimensionSpecs,
      ColumnSelectorFactory cursor
  )
  {
    int dimCount = dimensionSpecs.size();
    ColumnSelectorPlus<ColumnSelectorStrategyClass>[] dims = new ColumnSelectorPlus[dimCount];
    for (int i = 0; i < dimCount; i++) {
      final DimensionSpec dimSpec = dimensionSpecs.get(i);
      final String dimName = dimSpec.getDimension();
      ColumnSelectorStrategyClass strategy = makeStrategy(
          strategyFactory,
          dimName,
          cursor.getColumnCapabilities(dimSpec.getDimension()),
          dimSpec.getExtractionFn() != null
      );
      final ColumnValueSelector selector = getColumnValueSelectorFromDimensionSpec(
          dimSpec,
          cursor
      );
      final ColumnSelectorPlus<ColumnSelectorStrategyClass> selectorPlus = new ColumnSelectorPlus<>(
          dimName,
          dimSpec.getOutputName(),
          strategy,
          selector
      );
      dims[i] = selectorPlus;
    }
    return dims;
  }

  // When determining the capabilites of a column during query processing, this function
  // adjusts the capabilities for columns that cannot be handled as-is to manageable defaults
  // (e.g., treating missing columns as empty String columns)
  private static ColumnCapabilities getEffectiveCapabilities(
      String dimName,
      ColumnCapabilities capabilities,
      boolean hasExFn
  )
  {
    if (capabilities == null) {
      capabilities = DEFAULT_STRING_CAPABILITIES;
    }

    // Complex dimension type is not supported
    if (capabilities.getType() == ValueType.COMPLEX) {
      capabilities = DEFAULT_STRING_CAPABILITIES;
    }

    // Currently, all extractionFns output Strings, so the column will return String values via a
    // DimensionSelector if an extractionFn is present.
    if (hasExFn) {
      capabilities = DEFAULT_STRING_CAPABILITIES;
    }

    return capabilities;
  }

  private static ColumnValueSelector getColumnValueSelectorFromDimensionSpec(
      DimensionSpec dimSpec,
      ColumnSelectorFactory columnSelectorFactory
  )
  {
    String dimName = dimSpec.getDimension();
    ColumnCapabilities capabilities = columnSelectorFactory.getColumnCapabilities(dimName);
    capabilities = getEffectiveCapabilities(dimName, capabilities, dimSpec.getExtractionFn() != null);
    switch (capabilities.getType()) {
      case STRING:
        return columnSelectorFactory.makeDimensionSelector(dimSpec);
      case LONG:
        return columnSelectorFactory.makeLongColumnSelector(dimSpec.getDimension());
      case FLOAT:
        return columnSelectorFactory.makeFloatColumnSelector(dimSpec.getDimension());
      default:
        return null;
    }
  }

  private static <ColumnSelectorStrategyClass extends ColumnSelectorStrategy> ColumnSelectorStrategyClass makeStrategy(
      ColumnSelectorStrategyFactory<ColumnSelectorStrategyClass> strategyFactory,
      String dimName,
      ColumnCapabilities capabilities,
      boolean hasExFn
  )
  {
    capabilities = getEffectiveCapabilities(dimName, capabilities, hasExFn);
    return strategyFactory.makeColumnSelectorStrategy(capabilities);
  }

  private static final Pattern LONG_PAT = Pattern.compile("[-|+]?\\d+");

  public static Long convertObjectToLong(Object valObj)
  {
    if (valObj == null) {
      return 0L;
    }

    if (valObj instanceof Long) {
      return (Long) valObj;
    } else if (valObj instanceof Number) {
      return ((Number) valObj).longValue();
    } else if (valObj instanceof String) {
      try {
        String s = ((String) valObj).replace(",", "");
        return LONG_PAT.matcher(s).matches() ? Long.valueOf(s) : Double.valueOf(s).longValue();
      }
      catch (Exception e) {
        throw new ParseException(e, "Unable to parse value[%s] as long", valObj);
      }
    } else {
      throw new ParseException("Unknown type[%s]", valObj.getClass());
    }
  }

  public static Float convertObjectToFloat(Object valObj)
  {
    if (valObj == null) {
      return 0.0f;
    }

    if (valObj instanceof Float) {
      return (Float) valObj;
    } else if (valObj instanceof Number) {
      return ((Number) valObj).floatValue();
    } else if (valObj instanceof String) {
      try {
        return Float.valueOf(((String) valObj).replace(",", ""));
      }
      catch (Exception e) {
        throw new ParseException(e, "Unable to parse value[%s] as float", valObj);
      }
    } else {
      throw new ParseException("Unknown type[%s]", valObj.getClass());
    }
  }
}

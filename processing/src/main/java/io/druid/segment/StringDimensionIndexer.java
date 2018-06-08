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

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import io.druid.collections.bitmap.BitmapFactory;
import io.druid.collections.bitmap.MutableBitmap;
import io.druid.common.config.NullHandling;
import io.druid.data.input.impl.DimensionSchema.MultiValueHandling;
import io.druid.java.util.common.ISE;
import io.druid.java.util.common.guava.Comparators;
import io.druid.query.dimension.DefaultDimensionSpec;
import io.druid.query.dimension.DimensionSpec;
import io.druid.query.extraction.ExtractionFn;
import io.druid.query.filter.ValueMatcher;
import io.druid.query.monomorphicprocessing.RuntimeShapeInspector;
import io.druid.segment.data.ArrayBasedIndexedInts;
import io.druid.segment.data.Indexed;
import io.druid.segment.data.IndexedInts;
import io.druid.segment.data.IndexedIterable;
import io.druid.segment.filter.BooleanValueMatcher;
import io.druid.segment.incremental.IncrementalIndex;
import io.druid.segment.incremental.IncrementalIndexRow;
import io.druid.segment.incremental.IncrementalIndexRowHolder;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntRBTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

public class StringDimensionIndexer implements DimensionIndexer<Integer, int[], String>
{

  private static String emptytoNullIfNeeded(Object o)
  {
    return o != null
           ? NullHandling.emptyToNullIfNeeded(o.toString())
           : null;
  }

  private static final int ABSENT_VALUE_ID = -1;

  private static class DimensionDictionary
  {
    @Nullable
    private String minValue = null;
    @Nullable
    private String maxValue = null;
    private int idForNull = ABSENT_VALUE_ID;

    private final Object2IntMap<String> valueToId = new Object2IntOpenHashMap<>();

    private final List<String> idToValue = Lists.newArrayList();
    private final Object lock;

    public DimensionDictionary()
    {
      this.lock = new Object();
      valueToId.defaultReturnValue(-1);
    }

    public int getId(String value)
    {
      synchronized (lock) {
        if (value == null) {
          return idForNull;
        }
        return valueToId.getInt(value);
      }
    }

    public String getValue(int id)
    {
      synchronized (lock) {
        if (id == idForNull) {
          return null;
        }
        return idToValue.get(id);
      }
    }

    public int size()
    {
      synchronized (lock) {
        // using idToValue rather than valueToId because the valueToId doesn't account null value, if it is present.
        return idToValue.size();
      }
    }

    public int add(@Nullable String originalValue)
    {
      synchronized (lock) {
        if (originalValue == null) {
          if (idForNull == ABSENT_VALUE_ID) {
            idForNull = size();
            idToValue.add(null);
          }
          return idForNull;
        }
        int prev = valueToId.getInt(originalValue);
        if (prev >= 0) {
          return prev;
        }
        final int index = size();
        valueToId.put(originalValue, index);
        idToValue.add(originalValue);
        minValue = minValue == null || minValue.compareTo(originalValue) > 0 ? originalValue : minValue;
        maxValue = maxValue == null || maxValue.compareTo(originalValue) < 0 ? originalValue : maxValue;
        return index;
      }
    }

    public String getMinValue()
    {
      synchronized (lock) {
        return minValue;
      }
    }

    public String getMaxValue()
    {
      synchronized (lock) {
        return maxValue;
      }
    }

    public SortedDimensionDictionary sort()
    {
      synchronized (lock) {
        return new SortedDimensionDictionary(idToValue, size());
      }
    }
  }

  private static class SortedDimensionDictionary
  {
    private final List<String> sortedVals;
    private final int[] idToIndex;
    private final int[] indexToId;

    public SortedDimensionDictionary(List<String> idToValue, int length)
    {
      Object2IntSortedMap<String> sortedMap = new Object2IntRBTreeMap<>(Comparators.naturalNullsFirst());
      for (int id = 0; id < length; id++) {
        String value = idToValue.get(id);
        sortedMap.put(value, id);
      }
      this.sortedVals = Lists.newArrayList(sortedMap.keySet());
      this.idToIndex = new int[length];
      this.indexToId = new int[length];
      int index = 0;
      for (IntIterator iterator = sortedMap.values().iterator(); iterator.hasNext(); ) {
        int id = iterator.nextInt();
        idToIndex[id] = index;
        indexToId[index] = id;
        index++;
      }
    }

    public int getUnsortedIdFromSortedId(int index)
    {
      return indexToId[index];
    }

    public int getSortedIdFromUnsortedId(int id)
    {
      return idToIndex[id];
    }

    public String getValueFromSortedId(int index)
    {
      return sortedVals.get(index);
    }
  }

  private final DimensionDictionary dimLookup;
  private final MultiValueHandling multiValueHandling;
  private final boolean hasBitmapIndexes;
  private SortedDimensionDictionary sortedLookup;
  private boolean hasMultipleValues = false;

  public StringDimensionIndexer(MultiValueHandling multiValueHandling, boolean hasBitmapIndexes)
  {
    this.dimLookup = new DimensionDictionary();
    this.multiValueHandling = multiValueHandling == null ? MultiValueHandling.ofDefault() : multiValueHandling;
    this.hasBitmapIndexes = hasBitmapIndexes;
  }

  @Override
  public int[] processRowValsToUnsortedEncodedKeyComponent(Object dimValues, boolean reportParseExceptions)
  {
    final int[] encodedDimensionValues;
    final int oldDictSize = dimLookup.size();

    if (dimValues == null) {
      final int nullId = dimLookup.getId(null);
      encodedDimensionValues = nullId == ABSENT_VALUE_ID ? new int[]{dimLookup.add(null)} : new int[]{nullId};
    } else if (dimValues instanceof List) {
      List<Object> dimValuesList = (List) dimValues;
      if (dimValuesList.isEmpty()) {
        dimLookup.add(null);
        encodedDimensionValues = IntArrays.EMPTY_ARRAY;
      } else if (dimValuesList.size() == 1) {
        encodedDimensionValues = new int[]{dimLookup.add(emptytoNullIfNeeded(dimValuesList.get(0)))};
      } else {
        hasMultipleValues = true;
        final String[] dimensionValues = new String[dimValuesList.size()];
        for (int i = 0; i < dimValuesList.size(); i++) {
          dimensionValues[i] = emptytoNullIfNeeded(dimValuesList.get(i));
        }
        if (multiValueHandling.needSorting()) {
          // Sort multival row by their unencoded values first.
          Arrays.sort(dimensionValues, Comparators.naturalNullsFirst());
        }

        final int[] retVal = new int[dimensionValues.length];

        int prevId = -1;
        int pos = 0;
        for (String dimensionValue : dimensionValues) {
          if (multiValueHandling != MultiValueHandling.SORTED_SET) {
            retVal[pos++] = dimLookup.add(dimensionValue);
            continue;
          }
          int index = dimLookup.add(dimensionValue);
          if (index != prevId) {
            prevId = retVal[pos++] = index;
          }
        }

        encodedDimensionValues = pos == retVal.length ? retVal : Arrays.copyOf(retVal, pos);
      }
    } else {
      encodedDimensionValues = new int[]{dimLookup.add(emptytoNullIfNeeded(dimValues))};
    }

    // If dictionary size has changed, the sorted lookup is no longer valid.
    if (oldDictSize != dimLookup.size()) {
      sortedLookup = null;
    }

    return encodedDimensionValues;
  }

  @Override
  public long estimateEncodedKeyComponentSize(int[] key)
  {
    // string length is being accounted for each time they are referenced, based on dimension handler interface,
    // even though they are stored just once. It may overestimate the size by a bit, but we wanted to leave
    // more buffer to be safe
    long estimatedSize = key.length * Integer.BYTES;
    estimatedSize += Arrays.stream(key)
                           .filter(element -> dimLookup.getValue(element) != null)
                           .mapToLong(element -> dimLookup.getValue(element).length() * Character.BYTES)
                           .sum();
    return estimatedSize;
  }

  public Integer getSortedEncodedValueFromUnsorted(Integer unsortedIntermediateValue)
  {
    return sortedLookup().getSortedIdFromUnsortedId(unsortedIntermediateValue);
  }

  @Override
  public Integer getUnsortedEncodedValueFromSorted(Integer sortedIntermediateValue)
  {
    return sortedLookup().getUnsortedIdFromSortedId(sortedIntermediateValue);
  }

  @Override
  public Indexed<String> getSortedIndexedValues()
  {
    return new Indexed<String>()
    {
      @Override
      public Class<? extends String> getClazz()
      {
        return String.class;
      }

      @Override
      public int size()
      {
        return getCardinality();
      }

      @Override
      public String get(int index)
      {
        return getActualValue(index, true);
      }

      @Override
      public int indexOf(String value)
      {
        int id = getEncodedValue(value, false);
        return id < 0 ? ABSENT_VALUE_ID : getSortedEncodedValueFromUnsorted(id);
      }

      @Override
      public Iterator<String> iterator()
      {
        return IndexedIterable.create(this).iterator();
      }

      @Override
      public void inspectRuntimeShape(RuntimeShapeInspector inspector)
      {
        // nothing to inspect
      }
    };
  }

  @Override
  public String getMinValue()
  {
    return dimLookup.getMinValue();
  }

  @Override
  public String getMaxValue()
  {
    return dimLookup.getMaxValue();
  }

  @Override
  public int getCardinality()
  {
    return dimLookup.size();
  }

  @Override
  public int compareUnsortedEncodedKeyComponents(int[] lhs, int[] rhs)
  {
    int lhsLen = lhs.length;
    int rhsLen = rhs.length;

    int retVal = Ints.compare(lhsLen, rhsLen);
    int valsIndex = 0;
    while (retVal == 0 && valsIndex < lhsLen) {
      int lhsVal = lhs[valsIndex];
      int rhsVal = rhs[valsIndex];
      if (lhsVal != rhsVal) {
        final String lhsValActual = getActualValue(lhsVal, false);
        final String rhsValActual = getActualValue(rhsVal, false);
        if (lhsValActual != null && rhsValActual != null) {
          retVal = lhsValActual.compareTo(rhsValActual);
        } else if (lhsValActual == null ^ rhsValActual == null) {
          retVal = lhsValActual == null ? -1 : 1;
        }
      }
      ++valsIndex;
    }
    return retVal;
  }

  @Override
  public boolean checkUnsortedEncodedKeyComponentsEqual(int[] lhs, int[] rhs)
  {
    return Arrays.equals(lhs, rhs);
  }

  @Override
  public int getUnsortedEncodedKeyComponentHashCode(int[] key)
  {
    return Arrays.hashCode(key);
  }

  @Override
  public DimensionSelector makeDimensionSelector(
      final DimensionSpec spec,
      final IncrementalIndexRowHolder currEntry,
      final IncrementalIndex.DimensionDesc desc
  )
  {
    final ExtractionFn extractionFn = spec.getExtractionFn();

    final int dimIndex = desc.getIndex();
    final int maxId = getCardinality();

    class IndexerDimensionSelector implements DimensionSelector, IdLookup
    {
      private final ArrayBasedIndexedInts indexedInts = new ArrayBasedIndexedInts();
      private int[] nullIdIntArray;

      @Override
      public IndexedInts getRow()
      {
        final Object[] dims = currEntry.get().getDims();

        int[] indices;
        if (dimIndex < dims.length) {
          indices = (int[]) dims[dimIndex];
        } else {
          indices = null;
        }

        int[] row = null;
        int rowSize = 0;

        // usually due to currEntry's rowIndex is smaller than the row's rowIndex in which this dim first appears
        if (indices == null || indices.length == 0) {
          if (hasMultipleValues) {
            row = IntArrays.EMPTY_ARRAY;
            rowSize = 0;
          } else {
            final int nullId = getEncodedValue(null, false);
            if (nullId > -1) {
              if (nullIdIntArray == null) {
                nullIdIntArray = new int[]{nullId};
              }
              row = nullIdIntArray;
              rowSize = 1;
            } else {
              // doesn't contain nullId, then empty array is used
              // Choose to use ArrayBasedIndexedInts later, instead of special "empty" IndexedInts, for monomorphism
              row = IntArrays.EMPTY_ARRAY;
              rowSize = 0;
            }
          }
        }

        if (row == null && indices != null && indices.length > 0) {
          row = indices;
          rowSize = indices.length;
        }

        indexedInts.setValues(row, rowSize);
        return indexedInts;
      }

      @Override
      public ValueMatcher makeValueMatcher(final String value)
      {
        if (extractionFn == null) {
          final int valueId = lookupId(value);
          if (valueId >= 0 || value == null) {
            return new ValueMatcher()
            {
              @Override
              public boolean matches()
              {
                Object[] dims = currEntry.get().getDims();
                if (dimIndex >= dims.length) {
                  return value == null;
                }

                int[] dimsInt = (int[]) dims[dimIndex];
                if (dimsInt == null || dimsInt.length == 0) {
                  return value == null;
                }

                for (int id : dimsInt) {
                  if (id == valueId) {
                    return true;
                  }
                }
                return false;
              }

              @Override
              public void inspectRuntimeShape(RuntimeShapeInspector inspector)
              {
                // nothing to inspect
              }
            };
          } else {
            return BooleanValueMatcher.of(false);
          }
        } else {
          // Employ caching BitSet optimization
          return makeValueMatcher(Predicates.equalTo(value));
        }
      }

      @Override
      public ValueMatcher makeValueMatcher(final Predicate<String> predicate)
      {
        final BitSet checkedIds = new BitSet(maxId);
        final BitSet matchingIds = new BitSet(maxId);
        final boolean matchNull = predicate.apply(null);

        // Lazy matcher; only check an id if matches() is called.
        return new ValueMatcher()
        {
          @Override
          public boolean matches()
          {
            Object[] dims = currEntry.get().getDims();
            if (dimIndex >= dims.length) {
              return matchNull;
            }

            int[] dimsInt = (int[]) dims[dimIndex];
            if (dimsInt == null || dimsInt.length == 0) {
              return matchNull;
            }

            for (int id : dimsInt) {
              if (checkedIds.get(id)) {
                if (matchingIds.get(id)) {
                  return true;
                }
              } else {
                final boolean matches = predicate.apply(lookupName(id));
                checkedIds.set(id);
                if (matches) {
                  matchingIds.set(id);
                  return true;
                }
              }
            }
            return false;
          }

          @Override
          public void inspectRuntimeShape(RuntimeShapeInspector inspector)
          {
            // nothing to inspect
          }
        };
      }

      @Override
      public int getValueCardinality()
      {
        return maxId;
      }

      @Override
      public String lookupName(int id)
      {
        if (id >= maxId) {
          throw new ISE("id[%d] >= maxId[%d]", id, maxId);
        }
        final String strValue = getActualValue(id, false);
        return extractionFn == null ? strValue : extractionFn.apply(strValue);
      }

      @Override
      public boolean nameLookupPossibleInAdvance()
      {
        return true;
      }

      @Nullable
      @Override
      public IdLookup idLookup()
      {
        return extractionFn == null ? this : null;
      }

      @Override
      public int lookupId(String name)
      {
        if (extractionFn != null) {
          throw new UnsupportedOperationException(
              "cannot perform lookup when applying an extraction function"
          );
        }
        return getEncodedValue(name, false);
      }

      @SuppressWarnings("deprecation")
      @Nullable
      @Override
      public Object getObject()
      {
        IncrementalIndexRow key = currEntry.get();
        if (key == null) {
          return null;
        }

        Object[] dims = key.getDims();
        if (dimIndex >= dims.length) {
          return null;
        }

        return convertUnsortedEncodedKeyComponentToActualArrayOrList(
            (int[]) dims[dimIndex],
            DimensionIndexer.ARRAY
        );
      }

      @SuppressWarnings("deprecation")
      @Override
      public Class classOfObject()
      {
        return Object.class;
      }

      @Override
      public void inspectRuntimeShape(RuntimeShapeInspector inspector)
      {
        // nothing to inspect
      }
    }
    return new IndexerDimensionSelector();
  }

  @Override
  public ColumnValueSelector<?> makeColumnValueSelector(
      IncrementalIndexRowHolder currEntry,
      IncrementalIndex.DimensionDesc desc
  )
  {
    return makeDimensionSelector(DefaultDimensionSpec.of(desc.getName()), currEntry, desc);
  }

  @Override
  public Object convertUnsortedEncodedKeyComponentToActualArrayOrList(int[] key, boolean asList)
  {
    if (key == null || key.length == 0) {
      return null;
    }
    if (key.length == 1) {
      return getActualValue(key[0], false);
    } else {
      if (asList) {
        List<Comparable> rowVals = new ArrayList<>(key.length);
        for (int id : key) {
          String val = getActualValue(id, false);
          rowVals.add(NullHandling.nullToEmptyIfNeeded(val));
        }
        return rowVals;
      } else {
        String[] rowArray = new String[key.length];
        for (int i = 0; i < key.length; i++) {
          String val = getActualValue(key[i], false);
          rowArray[i] = NullHandling.nullToEmptyIfNeeded(val);
        }
        return rowArray;
      }
    }
  }

  @Override
  public ColumnValueSelector convertUnsortedValuesToSorted(ColumnValueSelector selectorWithUnsortedValues)
  {
    DimensionSelector dimSelectorWithUnsortedValues = (DimensionSelector) selectorWithUnsortedValues;
    class SortedDimensionSelector implements DimensionSelector, IndexedInts
    {
      @Override
      public int size()
      {
        return dimSelectorWithUnsortedValues.getRow().size();
      }

      @Override
      public int get(int index)
      {
        return sortedLookup().getSortedIdFromUnsortedId(dimSelectorWithUnsortedValues.getRow().get(index));
      }

      @Override
      public IndexedInts getRow()
      {
        return this;
      }

      @Override
      public ValueMatcher makeValueMatcher(@Nullable String value)
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public ValueMatcher makeValueMatcher(Predicate<String> predicate)
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public int getValueCardinality()
      {
        return dimSelectorWithUnsortedValues.getValueCardinality();
      }

      @Nullable
      @Override
      public String lookupName(int id)
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean nameLookupPossibleInAdvance()
      {
        throw new UnsupportedOperationException();
      }

      @Nullable
      @Override
      public IdLookup idLookup()
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public void inspectRuntimeShape(RuntimeShapeInspector inspector)
      {
        inspector.visit("dimSelectorWithUnsortedValues", dimSelectorWithUnsortedValues);
      }

      @Nullable
      @Override
      public Object getObject()
      {
        return dimSelectorWithUnsortedValues.getObject();
      }

      @Override
      public Class classOfObject()
      {
        return dimSelectorWithUnsortedValues.classOfObject();
      }
    }
    return new SortedDimensionSelector();
  }

  @Override
  public void fillBitmapsFromUnsortedEncodedKeyComponent(
      int[] key, int rowNum, MutableBitmap[] bitmapIndexes, BitmapFactory factory
  )
  {
    if (!hasBitmapIndexes) {
      throw new UnsupportedOperationException("This column does not include bitmap indexes");
    }

    for (int dimValIdx : key) {
      if (bitmapIndexes[dimValIdx] == null) {
        bitmapIndexes[dimValIdx] = factory.makeEmptyMutableBitmap();
      }
      bitmapIndexes[dimValIdx].add(rowNum);
    }
  }

  private SortedDimensionDictionary sortedLookup()
  {
    return sortedLookup == null ? sortedLookup = dimLookup.sort() : sortedLookup;
  }

  private String getActualValue(int intermediateValue, boolean idSorted)
  {
    if (idSorted) {
      return sortedLookup().getValueFromSortedId(intermediateValue);
    } else {
      return dimLookup.getValue(intermediateValue);

    }
  }

  private int getEncodedValue(String fullValue, boolean idSorted)
  {
    int unsortedId = dimLookup.getId(fullValue);

    if (idSorted) {
      return sortedLookup().getSortedIdFromUnsortedId(unsortedId);
    } else {
      return unsortedId;
    }
  }
}

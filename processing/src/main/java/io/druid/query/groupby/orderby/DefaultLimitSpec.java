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

package io.druid.query.groupby.orderby;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.metamx.common.ISE;
import com.metamx.common.guava.Sequence;
import io.druid.common.guava.Sequences;
import io.druid.data.input.Row;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.PostAggregator;
import io.druid.query.dimension.DimensionSpec;
import io.druid.query.ordering.StringComparators.StringComparator;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 */
public class DefaultLimitSpec implements LimitSpec
{
  private static final byte CACHE_KEY = 0x1;

  private final List<OrderByColumnSpec> columns;
  private final int limit;
  private final int skip;

  @JsonCreator
  public DefaultLimitSpec(
      @JsonProperty("columns") List<OrderByColumnSpec> columns,
      @JsonProperty("limit") Integer limit,
      @JsonProperty("skip") Integer skip
  )
  {
    this.columns = (columns == null) ? ImmutableList.<OrderByColumnSpec>of() : columns;
    this.limit = (limit == null) ? Integer.MAX_VALUE : limit;
    this.skip = (skip == null || skip < 0) ? 0 : skip;

    Preconditions.checkArgument(this.limit > 0, "limit[%s] must be >0", limit);
  }

  public DefaultLimitSpec(List<OrderByColumnSpec> columns, Integer limit)
  {
    this(columns, limit, 0);
  }

  @JsonProperty
  public List<OrderByColumnSpec> getColumns()
  {
    return columns;
  }

  @JsonProperty
  public int getLimit()
  {
    return limit;
  }

  @JsonProperty
  public int getSkip()
  {
    return skip;
  }

  @Override
  public Function<Sequence<Row>, Sequence<Row>> build(
      List<DimensionSpec> dimensions, List<AggregatorFactory> aggs, List<PostAggregator> postAggs
  )
  {
    if (columns.isEmpty()) {
      return new LimitingFn(limit, skip);
    }

    // Materialize the Comparator first for fast-fail error checking.
    final Ordering<Row> ordering = makeComparator(dimensions, aggs, postAggs);

    if (limit == Integer.MAX_VALUE) {
      return new SortingFn(ordering, skip);
    } else {
      return new TopNFunction(ordering, limit, skip);
    }
  }

  @Override
  public LimitSpec merge(LimitSpec other)
  {
    return this;
  }

  private Ordering<Row> makeComparator(
      List<DimensionSpec> dimensions, List<AggregatorFactory> aggs, List<PostAggregator> postAggs
  )
  {
    Ordering<Row> ordering = new Ordering<Row>()
    {
      @Override
      public int compare(Row left, Row right)
      {
        return Longs.compare(left.getTimestampFromEpoch(), right.getTimestampFromEpoch());
      }
    };

    Map<String, DimensionSpec> dimensionsMap = Maps.newHashMap();
    for (DimensionSpec spec : dimensions) {
      dimensionsMap.put(spec.getOutputName(), spec);
    }

    Map<String, AggregatorFactory> aggregatorsMap = Maps.newHashMap();
    for (final AggregatorFactory agg : aggs) {
      aggregatorsMap.put(agg.getName(), agg);
    }

    Map<String, PostAggregator> postAggregatorsMap = Maps.newHashMap();
    for (PostAggregator postAgg : postAggs) {
      postAggregatorsMap.put(postAgg.getName(), postAgg);
    }

    for (OrderByColumnSpec columnSpec : columns) {
      String columnName = columnSpec.getDimension();
      Ordering<Row> nextOrdering = null;

      if (postAggregatorsMap.containsKey(columnName)) {
        nextOrdering = metricOrdering(columnName, postAggregatorsMap.get(columnName).getComparator());
      } else if (aggregatorsMap.containsKey(columnName)) {
        nextOrdering = metricOrdering(columnName, aggregatorsMap.get(columnName).getComparator());
      } else if (dimensionsMap.containsKey(columnName)) {
        nextOrdering = dimensionOrdering(columnName, columnSpec.getDimensionComparator());
      }

      if (nextOrdering == null) {
        throw new ISE("Unknown column in order clause[%s]", columnSpec);
      }

      switch (columnSpec.getDirection()) {
        case DESCENDING:
          nextOrdering = nextOrdering.reverse();
      }

      ordering = ordering.compound(nextOrdering);
    }

    return ordering;
  }

  private Ordering<Row> metricOrdering(final String column, final Comparator comparator)
  {
    return new Ordering<Row>()
    {
      @SuppressWarnings("unchecked")
      @Override
      public int compare(Row left, Row right)
      {
        return comparator.compare(left.getRaw(column), right.getRaw(column));
      }
    };
  }

  private Ordering<Row> dimensionOrdering(final String dimension, final StringComparator comparator)
  {
    return Ordering.from(comparator)
                   .nullsFirst()
                   .onResultOf(
                       new Function<Row, String>()
                       {
                         @Override
                         public String apply(Row input)
                         {
                           // Multi-value dimensions have all been flattened at this point;
                           final List<String> dimList = input.getDimension(dimension);
                           return dimList.isEmpty() ? null : dimList.get(0);
                         }
                       }
                   );
  }

  @Override
  public String toString()
  {
    return "DefaultLimitSpec{" +
           "columns='" + columns + '\'' +
           ", limit=" + limit +
           (skip > 0 ? ", skip=" + skip : "") +
           '}';
  }

  private static class LimitingFn implements Function<Sequence<Row>, Sequence<Row>>
  {
    private final int limit;
    private final int skip;

    public LimitingFn(int limit, int skip)
    {
      this.limit = limit;
      this.skip = skip;
    }

    @Override
    public Sequence<Row> apply(
        Sequence<Row> input
    )
    {
      if (skip > 0) {
        input = Sequences.filter(input, Sequences.<Row>skipper(skip));
      }
      return Sequences.limit(input, limit);
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

      LimitingFn that = (LimitingFn) o;

      if (limit != that.limit) {
        return false;
      }
      if (skip != that.skip) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode()
    {
      return Objects.hash(limit, skip);
    }
  }

  private static class SortingFn implements Function<Sequence<Row>, Sequence<Row>>
  {
    private final int skip;
    private final Ordering<Row> ordering;

    public SortingFn(Ordering<Row> ordering, int skip)
    {
      this.ordering = ordering;
      this.skip = skip;
    }

    @Override
    public Sequence<Row> apply(@Nullable Sequence<Row> input)
    {
      return Sequences.sort(input, ordering, skip);
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

      SortingFn sortingFn = (SortingFn) o;

      if (skip != sortingFn.skip) {
        return false;
      }
      if (ordering != null ? !ordering.equals(sortingFn.ordering) : sortingFn.ordering != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode()
    {
      return Objects.hash(ordering, skip);
    }
  }

  private static class TopNFunction implements Function<Sequence<Row>, Sequence<Row>>
  {
    private final TopNSorter<Row> sorter;
    private final int limit;
    private final int skip;

    public TopNFunction(Ordering<Row> ordering, int limit, int skip)
    {
      this.limit = limit;
      this.skip = skip;

      this.sorter = new TopNSorter<>(ordering);
    }

    @Override
    public Sequence<Row> apply(
        Sequence<Row> input
    )
    {
      Iterable<Row> rows = Sequences.toIterable(input);
      return Sequences.simple(sorter.toTopN(rows, limit, skip));
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

      TopNFunction that = (TopNFunction) o;

      if (limit != that.limit) {
        return false;
      }
      if (skip != that.skip) {
        return false;
      }
      if (sorter != null ? !sorter.equals(that.sorter) : that.sorter != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode()
    {
      return Objects.hash(sorter, limit, skip);
    }
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

    DefaultLimitSpec that = (DefaultLimitSpec) o;

    if (limit != that.limit) {
      return false;
    }
    if (skip != that.skip) {
      return false;
    }
    if (columns != null ? !columns.equals(that.columns) : that.columns != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(columns, limit, skip);
  }

  @Override
  public byte[] getCacheKey()
  {
    final byte[][] columnBytes = new byte[columns.size()][];
    int columnsBytesSize = 0;
    int index = 0;
    for (OrderByColumnSpec column : columns) {
      columnBytes[index] = column.getCacheKey();
      columnsBytesSize += columnBytes[index].length;
      ++index;
    }

    ByteBuffer buffer = ByteBuffer.allocate(1 + columnsBytesSize + Ints.BYTES + Ints.BYTES)
                                  .put(CACHE_KEY);
    for (byte[] columnByte : columnBytes) {
      buffer.put(columnByte);
    }
    buffer.put(Ints.toByteArray(limit));
    buffer.put(Ints.toByteArray(skip));
    return buffer.array();
  }
}

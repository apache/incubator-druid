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

package org.apache.druid.query.aggregation.tdigestsketch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tdunning.math.stats.MergingDigest;
import com.tdunning.math.stats.TDigest;
import org.apache.druid.query.aggregation.Aggregator;
import org.apache.druid.query.aggregation.AggregatorFactory;
import org.apache.druid.query.aggregation.AggregatorFactoryNotMergeableException;
import org.apache.druid.query.aggregation.AggregatorUtil;
import org.apache.druid.query.aggregation.BufferAggregator;
import org.apache.druid.query.cache.CacheKeyBuilder;
import org.apache.druid.segment.ColumnSelectorFactory;
import org.apache.druid.segment.ColumnValueSelector;
import org.apache.druid.segment.column.ColumnCapabilities;
import org.apache.druid.segment.column.ValueType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aggregation operations over the tdigest-based quantile sketch
 * available on <a href="https://github.com/tdunning/t-digest">github</a> and described
 * in the paper
 * <a href="https://github.com/tdunning/t-digest/blob/master/docs/t-digest-paper/histo.pdf">
 * Computing extremely accurate quantiles using t-digests</a>.
 * <p>
 * <p>
 * At the time of writing this implementation, there are two flavors of {@link TDigest}
 * available - {@link MergingDigest} and {@link com.tdunning.math.stats.AVLTreeDigest}.
 * This implementation uses {@link MergingDigest} since it is more suited for the cases
 * when we have to merge intermediate aggregations which Druid needs to do as
 * part of query processing.
 */
public class TDigestBuildSketchAggregatorFactory extends AggregatorFactory
{

  // Default compression
  public static final int DEFAULT_COMPRESSION = 100;

  @Nonnull
  private final String name;
  @Nonnull
  private final String fieldName;

  private final int compression;

  @Nonnull
  private final byte cacheTypeId;

  public static final String TYPE_NAME = "buildTDigestSketch";

  @JsonCreator
  public TDigestBuildSketchAggregatorFactory(
      @JsonProperty("name") final String name,
      @JsonProperty("fieldName") final String fieldName,
      @Nullable @JsonProperty("compression") final Integer compression
  )
  {
    this(name, fieldName, compression, AggregatorUtil.TDIGEST_BUILD_SKETCH_CACHE_TYPE_ID);
  }

  TDigestBuildSketchAggregatorFactory(
      final String name,
      final String fieldName,
      @Nullable final Integer compression,
      final byte cacheTypeId
  )
  {
    this.name = Objects.requireNonNull(name, "Must have a valid, non-null aggregator name");
    this.fieldName = Objects.requireNonNull(fieldName, "Parameter fieldName must be specified");
    this.compression = compression == null ? DEFAULT_COMPRESSION : compression;
    this.cacheTypeId = cacheTypeId;
  }


  @Override
  public byte[] getCacheKey()
  {
    return new CacheKeyBuilder(
        cacheTypeId
    ).appendString(fieldName).appendInt(compression).build();
  }


  @Override
  public Aggregator factorize(ColumnSelectorFactory metricFactory)
  {
    ColumnCapabilities cap = metricFactory.getColumnCapabilities(fieldName);
    if (cap == null || ValueType.isNumeric(cap.getType())) {
      final ColumnValueSelector<Double> selector = metricFactory.makeColumnValueSelector(fieldName);
      return new TDigestBuildSketchAggregator(selector, compression);
    } else {
      final ColumnValueSelector<MergingDigest> selector = metricFactory.makeColumnValueSelector(fieldName);
      return new TDigestMergeSketchAggregator(selector, compression);
    }
  }

  @Override
  public BufferAggregator factorizeBuffered(ColumnSelectorFactory metricFactory)
  {
    ColumnCapabilities cap = metricFactory.getColumnCapabilities(fieldName);
    if (cap == null || ValueType.isNumeric(cap.getType())) {
      final ColumnValueSelector<Double> selector = metricFactory.makeColumnValueSelector(fieldName);
      return new TDigestBuildSketchBufferAggregator(selector, compression);
    } else {
      final ColumnValueSelector<MergingDigest> selector = metricFactory.makeColumnValueSelector(fieldName);
      return new TDigestMergeSketchBufferAggregator(selector, compression);
    }
  }

  public static final Comparator<TDigest> COMPARATOR = Comparator.nullsFirst(
      Comparator.comparingLong(a -> a.size())
  );

  @Override
  public Comparator getComparator()
  {
    return COMPARATOR;
  }

  @Override
  public Object combine(@Nullable Object lhs, @Nullable Object rhs)
  {
    if (lhs == null) {
      return rhs;
    }
    if (rhs == null) {
      return lhs;
    }
    TDigest union = (TDigest) lhs;
    union.add((TDigest) rhs);
    return union;
  }

  @Override
  public AggregatorFactory getCombiningFactory()
  {
    return new TDigestMergeSketchAggregatorFactory(name, name, compression);
  }

  @Override
  public AggregatorFactory getMergingFactory(AggregatorFactory other) throws AggregatorFactoryNotMergeableException
  {
    if (other.getName().equals(this.getName()) && this.getClass() == other.getClass()) {
      return getCombiningFactory();
    } else {
      throw new AggregatorFactoryNotMergeableException(this, other);
    }
  }

  @Override
  public List<AggregatorFactory> getRequiredColumns()
  {
    return Collections.singletonList(
        new TDigestBuildSketchAggregatorFactory(
            fieldName,
            fieldName,
            compression
        )
    );
  }

  @Override
  public Object deserialize(Object serializedSketch)
  {
    return TDigestSketchOperations.deserialize(serializedSketch);
  }

  @Override
  public Object finalizeComputation(Object object)
  {
    return object;
  }

  @Override
  @JsonProperty
  public String getName()
  {
    return name;
  }

  @JsonProperty
  public String getFieldName()
  {
    return fieldName;
  }

  @JsonProperty
  public int getCompression()
  {
    return compression;
  }

  @Override
  public List<String> requiredFields()
  {
    return Collections.singletonList(fieldName);
  }

  @Override
  public String getTypeName()
  {
    return TYPE_NAME;
  }

  @Override
  public int getMaxIntermediateSize()
  {
    //TODO: need support in tdigest to return appropriate value here.
    //For now returning a sensible value
    return 100000;
  }

  @Override
  public boolean equals(final Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }
    final TDigestBuildSketchAggregatorFactory that = (TDigestBuildSketchAggregatorFactory) o;

    return Objects.equals(name, that.name) &&
           Objects.equals(fieldName, that.fieldName) &&
           compression == that.compression;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(name, fieldName, compression);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "{"
           + "name=" + name
           + ", fieldName=" + fieldName
           + ", compression=" + compression
           + "}";
  }

}

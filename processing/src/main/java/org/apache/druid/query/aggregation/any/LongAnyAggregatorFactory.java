package org.apache.druid.query.aggregation.any;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import org.apache.druid.collections.SerializablePair;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.UOE;
import org.apache.druid.query.aggregation.AggregateCombiner;
import org.apache.druid.query.aggregation.Aggregator;
import org.apache.druid.query.aggregation.AggregatorFactory;
import org.apache.druid.query.aggregation.AggregatorUtil;
import org.apache.druid.query.aggregation.BufferAggregator;
import org.apache.druid.query.aggregation.NullableNumericAggregatorFactory;
import org.apache.druid.query.aggregation.first.LongFirstAggregatorFactory;
import org.apache.druid.query.aggregation.last.LongLastAggregator;
import org.apache.druid.query.aggregation.last.LongLastAggregatorFactory;
import org.apache.druid.query.aggregation.last.LongLastBufferAggregator;
import org.apache.druid.query.monomorphicprocessing.RuntimeShapeInspector;
import org.apache.druid.segment.ColumnSelectorFactory;
import org.apache.druid.segment.ColumnValueSelector;
import org.apache.druid.segment.column.ColumnHolder;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LongAnyAggregatorFactory extends NullableNumericAggregatorFactory<ColumnValueSelector>
{
  private static final Comparator<Number> VALUE_COMPARATOR = Comparator.nullsFirst(
      Comparator.comparingLong(Number::longValue)
  );

  private final String fieldName;
  private final String name;

  @JsonCreator
  public LongAnyAggregatorFactory(
      @JsonProperty("name") String name,
      @JsonProperty("fieldName") final String fieldName
  )
  {
    Preconditions.checkNotNull(name, "Must have a valid, non-null aggregator name");
    Preconditions.checkNotNull(fieldName, "Must have a valid, non-null fieldName");
    this.name = name;
    this.fieldName = fieldName;
  }

  @Override
  protected ColumnValueSelector selector(ColumnSelectorFactory metricFactory)
  {
    return metricFactory.makeColumnValueSelector(fieldName);
  }

  @Override
  protected Aggregator factorize(ColumnSelectorFactory metricFactory, ColumnValueSelector selector)
  {
    return new LongAnyAggregator(selector);
  }

  @Override
  protected BufferAggregator factorizeBuffered(ColumnSelectorFactory metricFactory, ColumnValueSelector selector)
  {
    return new LongAnyBufferAggregator(selector);
  }

  @Override
  public Comparator getComparator()
  {
    return LongAnyAggregatorFactory.VALUE_COMPARATOR;
  }

  @Override
  @Nullable
  public Object combine(@Nullable Object lhs, @Nullable Object rhs)
  {
    if (lhs != null) {
      return lhs;
    } else {
      return rhs;
    }
  }

  @Override
  public AggregatorFactory getCombiningFactory()
  {
    return new LongAnyAggregatorFactory(name, name);
  }

  @Override
  public List<AggregatorFactory> getRequiredColumns()
  {
    return Collections.singletonList(new LongAnyAggregatorFactory(fieldName, fieldName));
  }

  @Override
  public Object deserialize(Object object)
  {
    return object;
  }

  @Override
  @Nullable
  public Object finalizeComputation(@Nullable Object object)
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

  @Override
  public List<String> requiredFields()
  {
    return Arrays.asList(fieldName);
  }

  @Override
  public byte[] getCacheKey()
  {
    byte[] fieldNameBytes = StringUtils.toUtf8(fieldName);

    return ByteBuffer.allocate(1 + fieldNameBytes.length)
                     .put(AggregatorUtil.LONG_ANY_CACHE_TYPE_ID)
                     .put(fieldNameBytes)
                     .array();
  }

  @Override
  public String getTypeName()
  {
    return "long";
  }

  @Override
  public int getMaxIntermediateSize()
  {
    return Long.BYTES;
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

    LongAnyAggregatorFactory that = (LongAnyAggregatorFactory) o;

    return name.equals(that.name) && fieldName.equals(that.fieldName);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(name, fieldName);
  }

  @Override
  public String toString()
  {
    return "LongAnyAggregatorFactory{" +
           "name='" + name + '\'' +
           ", fieldName='" + fieldName + '\'' +
           '}';
  }
}

package org.apache.druid.query.aggregation.any;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import org.apache.druid.collections.SerializablePair;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.query.aggregation.Aggregator;
import org.apache.druid.query.aggregation.AggregatorFactory;
import org.apache.druid.query.aggregation.AggregatorUtil;
import org.apache.druid.query.aggregation.BufferAggregator;
import org.apache.druid.query.aggregation.NullableNumericAggregatorFactory;
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
import java.util.Objects;

public class DoubleAnyAggregatorFactory extends NullableNumericAggregatorFactory<ColumnValueSelector>
{
  private static final Comparator<Number> VALUE_COMPARATOR = Comparator.nullsFirst(
      Comparator.comparingDouble(Number::doubleValue)
  );

  private final String fieldName;
  private final String name;
  private final boolean storeDoubleAsFloat;

  @JsonCreator
  public DoubleAnyAggregatorFactory(
      @JsonProperty("name") String name,
      @JsonProperty("fieldName") final String fieldName
  )
  {
    Preconditions.checkNotNull(name, "Must have a valid, non-null aggregator name");
    Preconditions.checkNotNull(fieldName, "Must have a valid, non-null fieldName");
    this.name = name;
    this.fieldName = fieldName;
    this.storeDoubleAsFloat = ColumnHolder.storeDoubleAsFloat();
  }

  @Override
  protected ColumnValueSelector selector(ColumnSelectorFactory metricFactory)
  {
    return metricFactory.makeColumnValueSelector(fieldName);
  }

  @Override
  protected Aggregator factorize(ColumnSelectorFactory metricFactory, ColumnValueSelector selector)
  {
    return new DoubleAnyAggregator(selector);
  }

  @Override
  protected BufferAggregator factorizeBuffered(ColumnSelectorFactory metricFactory, ColumnValueSelector selector)
  {
    return new DoubleAnyBufferAggregator(selector);
  }

  @Override
  public Comparator getComparator()
  {
    return DoubleAnyAggregatorFactory.VALUE_COMPARATOR;
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
    return new DoubleAnyAggregatorFactory(name, name);
  }

  @Override
  public List<AggregatorFactory> getRequiredColumns()
  {
    return Collections.singletonList(new DoubleAnyAggregatorFactory(fieldName, fieldName));
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
                     .put(AggregatorUtil.DOUBLE_ANY_CACHE_TYPE_ID)
                     .put(fieldNameBytes)
                     .array();
  }

  @Override
  public String getTypeName()
  {
    if (storeDoubleAsFloat) {
      return "float";
    }
    return "double";
  }

  @Override
  public int getMaxIntermediateSize()
  {
    return Double.BYTES;
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

    DoubleAnyAggregatorFactory that = (DoubleAnyAggregatorFactory) o;

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
    return "DoubleAnyAggregatorFactory{" +
           "name='" + name + '\'' +
           ", fieldName='" + fieldName + '\'' +
           '}';
  }
}

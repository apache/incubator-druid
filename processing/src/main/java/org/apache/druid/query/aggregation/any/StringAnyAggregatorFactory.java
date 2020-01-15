package org.apache.druid.query.aggregation.any;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.query.aggregation.Aggregator;
import org.apache.druid.query.aggregation.AggregatorFactory;
import org.apache.druid.query.aggregation.AggregatorUtil;
import org.apache.druid.query.aggregation.BufferAggregator;
import org.apache.druid.query.aggregation.first.StringFirstAggregatorFactory;
import org.apache.druid.query.cache.CacheKeyBuilder;
import org.apache.druid.segment.ColumnSelectorFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class StringAnyAggregatorFactory extends AggregatorFactory
{
  private static final Comparator<String> VALUE_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());

  private final String fieldName;
  private final String name;
  protected final int maxStringBytes;

  @JsonCreator
  public StringAnyAggregatorFactory(
      @JsonProperty("name") String name,
      @JsonProperty("fieldName") final String fieldName,
      @JsonProperty("maxStringBytes") Integer maxStringBytes
  )
  {
    Preconditions.checkNotNull(name, "Must have a valid, non-null aggregator name");
    Preconditions.checkNotNull(fieldName, "Must have a valid, non-null fieldName");
    if (maxStringBytes != null && maxStringBytes < 0) {
      throw new IAE("maxStringBytes must be greater than 0");
    }
    this.name = name;
    this.fieldName = fieldName;
    this.maxStringBytes = maxStringBytes == null
                          ? StringFirstAggregatorFactory.DEFAULT_MAX_STRING_SIZE
                          : maxStringBytes;
  }

  @Override
  public Aggregator factorize(ColumnSelectorFactory metricFactory)
  {
    return new StringAnyAggregator(metricFactory.makeColumnValueSelector(fieldName), maxStringBytes);
  }

  @Override
  public BufferAggregator factorizeBuffered(ColumnSelectorFactory metricFactory)
  {
    return new StringAnyBufferAggregator(metricFactory.makeColumnValueSelector(fieldName), maxStringBytes);
  }

  @Override
  public Comparator getComparator()
  {
    return StringAnyAggregatorFactory.VALUE_COMPARATOR;
  }

  @Override
  public Object combine(Object lhs, Object rhs)
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
    return new StringAnyAggregatorFactory(name, name, maxStringBytes);
  }

  @Override
  public List<AggregatorFactory> getRequiredColumns()
  {
    return Collections.singletonList(new StringAnyAggregatorFactory(fieldName, fieldName, maxStringBytes));
  }

  @Override
  public Object deserialize(Object object)
  {
    return object;
  }

  @Override
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

  @JsonProperty
  public Integer getMaxStringBytes()
  {
    return maxStringBytes;
  }

  @Override
  public List<String> requiredFields()
  {
    return Arrays.asList(fieldName);
  }

  @Override
  public byte[] getCacheKey()
  {
    return new CacheKeyBuilder(AggregatorUtil.STRING_ANY_CACHE_TYPE_ID)
        .appendString(fieldName)
        .appendInt(maxStringBytes)
        .build();
  }

  @Override
  public String getTypeName()
  {
    return "string";
  }

  @Override
  public int getMaxIntermediateSize()
  {
    return maxStringBytes;
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
    StringAnyAggregatorFactory that = (StringAnyAggregatorFactory) o;
    return maxStringBytes == that.maxStringBytes &&
           Objects.equals(fieldName, that.fieldName) &&
           Objects.equals(name, that.name);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(fieldName, name, maxStringBytes);
  }

  @Override
  public String toString()
  {
    return "StringAnyAggregatorFactory{" +
           "fieldName='" + fieldName + '\'' +
           ", name='" + name + '\'' +
           ", maxStringBytes=" + maxStringBytes +
           '}';
  }
}

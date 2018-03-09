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

package io.druid.query.aggregation.datasketches.tuple;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.druid.query.aggregation.PostAggregator;

/**
 * Base class for post aggs taking multiple sketches as input
 */
public abstract class ArrayOfDoublesSketchMultiPostAggregator extends ArrayOfDoublesSketchPostAggregator
{

  private final List<PostAggregator> fields;

  @JsonCreator
  public ArrayOfDoublesSketchMultiPostAggregator(final String name, final List<PostAggregator> fields)
  {
    super(name);
    this.fields = fields;
  }

  @Override
  public Set<String> getDependentFields()
  {
    final Set<String> dependentFields = super.getDependentFields();
    for (final PostAggregator field : fields) {
      dependentFields.addAll(field.getDependentFields());
    }
    return dependentFields;
  }

  @JsonProperty
  public List<PostAggregator> getFields()
  {
    return fields;
  }

  @Override
  public String toString()
  {
    return this.getClass().getSimpleName() + "{"
        + "name='" + getName() + '\''
        + ", fields=" + fields
        + "}";
  }

  @Override
  public boolean equals(final Object o)
  {
    if (!super.equals(o)) {
      return false;
    }
    if (!(o instanceof ArrayOfDoublesSketchMultiPostAggregator)) {
      return false;
    }
    final ArrayOfDoublesSketchMultiPostAggregator that = (ArrayOfDoublesSketchMultiPostAggregator) o;
    return fields.equals(that.fields);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(super.hashCode(), fields);
  }

}

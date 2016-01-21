/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Metamarkets licenses this file
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
package io.druid.data.input.avro;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.StringType;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.metamx.common.parsers.ParseException;

public class GenericRecordAsMap implements Map<String, Object>
{
  private final GenericRecord record;
  private final boolean fromPigAvroStorage;
  private final Map<String, List<PathComponent>> mappingCache;
  private final boolean useString;

  private static final Function<Object, String> PIG_AVRO_STORAGE_ARRAY_TO_STRING_INCLUDING_NULL = new Function<Object, String>()
  {
    @Nullable
    @Override
    public String apply(Object input)
    {
      return String.valueOf(((GenericRecord) input).get(0));
    }
  };

  public GenericRecordAsMap(GenericRecord record, 
      boolean fromPigAvroStorage, 
      Map<String, List<PathComponent>> mappingCache
  )
  {
    this.record = record;
    this.fromPigAvroStorage = fromPigAvroStorage;
    this.mappingCache = mappingCache;
    
    // check the schema to see the type of string to use for map keys
    Schema schema = record.getSchema();
    String prop = schema.getProp("avro.java.string");
    boolean useString = false;
    try {
      // The following succeeds if a valid value has been passed
      if (prop != null && StringType.valueOf(prop) == StringType.String) {
        useString = true;
      }
    } catch (IllegalArgumentException e) {
      // either no value was present in the schema or an invalid
      // value was present. Fall back to the default.
    }
    this.useString = useString;
  }

  @Override
  public int size()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsKey(Object key)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsValue(Object value)
  {
    throw new UnsupportedOperationException();
  }

  /**
   * When used in MapBasedRow, field in GenericRecord will be interpret as follows:
   * <ul>
   * <li>avro schema type -> druid dimension:</li>
   * <ul>
   * <li>null, boolean, int, long, float, double, string, Records, Enums, Maps, Fixed -> String, using
   * String.valueOf</li>
   * <li>bytes -> Arrays.toString()</li>
   * <li>Arrays -> List&lt;String&gt;, using Lists.transform(&lt;List&gt;dimValue, TO_STRING_INCLUDING_NULL)</li>
   * </ul>
   * <li>avro schema type -> druid metric:</li>
   * <ul>
   * <li>null -> 0F/0L</li>
   * <li>int, long, float, double -> Float/Long, using Number.floatValue()/Number.longValue()</li>
   * <li>string -> Float/Long, using Float.valueOf()/Long.valueOf()</li>
   * <li>boolean, bytes, Arrays, Records, Enums, Maps, Fixed -> ParseException</li>
   * </ul>
   * </ul>
   * 
   * <p>
   * If mappingCache is set and has an entry for the key, the key is interpreted as a complex path through the 
   * avro schema, allowing schemas with nested objects, lists, and maps to be traversed. If a field/entry/mapvalue 
   * isn't present, null is returned.
   */
  @Override
  public Object get(Object key)
  {
    Object field;
    List<PathComponent> path;

    // Lookup whether there is a mapping for this key
    path = mappingCache.get((String) key);
    if (path == null) {
      path = AvroSchemaMappingHelper.getSimpleFieldAccessor((String) key);
    } 

    field = record;

    for (PathComponent pathComp : path) {
      if (field == null) {
        return null;
      }

      switch (pathComp.getType()) {
      case ARRAY: {
        Integer index = pathComp.getArrayIdx();
        if (field instanceof List<?>) {
          List<?> array = (List<?>) field;
          if (index >= array.size()) {
            // past end of the array - just return null
            return null;
          } else {
            field = array.get(index);
            if (fromPigAvroStorage && field instanceof GenericRecord) {
              field = ((GenericRecord) field).get(0);
            }
          }
        } else {
          // type mismatch = expected an array but was something else
          throw new ParseException("Type mismatch processing array entry %d for %s. Expected List but found %s", 
              pathComp.getArrayIdx(), key, field.getClass().getSimpleName());
        }
        break;
      }
      case MAP:
        if (field instanceof Map<?, ?>) {
          // avro Maps are indexed by Utf8, not by String
          Object mapKey = useString ? new String(pathComp.getMapKey()) :
                new Utf8(pathComp.getMapKey());
          field = ((Map<?, ?>) field).get(mapKey);
          if (fromPigAvroStorage && field instanceof GenericRecord) {
            field = ((GenericRecord) field).get(0);
          }
        } else {
          // type mismatch - expected a map but was something else
          throw new ParseException("Type mismatch processing map entry %s for %s. Expected Map but found %s",
              pathComp.getMapKey(), key, field.getClass().getSimpleName());
        }
        break;
      case FIELD:
        if (field instanceof GenericRecord) {
          GenericRecord r = (GenericRecord) field;
          Field f = r.getSchema().getField(pathComp.getFieldName());
          if (f != null) {
            field = r.get(f.pos());
          } else {
            field = null;
          }
        } else {
          // type mismatch - expected a record but was something else
          throw new ParseException("Type mismatch processing field entry %s for %s. Expected GenericRecord but found %s",
              pathComp.getFieldName(), key, field.getClass().getSimpleName());
        }
        break;
      }
    }

    if (fromPigAvroStorage && field instanceof GenericData.Array<?>) {
      return Lists.transform((List<?>) field, PIG_AVRO_STORAGE_ARRAY_TO_STRING_INCLUDING_NULL);
    }
    if (field instanceof ByteBuffer) {
      return Arrays.toString(((ByteBuffer) field).array());
    }
    return field;
  }

  @Override
  public Object put(String key, Object value)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object remove(Object key)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map<? extends String, ?> m)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> keySet()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<Object> values()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Entry<String, Object>> entrySet()
  {
    throw new UnsupportedOperationException();
  }
}

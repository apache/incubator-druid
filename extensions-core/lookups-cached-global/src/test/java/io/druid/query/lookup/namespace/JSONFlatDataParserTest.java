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

package io.druid.query.lookup.namespace;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import io.druid.data.input.MapPopulator;
import io.druid.data.input.MultiMapsPopulator;
import io.druid.jackson.DefaultObjectMapper;
import org.apache.commons.collections.keyvalue.MultiKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JSONFlatDataParserTest
{
  private static final ObjectMapper MAPPER = new DefaultObjectMapper();
  private static final String KEY1 = "foo1";
  private static final String KEY2 = "foo2";
  private static final String VAL1 = "bar";
  private static final String VAL2 = "baz";
  private static final String OTHERVAL1 = "3";
  private static final String OTHERVAL2 = null;
  private static final String CANBEEMPTY1 = "";
  private static final String CANBEEMPTY2 = "notEmpty";
  private static final List<Map<String, Object>> MAPPINGS = ImmutableList.<Map<String, Object>>of(
      ImmutableMap.<String, Object>of("key", "foo1", "val", "bar", "otherVal", 3, "canBeEmpty", ""),
      ImmutableMap.<String, Object>of("key", "foo2", "val", "baz", "canBeEmpty", "notEmpty")
  );
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  private File tmpFile;

  @Before
  public void setUp() throws Exception
  {
    tmpFile = temporaryFolder.newFile("lookup.json");
    final CharSink sink = Files.asByteSink(tmpFile).asCharSink(Charsets.UTF_8);
    sink.writeLines(
        Iterables.transform(
            MAPPINGS,
            new Function<Map<String, Object>, CharSequence>()
            {
              @Override
              public CharSequence apply(Map<String, Object> input)
              {
                try {
                  return MAPPER.writeValueAsString(input);
                }
                catch (Exception e) {
                  throw Throwables.propagate(e);
                }
              }
            }
        )
    );
  }

  @Test
  public void testSimpleParse() throws Exception
  {
    final URIExtractionNamespace extractionNamespace = new URIExtractionNamespace(
        new URI("dummy"),
        null,
        null,
        new URIExtractionNamespace.JSONFlatDataParser(
            MAPPER
        ),
        ImmutableList.of(new KeyValueMap(KeyValueMap.DEFAULT_MAPNAME, "key", "val")),
        null,
        null
    );
    final ConcurrentMap<MultiKey, Map<String, String>> map = Maps.newConcurrentMap();
    new MultiMapsPopulator<>(extractionNamespace.getParser(extractionNamespace.getNamespaceParseSpec().getParser(), "test"),
        new Function<MultiKey, Map<String, String>>()
        {
          @Override
          public Map<String, String> apply(MultiKey key)
          {
            Map<String, String> inner = map.get(key);
            if (inner == null) {
              map.putIfAbsent(key, Maps.<String, String>newHashMap());
              inner = map.get(key);
            }
            return inner;
          }
        })
        .populate(Files.asByteSource(tmpFile), map);
    final Map<String, String> resultMap = map.get(new MultiKey("test", KeyValueMap.DEFAULT_MAPNAME));
    Assert.assertEquals(VAL1, resultMap.get(KEY1));
    Assert.assertEquals(VAL2, resultMap.get(KEY2));
  }

  @Test
  public void testParseWithNullValues() throws Exception
  {
    final URIExtractionNamespace extractionNamespace = new URIExtractionNamespace(
        new URI("dummy"),
        null,
        null,
        new URIExtractionNamespace.JSONFlatDataParser(
            MAPPER
        ),
        ImmutableList.of(new KeyValueMap(KeyValueMap.DEFAULT_MAPNAME, "key", "otherVal")),
        null,
        null
    );
    final ConcurrentMap<MultiKey, Map<String, String>> map = Maps.newConcurrentMap();
    new MultiMapsPopulator<>(extractionNamespace.getParser(extractionNamespace.getNamespaceParseSpec().getParser(), "test2"),
        new Function<MultiKey, Map<String, String>>()
        {
          @Override
          public Map<String, String> apply(MultiKey key)
          {
            Map<String, String> inner = map.get(key);
            if (inner == null) {
              map.putIfAbsent(key, Maps.<String, String>newHashMap());
              inner = map.get(key);
            }
            return inner;
          }
        })
        .populate(Files.asByteSource(tmpFile), map);
    final Map<String, String> resultMap = map.get(new MultiKey("test2", KeyValueMap.DEFAULT_MAPNAME));
    Assert.assertEquals(OTHERVAL1, resultMap.get(KEY1));
    Assert.assertEquals(OTHERVAL2, resultMap.get(KEY2));
  }

  @Test
  public void testParseWithEmptyValues() throws Exception
  {
    final URIExtractionNamespace extractionNamespace = new URIExtractionNamespace(
        new URI("dummy"),
        null,
        null,
        new URIExtractionNamespace.JSONFlatDataParser(
            MAPPER
        ),
        ImmutableList.of(new KeyValueMap(KeyValueMap.DEFAULT_MAPNAME, "key", "canBeEmpty")),
        null,
        null
    );
    final ConcurrentMap<MultiKey, Map<String, String>> map = Maps.newConcurrentMap();
    new MultiMapsPopulator<>(extractionNamespace.getParser(extractionNamespace.getNamespaceParseSpec().getParser(), "test3"),
        new Function<MultiKey, Map<String, String>>()
        {
          @Override
          public Map<String, String> apply(MultiKey key)
          {
            Map<String, String> inner = map.get(key);
            if (inner == null) {
              map.putIfAbsent(key, Maps.<String, String>newHashMap());
              inner = map.get(key);
            }
            return inner;
          }
        })
        .populate(Files.asByteSource(tmpFile), map);
    final Map<String, String> resultMap = map.get(new MultiKey("test3", KeyValueMap.DEFAULT_MAPNAME));
    Assert.assertEquals(CANBEEMPTY1, resultMap.get(KEY1));
    Assert.assertEquals(CANBEEMPTY2, resultMap.get(KEY2));
  }

  @Test
  public void testNonExistingParse() throws Exception
  {
    final URIExtractionNamespace.JSONFlatDataParser parser = new URIExtractionNamespace.JSONFlatDataParser(
        MAPPER
    );
    final Map<String, Object> map = new HashMap<>();
    final Map<String, Object> resultMap = new HashMap<>();
    new MapPopulator<>(parser.getParser()).populate(Files.asByteSource(tmpFile), map);
    resultMap.put((String)map.get("keyWHOOPS"), map.get("val"));
    Assert.assertEquals(null, resultMap.get(KEY1));
  }
}

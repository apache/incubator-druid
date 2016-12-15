/*
 *
 *  Licensed to Metamarkets Group Inc. (Metamarkets) under one
 *  or more contributor license agreements. See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership. Metamarkets licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 * /
 */

package io.druid.server.lookup.jdbc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.druid.jackson.DefaultObjectMapper;
import io.druid.metadata.MetadataStorageConnectorConfig;
import io.druid.metadata.TestDerbyConnector;
import io.druid.server.lookup.DataFetcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.skife.jdbi.v2.Handle;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@RunWith(Parameterized.class)
public class JdbcDataFetcherTest
{
  @Parameterized.Parameters
  public static Collection<Object[]> parameters()
  {
    return Arrays.asList(new Object[][]{
        {prefetchRangeProvider, "foo", prefetchRangeExpected}, {prefetchPointsProvider, "foo", prefetchPointsExpected}
    });
  }

  public JdbcDataFetcherTest(PrefetchKeyProvider prefetchKeyProvider, String prefetchKey, Map<String, String> prefetchExpected) {
    this.prefetchKeyProvider = prefetchKeyProvider;
    this.prefetchKey = prefetchKey;
    this.prefetchExpected = prefetchExpected;
  }

  @Rule
  public final TestDerbyConnector.DerbyConnectorRule derbyConnectorRule = new TestDerbyConnector.DerbyConnectorRule();
  Handle handle;
  
  private JdbcDataFetcher jdbcDataFetcher;
  private static final String tableName = "tableName";
  private static final String keyColumn = "keyColumn";
  private static final String valueColumn = "valueColumn";

  private static final Map<String, String> lookupMap = ImmutableMap.of(
      "foo", "bar",
      "bad", "bar",
      "how about that", "foo",
      "empty string", ""
  );

  private final PrefetchKeyProvider prefetchKeyProvider;
  private final String prefetchKey;
  private final Map<String, String> prefetchExpected;

  private static final PrefetchKeyProvider prefetchRangeProvider =
      new PrefetchKeyRangeProvider(ImmutableList.of("A", "Z", "a", "e", "z"));
  private static final Map<String, String> prefetchRangeExpected = ImmutableMap.of(
      "foo", "bar",
      "how about that", "foo",
      "empty string", ""
  );

  private static final PrefetchKeyProvider prefetchPointsProvider = new PrefetchKeyProvider()
  {
    @Override
    public PrefetchQueryProvider getQueryProvider()
    {
      return new PrefetchPointsQueryProvider();
    }

    @Override
    public String[] get(String key)
    {
      return new String[] {"foo", "bad"};
    }
  };

  private static final Map<String, String> prefetchPointsExpected = ImmutableMap.of(
      "foo", "bar",
      "bad", "bar"
  );

  @Before
  public void setUp() throws InterruptedException
  {
    jdbcDataFetcher = new JdbcDataFetcher(derbyConnectorRule.getMetadataConnectorConfig(), "tableName", "keyColumn", "valueColumn",
                                          100, prefetchKeyProvider);

    handle = derbyConnectorRule.getConnector().getDBI().open();
    Assert.assertEquals(
        0,
        handle.createStatement(
            String.format(
                "CREATE TABLE %s (%s VARCHAR(64), %s VARCHAR(64))",
                tableName,
                keyColumn,
                valueColumn
            )
        ).setQueryTimeout(1).execute()
    );
    handle.createStatement(String.format("TRUNCATE TABLE %s", tableName)).setQueryTimeout(1).execute();

    for (Map.Entry<String, String> entry : lookupMap.entrySet()) {
      insertValues(entry.getKey(), entry.getValue(), handle);
    }
    handle.commit();
  }

  @After
  public void tearDown()
  {
    handle.createStatement("DROP TABLE " + tableName).setQueryTimeout(1).execute();
    handle.close();
  }

  @Test
  public void testFetch() throws InterruptedException
  {
    Assert.assertEquals("null check", null, jdbcDataFetcher.fetch("baz"));
    assertMapLookup(lookupMap, jdbcDataFetcher);
  }

  @Test
  public void testFetchAll()
  {
    ImmutableMap.Builder<String,String> mapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, String> entry: jdbcDataFetcher.fetchAll()
         ) {
      mapBuilder.put(entry.getKey(), entry.getValue());
    }
    Assert.assertEquals("maps should match", lookupMap, mapBuilder.build());
  }

  @Test
  public void testFetchKeys()
  {
    ImmutableMap.Builder<String,String> mapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, String> entry: jdbcDataFetcher.fetch(lookupMap.keySet())
        ) {
      mapBuilder.put(entry.getKey(), entry.getValue());
    }

    Assert.assertEquals(lookupMap, mapBuilder.build());
  }

  @Test
  public void testPrefetch()
  {
    Assert.assertEquals(prefetchExpected, jdbcDataFetcher.prefetch(prefetchKey));
    assertMapLookup(lookupMap, jdbcDataFetcher);
  }

  @Test
  public void testReverseFetch() throws InterruptedException
  {
    Assert.assertEquals(
        "reverse lookup should match",
        Sets.newHashSet("foo", "bad"),
        Sets.newHashSet(jdbcDataFetcher.reverseFetchKeys("bar"))
    );
    Assert.assertEquals(
        "reverse lookup should match",
        Sets.newHashSet("how about that"),
        Sets.newHashSet(jdbcDataFetcher.reverseFetchKeys("foo"))
    );
    Assert.assertEquals(
        "reverse lookup should match",
        Sets.newHashSet("empty string"),
        Sets.newHashSet(jdbcDataFetcher.reverseFetchKeys(""))
    );
    Assert.assertEquals(
        "reverse lookup of none existing value should be empty list",
        Collections.EMPTY_LIST,
        jdbcDataFetcher.reverseFetchKeys("does't exist")
    );
  }

  @Test
  public void testSerDesr() throws IOException
  {
    JdbcDataFetcher jdbcDataFetcher = new JdbcDataFetcher(new MetadataStorageConnectorConfig(), "table", "keyColumn", "ValueColumn",
                                                          100, prefetchRangeProvider);
    DefaultObjectMapper mapper = new DefaultObjectMapper();
    String jdbcDataFetcherSer = mapper.writeValueAsString(jdbcDataFetcher);
    Assert.assertEquals(jdbcDataFetcher, mapper.reader(DataFetcher.class).readValue(jdbcDataFetcherSer));
  }

  private void assertMapLookup(Map<String, String> map, DataFetcher dataFetcher)
  {
    for (Map.Entry<String, String> entry : map.entrySet()) {
      String key = entry.getKey();
      String val = entry.getValue();
      Assert.assertEquals("non-null check", val, dataFetcher.fetch(key));
    }
  }

  private void insertValues(final String key, final String val, Handle handle)
  {
    final String query;
    handle.createStatement(
        String.format("DELETE FROM %s WHERE %s='%s'", tableName, keyColumn, key)
    ).setQueryTimeout(1).execute();
    query = String.format(
        "INSERT INTO %s (%s, %s) VALUES ('%s', '%s')",
        tableName,
        keyColumn, valueColumn,
        key, val
    );
    Assert.assertEquals(1, handle.createStatement(query).setQueryTimeout(1).execute());
    handle.commit();
  }
}

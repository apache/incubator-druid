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

package io.druid.query.search;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.metamx.common.guava.Sequence;
import com.metamx.common.guava.Sequences;
import com.metamx.common.logger.Logger;
import io.druid.query.Druids;
import io.druid.query.Query;
import io.druid.query.QueryRunner;
import io.druid.query.QueryRunnerTestHelper;
import io.druid.query.Result;
import io.druid.query.dimension.ExtractionDimensionSpec;
import io.druid.query.extraction.LookupExtractionFn;
import io.druid.query.extraction.MapLookupExtractor;
import io.druid.query.filter.AndDimFilter;
import io.druid.query.filter.DimFilter;
import io.druid.query.filter.ExtractionDimFilter;
import io.druid.query.filter.RegexDimFilter;
import io.druid.query.filter.SelectorDimFilter;
import io.druid.query.search.search.FragmentSearchQuerySpec;
import io.druid.query.search.search.SearchHit;
import io.druid.query.search.search.SearchQuery;
import io.druid.query.search.search.SearchQueryConfig;
import io.druid.query.search.search.StrlenSearchSortSpec;
import io.druid.query.spec.MultipleIntervalSegmentSpec;
import io.druid.segment.TestHelper;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 */
@RunWith(Parameterized.class)
public class SearchQueryRunnerTest
{
  private static final Logger LOG = new Logger(SearchQueryRunnerTest.class);
  private static final SearchQueryQueryToolChest toolChest = new SearchQueryQueryToolChest(
      new SearchQueryConfig(),
      QueryRunnerTestHelper.NoopIntervalChunkingQueryRunnerDecorator()
  );

  @Parameterized.Parameters
  public static Iterable<Object[]> constructorFeeder() throws IOException
  {
    return QueryRunnerTestHelper.transformToConstructionFeeder(
        QueryRunnerTestHelper.makeQueryRunners(
            new SearchQueryRunnerFactory(
                toolChest,
                QueryRunnerTestHelper.NOOP_QUERYWATCHER
            )
        )
    );
  }

  private final QueryRunner runner;

  public SearchQueryRunnerTest(
      QueryRunner runner
  )
  {
    this.runner = runner;
  }

  @Test
  public void testSearchHitSerDe() throws Exception
  {
    for (SearchHit hit : Arrays.asList(new SearchHit("dim1", "val1"), new SearchHit("dim2", "val2", 3))) {
      SearchHit read = TestHelper.JSON_MAPPER.readValue(
          TestHelper.JSON_MAPPER.writeValueAsString(hit),
          SearchHit.class
      );
      Assert.assertEquals(hit, read);
      if (hit.getCount() == null) {
        Assert.assertNull(read.getCount());
      } else {
        Assert.assertEquals(hit.getCount(), read.getCount());
      }
    }
  }

  @Test
  public void testSearch()
  {
    SearchQuery searchQuery = Druids.newSearchQueryBuilder()
                                    .dataSource(QueryRunnerTestHelper.dataSource)
                                    .granularity(QueryRunnerTestHelper.allGran)
                                    .intervals(QueryRunnerTestHelper.fullOnInterval)
                                    .query("a")
                                    .build();

    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "automotive", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "mezzanine", 279));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "travel", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "health", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "entertainment", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.marketDimension, "total_market", 186));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementishDimension, "a", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.partialNullDimension, "value", 186));

    checkSearchQuery(searchQuery, expectedHits);
  }

  @Test
  public void testSearchWithCardinality()
  {
    final SearchQuery searchQuery = Druids.newSearchQueryBuilder()
                                          .dataSource(QueryRunnerTestHelper.dataSource)
                                          .granularity(QueryRunnerTestHelper.allGran)
                                          .intervals(QueryRunnerTestHelper.fullOnInterval)
                                          .query("a")
                                          .build();

    // double the value
    QueryRunner mergedRunner = toolChest.mergeResults(
        new QueryRunner<Result<SearchResultValue>>()
        {
          @Override
          public Sequence<Result<SearchResultValue>> run(
              Query<Result<SearchResultValue>> query, Map<String, Object> responseContext
          )
          {
            final Query<Result<SearchResultValue>> query1 = searchQuery.withQuerySegmentSpec(
                new MultipleIntervalSegmentSpec(Lists.newArrayList(new Interval("2011-01-12/2011-02-28")))
            );
            final Query<Result<SearchResultValue>> query2 = searchQuery.withQuerySegmentSpec(
                new MultipleIntervalSegmentSpec(Lists.newArrayList(new Interval("2011-03-01/2011-04-15")))
            );
            return Sequences.concat(runner.run(query1, responseContext), runner.run(query2, responseContext));
          }
        }
    );

    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "automotive", 186));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "mezzanine", 558));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "travel", 186));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "health", 186));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "entertainment", 186));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.marketDimension, "total_market", 372));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementishDimension, "a", 186));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.partialNullDimension, "value", 372));

    checkSearchQuery(searchQuery, mergedRunner, expectedHits);
  }

  @Test
  public void testSearchSameValueInMultiDims()
  {
    SearchQuery searchQuery = Druids.newSearchQueryBuilder()
                                    .dataSource(QueryRunnerTestHelper.dataSource)
                                    .granularity(QueryRunnerTestHelper.allGran)
                                    .intervals(QueryRunnerTestHelper.fullOnInterval)
                                    .dimensions(
                                        Arrays.asList(
                                            QueryRunnerTestHelper.placementDimension,
                                            QueryRunnerTestHelper.placementishDimension
                                        )
                                    )
                                    .query("e")
                                    .build();

    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementDimension, "preferred", 1209));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementishDimension, "e", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementishDimension, "preferred", 1209));

    checkSearchQuery(searchQuery, expectedHits);
  }

  @Test
  public void testSearchSameValueInMultiDims2()
  {
    SearchQuery searchQuery = Druids.newSearchQueryBuilder()
                                    .dataSource(QueryRunnerTestHelper.dataSource)
                                    .granularity(QueryRunnerTestHelper.allGran)
                                    .intervals(QueryRunnerTestHelper.fullOnInterval)
                                    .dimensions(
                                        Arrays.asList(
                                            QueryRunnerTestHelper.placementDimension,
                                            QueryRunnerTestHelper.placementishDimension
                                        )
                                    )
                                    .sortSpec(new StrlenSearchSortSpec())
                                    .query("e")
                                    .build();

    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementishDimension, "e", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementDimension, "preferred", 1209));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.placementishDimension, "preferred", 1209));

    checkSearchQuery(searchQuery, expectedHits);
  }

  @Test
  public void testFragmentSearch()
  {
    SearchQuery searchQuery = Druids.newSearchQueryBuilder()
                                    .dataSource(QueryRunnerTestHelper.dataSource)
                                    .granularity(QueryRunnerTestHelper.allGran)
                                    .intervals(QueryRunnerTestHelper.fullOnInterval)
                                    .query(new FragmentSearchQuerySpec(Arrays.asList("auto", "ve")))
                                    .build();

    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "automotive", 93));

    checkSearchQuery(searchQuery, expectedHits);
  }

  @Test
  public void testSearchWithDimensionQuality()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "automotive", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "mezzanine", 279));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "travel", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "health", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "entertainment", 93));

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .dimensions("quality")
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("a")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithDimensionProvider()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.marketDimension, "total_market", 186));

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .dimensions("market")
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("a")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithDimensionsQualityAndProvider()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "automotive", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "mezzanine", 279));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "travel", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "health", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "entertainment", 93));
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.marketDimension, "total_market", 186));

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .dimensions(
                  Arrays.asList(
                      QueryRunnerTestHelper.qualityDimension,
                      QueryRunnerTestHelper.marketDimension
                  )
              )
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("a")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithDimensionsPlacementAndProvider()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.marketDimension, "total_market", 186));

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .dimensions(
                  Arrays.asList(
                      QueryRunnerTestHelper.placementishDimension,
                      QueryRunnerTestHelper.marketDimension
                  )
              )
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("mark")
              .build(),
        expectedHits
    );
  }


  @Test
  public void testSearchWithExtractionFilter1()
  {
    final String automotiveSnowman = "automotive☃";
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, automotiveSnowman, 93));

    final LookupExtractionFn lookupExtractionFn = new LookupExtractionFn(
        new MapLookupExtractor(ImmutableMap.of("automotive", automotiveSnowman), false),
        true,
        null,
        true,
        false
    );

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .filters(
                  new ExtractionDimFilter(
                      QueryRunnerTestHelper.qualityDimension,
                      automotiveSnowman,
                      lookupExtractionFn,
                      null
                  )
              )
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .dimensions(
                  new ExtractionDimensionSpec(
                      QueryRunnerTestHelper.qualityDimension,
                      null,
                      lookupExtractionFn,
                      null
                  )
              )
              .query("☃")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithSingleFilter1()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "mezzanine", 93));

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .filters(
                  new AndDimFilter(
                      Arrays.<DimFilter>asList(
                          new SelectorDimFilter(QueryRunnerTestHelper.marketDimension, "total_market"),
                          new SelectorDimFilter(QueryRunnerTestHelper.qualityDimension, "mezzanine"))))
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .dimensions(QueryRunnerTestHelper.qualityDimension)
              .query("a")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithSingleFilter2()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.marketDimension, "total_market", 186));

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .filters(QueryRunnerTestHelper.marketDimension, "total_market")
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .dimensions(QueryRunnerTestHelper.marketDimension)
              .query("a")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchMultiAndFilter()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "automotive", 93));

    DimFilter filter = Druids.newAndDimFilterBuilder()
                             .fields(
                                 Arrays.<DimFilter>asList(
                                     Druids.newSelectorDimFilterBuilder()
                                           .dimension(QueryRunnerTestHelper.marketDimension)
                                           .value("spot")
                                           .build(),
                                     Druids.newSelectorDimFilterBuilder()
                                           .dimension(QueryRunnerTestHelper.qualityDimension)
                                           .value("automotive")
                                           .build()
                                 )
                             )
                             .build();

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .filters(filter)
              .dimensions(QueryRunnerTestHelper.qualityDimension)
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("a")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithMultiOrFilter()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();
    expectedHits.add(new SearchHit(QueryRunnerTestHelper.qualityDimension, "automotive", 93));

    DimFilter filter = Druids.newOrDimFilterBuilder()
                             .fields(
                                 Arrays.<DimFilter>asList(
                                     Druids.newSelectorDimFilterBuilder()
                                           .dimension(QueryRunnerTestHelper.qualityDimension)
                                           .value("total_market")
                                           .build(),
                                     Druids.newSelectorDimFilterBuilder()
                                           .dimension(QueryRunnerTestHelper.qualityDimension)
                                           .value("automotive")
                                           .build()
                                 )
                             )
                             .build();

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .dimensions(QueryRunnerTestHelper.qualityDimension)
              .filters(filter)
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("a")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithEmptyResults()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("abcd123")
              .build(),
        expectedHits
    );
  }

  @Test
  public void testSearchWithFilterEmptyResults()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();

    DimFilter filter = Druids.newAndDimFilterBuilder()
                             .fields(
                                 Arrays.<DimFilter>asList(
                                     Druids.newSelectorDimFilterBuilder()
                                           .dimension(QueryRunnerTestHelper.marketDimension)
                                           .value("total_market")
                                           .build(),
                                     Druids.newSelectorDimFilterBuilder()
                                           .dimension(QueryRunnerTestHelper.qualityDimension)
                                           .value("automotive")
                                           .build()
                                 )
                             )
                             .build();

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .filters(filter)
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .query("a")
              .build(),
        expectedHits
    );
  }


  @Test
  public void testSearchNonExistingDimension()
  {
    List<SearchHit> expectedHits = Lists.newLinkedList();

    checkSearchQuery(
        Druids.newSearchQueryBuilder()
              .dataSource(QueryRunnerTestHelper.dataSource)
              .granularity(QueryRunnerTestHelper.allGran)
              .intervals(QueryRunnerTestHelper.fullOnInterval)
              .dimensions("does_not_exist")
              .query("a")
              .build(),
        expectedHits
    );
  }

  private void checkSearchQuery(Query searchQuery, List<SearchHit> expectedResults)
  {
    checkSearchQuery(searchQuery, runner, expectedResults);
  }

  private void checkSearchQuery(Query searchQuery, QueryRunner runner, List<SearchHit> expectedResults)
  {
    Iterable<Result<SearchResultValue>> results = Sequences.toList(
        runner.run(searchQuery, ImmutableMap.of()),
        Lists.<Result<SearchResultValue>>newArrayList()
    );
    List<SearchHit> copy = ImmutableList.copyOf(expectedResults);
    for (Result<SearchResultValue> result : results) {
      Assert.assertEquals(new DateTime("2011-01-12T00:00:00.000Z"), result.getTimestamp());
      Assert.assertTrue(result.getValue() instanceof Iterable);

      Iterable<SearchHit> resultValues = result.getValue();
      for (SearchHit resultValue : resultValues) {
        int index = expectedResults.indexOf(resultValue);
        if (index < 0) {
          fail(
              copy, results,
              "No result found containing " + resultValue.getDimension() + " and " + resultValue.getValue()
          );
        }
        SearchHit expected = expectedResults.remove(index);
        if (!resultValue.toString().equals(expected.toString())) {
          fail(
              copy, results,
              "Invalid count for " + resultValue + ".. which was expected to be " + expected.getCount()
          );
        }
      }
    }
    if (!expectedResults.isEmpty()) {
      fail(copy, results, "Some expected results are not shown: " + expectedResults);
    }
  }

  private void fail(
      List<SearchHit> expectedResults,
      Iterable<Result<SearchResultValue>> results, String errorMsg
  )
  {
    LOG.info("Expected..");
    for (SearchHit expected : expectedResults) {
      LOG.info(expected.toString());
    }
    LOG.info("Result..");
    for (Result<SearchResultValue> r : results) {
      for (SearchHit v : r.getValue()) {
        LOG.info(v.toString());
      }
    }
    Assert.fail(errorMsg);
  }
}

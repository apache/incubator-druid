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

package io.druid.query.metadata;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.metamx.common.guava.Sequence;
import com.metamx.common.guava.Sequences;
import io.druid.collections.StupidPool;
import io.druid.query.Druids;
import io.druid.query.QueryRunner;
import io.druid.query.QueryRunnerFactory;
import io.druid.query.QueryRunnerTestHelper;
import io.druid.query.Result;
import io.druid.query.TestQueryRunners;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.DoubleMaxAggregatorFactory;
import io.druid.query.aggregation.DoubleMinAggregatorFactory;
import io.druid.query.aggregation.PostAggregator;
import io.druid.query.metadata.metadata.ColumnAnalysis;
import io.druid.query.metadata.metadata.ListColumnIncluderator;
import io.druid.query.metadata.metadata.SegmentAnalysis;
import io.druid.query.metadata.metadata.SegmentMetadataQuery;
import io.druid.segment.IncrementalIndexSegment;
import io.druid.segment.QueryableIndexSegment;
import io.druid.segment.TestHelper;
import io.druid.segment.TestIndex;
import io.druid.segment.column.ValueType;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
public class SegmentMetadataUnionQueryTest
{
  private static final QueryRunnerFactory FACTORY = new SegmentMetadataQueryRunnerFactory(
      new SegmentMetadataQueryQueryToolChest(new SegmentMetadataQueryConfig()),
      QueryRunnerTestHelper.NOOP_QUERYWATCHER
  );
  private final QueryRunner runner;
  private final boolean mmap;

  public SegmentMetadataUnionQueryTest(
      QueryRunner runner,
      boolean mmap
  )
  {
    this.runner = runner;
    this.mmap = mmap;
  }

  @Parameterized.Parameters
  public static Iterable<Object[]> constructorFeeder() throws IOException
  {
    return ImmutableList.of(
        new Object[]{
            QueryRunnerTestHelper.makeUnionQueryRunner(
                FACTORY,
                new QueryableIndexSegment(
                    QueryRunnerTestHelper.segmentId,
                    TestIndex.getMMappedTestIndex()
                )
            ), true
        },
        new Object[]{
            QueryRunnerTestHelper.makeUnionQueryRunner(
                FACTORY,
                new IncrementalIndexSegment(
                    TestIndex.getIncrementalTestIndex(),
                    QueryRunnerTestHelper.segmentId
                )
            ), false
        }
    );
  }


  @Test
  public void testSegmentMetadataUnionQuery()
  {
    System.out.println();
    SegmentAnalysis expected = new SegmentAnalysis(
        QueryRunnerTestHelper.segmentId,
        Lists.newArrayList(new Interval("2011-01-12T00:00:00.000Z/2011-04-15T00:00:00.001Z")),
        ImmutableMap.of(
            "placement",
            new ColumnAnalysis(
                ValueType.STRING.toString(),
                false,
                mmap ? 43524 : 43056,
                1,
                "preferred",
                "preferred",
                null
            )
        ),
        mmap ? 287928 : 291020,
        4836,
        null,
        null
    );
    SegmentMetadataQuery query = new Druids.SegmentMetadataQueryBuilder()
        .dataSource(QueryRunnerTestHelper.unionDataSource)
        .intervals(QueryRunnerTestHelper.fullOnInterval)
        .toInclude(new ListColumnIncluderator(Lists.newArrayList("placement")))
        .build();
    List result = Sequences.toList(runner.run(query, Maps.newHashMap()), Lists.<SegmentAnalysis>newArrayList());
    System.out.println(result);
    TestHelper.assertExpectedObjects(ImmutableList.of(expected), result, "failed SegmentMetadata union query");
  }


}

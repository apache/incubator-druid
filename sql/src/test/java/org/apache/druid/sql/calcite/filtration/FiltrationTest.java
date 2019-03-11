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

package org.apache.druid.sql.calcite.filtration;

import com.google.common.collect.ImmutableList;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.query.filter.IntervalDimFilter;
import org.apache.druid.query.filter.NotDimFilter;
import org.apache.druid.segment.column.ColumnHolder;
import org.apache.druid.segment.column.ValueType;
import org.apache.druid.sql.calcite.rel.DruidQuerySignature;
import org.apache.druid.sql.calcite.table.RowSignature;
import org.apache.druid.sql.calcite.util.CalciteTestBase;
import org.junit.Assert;
import org.junit.Test;

public class FiltrationTest extends CalciteTestBase
{
  @Test
  public void testNotIntervals()
  {
    final Filtration filtration = Filtration.create(
        new NotDimFilter(
            new IntervalDimFilter(
                ColumnHolder.TIME_COLUMN_NAME,
                ImmutableList.of(Intervals.of("2000/2001"), Intervals.of("2002/2003")),
                null
            )
        ),
        null
    ).optimize(new DruidQuerySignature(RowSignature.builder().add(ColumnHolder.TIME_COLUMN_NAME, ValueType.LONG).build()));

    Assert.assertEquals(
        ImmutableList.of(Filtration.eternity()),
        filtration.getIntervals()
    );

    Assert.assertEquals(
        new NotDimFilter(
            new IntervalDimFilter(
                ColumnHolder.TIME_COLUMN_NAME,
                ImmutableList.of(Intervals.of("2000/2001"), Intervals.of("2002/2003")),
                null
            )
        ),
        filtration.getDimFilter()
    );
  }
}

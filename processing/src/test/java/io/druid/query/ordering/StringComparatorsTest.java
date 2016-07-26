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

package io.druid.query.ordering;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import io.druid.jackson.DefaultObjectMapper;
import io.druid.query.ordering.StringComparators.StringComparator;

public class StringComparatorsTest
{
  
  private void commonTest(StringComparator comparator)
  {
    // equality test
    Assert.assertTrue(comparator.compare(null, null) == 0);
    Assert.assertTrue(comparator.compare("", "") == 0);
    Assert.assertTrue(comparator.compare("123", "123") == 0);
    Assert.assertTrue(comparator.compare("abc123", "abc123") == 0);
    
    // empty strings < non-empty
    Assert.assertTrue(comparator.compare("", "abc") < 0);
    Assert.assertTrue(comparator.compare("abc", "") > 0);
    
    // null first test
    Assert.assertTrue(comparator.compare(null, "apple") < 0);
  }
  
  @Test
  public void testLexicographicComparator()
  {
    commonTest(StringComparators.LEXICOGRAPHIC);

    Assert.assertTrue(StringComparators.LEXICOGRAPHIC.compare("apple", "banana") < 0);
    Assert.assertTrue(StringComparators.LEXICOGRAPHIC.compare("banana", "banana") == 0);
  }

  @Test
  public void testAlphanumericComparator()
  {
    commonTest(StringComparators.ALPHANUMERIC);

    // numbers < non numeric
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("123", "abc") < 0);
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("abc", "123") > 0);

    // numbers ordered numerically
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("2", "11") < 0);
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("a2", "a11") < 0);

    // leading zeros
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("02", "11") < 0);
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("02", "002") < 0);

    // decimal points ...
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("1.3", "1.5") < 0);

    // ... don't work too well
    Assert.assertTrue(StringComparators.ALPHANUMERIC.compare("1.3", "1.15") < 0);

    // but you can sort ranges
    List<String> sorted = Lists.newArrayList("1-5", "11-15", "16-20", "21-25", "26-30", "6-10", "Other");
    Collections.sort(sorted, StringComparators.ALPHANUMERIC);

    Assert.assertEquals(
        ImmutableList.of("1-5", "6-10", "11-15", "16-20", "21-25", "26-30", "Other"),
        sorted
    );

    List<String> sortedFixedDecimal = Lists.newArrayList(
        "Other", "[0.00-0.05)", "[0.05-0.10)", "[0.10-0.50)", "[0.50-1.00)",
        "[1.00-5.00)", "[5.00-10.00)", "[10.00-20.00)"
    );

    Collections.sort(sortedFixedDecimal, StringComparators.ALPHANUMERIC);

    Assert.assertEquals(
        ImmutableList.of(
            "[0.00-0.05)", "[0.05-0.10)", "[0.10-0.50)", "[0.50-1.00)",
            "[1.00-5.00)", "[5.00-10.00)", "[10.00-20.00)", "Other"
        ),
        sortedFixedDecimal
    ); 
  }

  @Test
  public void testStrlenComparator()
  {
    commonTest(StringComparators.STRLEN);

    Assert.assertTrue(StringComparators.STRLEN.compare("a", "apple") < 0);
    Assert.assertTrue(StringComparators.STRLEN.compare("a", "elppa") < 0);
    Assert.assertTrue(StringComparators.STRLEN.compare("apple", "elppa") < 0);
  }

  @Test
  public void testLexicographicComparatorSerdeTest() throws IOException
  {
    ObjectMapper jsonMapper = new DefaultObjectMapper();
    String expectJsonSpec = "{\"type\":\"lexicographic\"}";
    
    String jsonSpec = jsonMapper.writeValueAsString(StringComparators.LEXICOGRAPHIC);
    Assert.assertEquals(expectJsonSpec, jsonSpec);
    Assert.assertEquals(StringComparators.LEXICOGRAPHIC
        , jsonMapper.readValue(expectJsonSpec, StringComparators.LexicographicComparator.class));
  }
  
  @Test
  public void testAlphanumericComparatorSerdeTest() throws IOException
  {
    ObjectMapper jsonMapper = new DefaultObjectMapper();
    String expectJsonSpec = "{\"type\":\"alphanumeric\"}";
    
    String jsonSpec = jsonMapper.writeValueAsString(StringComparators.ALPHANUMERIC);
    Assert.assertEquals(expectJsonSpec, jsonSpec);
    Assert.assertEquals(StringComparators.ALPHANUMERIC
        , jsonMapper.readValue(expectJsonSpec, StringComparators.AlphanumericComparator.class));
  }
  
  @Test
  public void testStrlenComparatorSerdeTest() throws IOException
  {
    ObjectMapper jsonMapper = new DefaultObjectMapper();
    String expectJsonSpec = "{\"type\":\"strlen\"}";
    
    String jsonSpec = jsonMapper.writeValueAsString(StringComparators.STRLEN);
    Assert.assertEquals(expectJsonSpec, jsonSpec);
    Assert.assertEquals(StringComparators.STRLEN
        , jsonMapper.readValue(expectJsonSpec, StringComparators.StrlenComparator.class));
  }
}

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

package io.druid.java.util.common.parsers;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import io.druid.java.util.common.StringUtils;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.IntFunction;

public class ParserUtils
{
  private static final String DEFAULT_COLUMN_NAME_PREFIX = "column_";

  public static final Function<String, String> nullEmptyStringFunction = new Function<String, String>()
  {
    @Override
    public String apply(String input)
    {
      if (input == null || input.isEmpty()) {
        return null;
      }
      return input;
    }
  };

  public static ArrayList<String> generateFieldNames(int length)
  {
    final ArrayList<String> names = new ArrayList<>(length);
    final IntFunction<String> columnNameGenerator = getDefaultColumnNameGenerator();
    for (int i = 0; i < length; ++i) {
      names.add(columnNameGenerator.apply(i));
    }
    return names;
  }

  /**
   * Factored timestamp parsing into its own Parser class, but leaving this here
   * for compatibility
   *
   * @param format
   *
   * @return
   */
  public static Function<String, DateTime> createTimestampParser(final String format)
  {
    return TimestampParser.createTimestampParser(format);
  }

  public static Set<String> findDuplicates(Iterable<String> fieldNames)
  {
    Set<String> duplicates = Sets.newHashSet();
    Set<String> uniqueNames = Sets.newHashSet();

    for (String fieldName : fieldNames) {
      String next = StringUtils.toLowerCase(fieldName);
      if (uniqueNames.contains(next)) {
        duplicates.add(next);
      }
      uniqueNames.add(next);
    }

    return duplicates;
  }

  public static void validateFields(Iterable<String> fieldNames)
  {
    Set<String> duplicates = findDuplicates(fieldNames);
    if (!duplicates.isEmpty()) {
      throw new ParseException("Duplicate column entries found : %s", duplicates.toString());
    }
  }

  public static String stripQuotes(String input)
  {
    input = input.trim();
    if (input.charAt(0) == '\"' && input.charAt(input.length() - 1) == '\"') {
      input = input.substring(1, input.length() - 1).trim();
    }
    return input;
  }

  /**
   * Return a function to generate default column names.
   * Note that the postfix for default column names starts from 1.
   *
   * @return column name generating function
   */
  public static IntFunction<String> getDefaultColumnNameGenerator()
  {
    return ordinal -> DEFAULT_COLUMN_NAME_PREFIX + (ordinal + 1);
  }
}

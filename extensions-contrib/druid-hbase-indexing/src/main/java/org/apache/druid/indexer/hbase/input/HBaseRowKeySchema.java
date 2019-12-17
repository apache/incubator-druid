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

package org.apache.druid.indexer.hbase.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

public class HBaseRowKeySchema
{

  private final String type;
  private final String name;
  private final int length;

  @JsonCreator
  public HBaseRowKeySchema(@JsonProperty("type") String type, @JsonProperty("name") String name,
      @Nullable @JsonProperty("length") int length)
  {
    this.type = type;
    this.name = name;
    this.length = length;
  }

  @JsonProperty
  public String getType()
  {
    return type;
  }

  @JsonProperty
  public String getName()
  {
    return name;
  }

  @JsonProperty
  public int getLength()
  {
    return length;
  }

  @Override
  public String toString()
  {
    return "[type: " + type + ", name: " + name + (length == 0 ? "]" : ", length: " + length);
  }
}

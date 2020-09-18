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

package org.apache.druid.server.metrics;

public interface TaskSlotCountStatsProvider
{
  /**
   * Return the number of total task slots during emission period.
   */
  long getTotalTaskSlotCount();

  /**
   * Return the number of idle task slots during emission period.
   */
  long getIdleTaskSlotCount();

  /**
   * Return the number of used task slots during emission period.
   */
  long getUsedTaskSlotCount();

  /**
   * Return the number of lazy task slots during emission period.
   */
  long getLazyTaskSlotCount();

  /**
   * Return the number of blacklisted task slots during emission period.
   */
  long getBlacklistedTaskSlotCount();
}
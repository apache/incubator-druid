/*
 * Druid - a distributed column store.
 * Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.segment.realtime.plumber;

import io.druid.data.input.Committer;
import io.druid.data.input.InputRow;
import io.druid.query.Query;
import io.druid.query.QueryRunner;
import io.druid.segment.incremental.IndexSizeExceededException;

public interface Plumber
{
  /**
   * Perform any initial setup. Should be called before using any other methods, and should be paired
   * with a corresponding call to {@link #finishJob}.
   */
  public void startJob();

  public Object getMetaData();
  
  /**
   * @param row - the row to insert
   * @return - positive numbers indicate how many summarized rows exist in the index for that timestamp,
   * -1 means a row was thrown away because it was too late
   */
  public int add(InputRow row) throws IndexSizeExceededException;
  public <T> QueryRunner<T> getQueryRunner(Query<T> query);

  /**
   * Persist any in-memory indexed data to durable storage. This may be only somewhat durable, e.g. the
   * machine's local disk.
   *
   * @param commitRunnable code to run after persisting data
   */
  void persist(Committer commitRunnable);
  void persist(Runnable commitRunnable);
  /**
   * Perform any final processing and clean up after ourselves. Should be called after all data has been
   * fed into sinks and persisted.
   */
  public void finishJob();

  public Sink getSink(long timestamp);
}

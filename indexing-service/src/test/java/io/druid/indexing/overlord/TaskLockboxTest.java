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

package io.druid.indexing.overlord;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import io.druid.data.input.FirehoseFactory;
import io.druid.indexing.common.TaskLock;
import io.druid.indexing.common.config.TaskStorageConfig;
import io.druid.indexing.common.task.NoopTask;
import io.druid.indexing.common.task.Task;
import io.druid.server.initialization.ServerConfig;
import org.easymock.EasyMock;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class TaskLockboxTest
{
  private TaskStorage taskStorage;

  private TaskLockbox lockbox;

  @Before
  public void setUp()
  {
    taskStorage = new HeapMemoryTaskStorage(new TaskStorageConfig(null));
    ServerConfig serverConfig = EasyMock.niceMock(ServerConfig.class);
    EasyMock.expect(serverConfig.getMaxIdleTime()).andReturn(new Period(100));
    EasyMock.replay(serverConfig);
    lockbox = new TaskLockbox(taskStorage, serverConfig);
  }

  @Test
  public void testLock() throws InterruptedException
  {
    Task task = NoopTask.create();
    lockbox.add(task);
    Assert.assertNotNull(lockbox.lock(task, new Interval("2015-01-01/2015-01-02")));
  }

  @Test(expected = IllegalStateException.class)
  public void testLockForInactiveTask() throws InterruptedException
  {
    lockbox.lock(NoopTask.create(), new Interval("2015-01-01/2015-01-02"));
  }

  @Test(expected = IllegalStateException.class)
  public void testLockAfterTaskComplete() throws InterruptedException
  {
    Task task = NoopTask.create();
    lockbox.add(task);
    lockbox.remove(task);
    lockbox.lock(task, new Interval("2015-01-01/2015-01-02"));
  }

  @Test
  public void testTryLock() throws InterruptedException
  {
    Task task = NoopTask.create();
    lockbox.add(task);
    Assert.assertTrue(lockbox.tryLock(task, new Interval("2015-01-01/2015-01-03")).isPresent());

    // try to take lock for task 2 for overlapping interval
    Task task2 = NoopTask.create();
    lockbox.add(task2);
    Assert.assertFalse(lockbox.tryLock(task2, new Interval("2015-01-01/2015-01-02")).isPresent());

    // task 1 unlocks the lock
    lockbox.remove(task);

    // Now task2 should be able to get the lock
    Assert.assertTrue(lockbox.tryLock(task2, new Interval("2015-01-01/2015-01-02")).isPresent());
  }

  @Test
  public void testTrySmallerLock() throws InterruptedException
  {
    Task task = NoopTask.create();
    lockbox.add(task);
    Optional<TaskLock> lock1 = lockbox.tryLock(task, new Interval("2015-01-01/2015-01-03"));
    Assert.assertTrue(lock1.isPresent());
    Assert.assertEquals(new Interval("2015-01-01/2015-01-03"), lock1.get().getInterval());

    // same task tries to take partially overlapping interval; should fail
    Assert.assertFalse(lockbox.tryLock(task, new Interval("2015-01-02/2015-01-04")).isPresent());

    // same task tries to take contained interval; should succeed and should match the original lock
    Optional<TaskLock> lock2 = lockbox.tryLock(task, new Interval("2015-01-01/2015-01-02"));
    Assert.assertTrue(lock2.isPresent());
    Assert.assertEquals(new Interval("2015-01-01/2015-01-03"), lock2.get().getInterval());

    // only the first lock should actually exist
    Assert.assertEquals(
        ImmutableList.of(lock1.get()),
        lockbox.findLocksForTask(task)
    );
  }

  @Test(expected = IllegalStateException.class)
  public void testTryLockForInactiveTask() throws InterruptedException
  {
    Assert.assertFalse(lockbox.tryLock(NoopTask.create(), new Interval("2015-01-01/2015-01-02")).isPresent());
  }

  @Test(expected = IllegalStateException.class)
  public void testTryLockAfterTaskComplete() throws InterruptedException
  {
    Task task = NoopTask.create();
    lockbox.add(task);
    lockbox.remove(task);
    Assert.assertFalse(lockbox.tryLock(task, new Interval("2015-01-01/2015-01-02")).isPresent());
  }

  @Test(expected = InterruptedException.class)
  public void testTimeoutForLock() throws InterruptedException
  {
    Task task1 = NoopTask.create();
    Task task2 = new SomeTask(null, 0, 0, null, null, null);

    lockbox.add(task1);
    lockbox.add(task2);

    lockbox.lock(task1, new Interval("2015-01-01/2015-01-02"));
    lockbox.lock(task2, new Interval("2015-01-01/2015-01-15"));
  }

  public class SomeTask extends NoopTask {

    public SomeTask(
        @JsonProperty("id") String id,
        @JsonProperty("runTime") long runTime,
        @JsonProperty("isReadyTime") long isReadyTime,
        @JsonProperty("isReadyResult") String isReadyResult,
        @JsonProperty("firehose") FirehoseFactory firehoseFactory,
        @JsonProperty("context") Map<String, Object> context
    )
    {
      super(id, runTime, isReadyTime, isReadyResult, firehoseFactory, context);
    }

    @Override
    public String getType()
    {
      return "someTask";
    }

    @Override
    public  String getGroupId() { return "someGroupId";}

  }

}

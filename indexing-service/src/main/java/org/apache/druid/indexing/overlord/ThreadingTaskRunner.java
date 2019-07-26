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

package org.apache.druid.indexing.overlord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.druid.guice.annotations.Self;
import org.apache.druid.indexer.RunnerTaskState;
import org.apache.druid.indexer.TaskLocation;
import org.apache.druid.indexer.TaskStatus;
import org.apache.druid.indexing.common.MultipleFileTaskReportFileWriter;
import org.apache.druid.indexing.common.TaskReportFileWriter;
import org.apache.druid.indexing.common.TaskToolbox;
import org.apache.druid.indexing.common.TaskToolboxFactory;
import org.apache.druid.indexing.common.config.TaskConfig;
import org.apache.druid.indexing.common.task.Task;
import org.apache.druid.indexing.overlord.autoscaling.ScalingStats;
import org.apache.druid.indexing.worker.config.WorkerConfig;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.java.util.common.IOE;
import org.apache.druid.java.util.common.ISE;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.concurrent.Execs;
import org.apache.druid.java.util.common.io.Closer;
import org.apache.druid.java.util.emitter.EmittingLogger;
import org.apache.druid.query.Query;
import org.apache.druid.query.QueryRunner;
import org.apache.druid.query.QuerySegmentWalker;
import org.apache.druid.query.SegmentDescriptor;
import org.apache.druid.segment.realtime.appenderator.AppenderatorsManager;
import org.apache.druid.server.DruidNode;
import org.apache.druid.tasklogs.TaskLogPusher;
import org.apache.druid.tasklogs.TaskLogStreamer;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * TaskRunner implemention for the CliIndexer task execution service, which runs all tasks in a single process.
 *
 * Two thread pools are used:
 * - A task execution pool, sized to number of worker slots. This is used to execute the Task run() methods.
 * - A control thread pool, sized to worker slots * 2. The control threads setup and submit work to the
 *   task execution pool, and are also responsible for running graceful shutdown on the Task objects.
 *   Only one shutdown per-task can be running at a given time, and there is one control thread per task,
 *   thus the pool has 2 * worker slots.
 *
 * Note that separate task logs are not supported, all task log entries will be written to the Indexer process log
 * instead.
 */
public class ThreadingTaskRunner
    extends BaseRestorableTaskRunner<ThreadingTaskRunner.ThreadingTaskRunnerWorkItem>
    implements TaskLogStreamer, QuerySegmentWalker
{
  private static final EmittingLogger LOGGER = new EmittingLogger(ThreadingTaskRunner.class);

  private final TaskToolboxFactory toolboxFactory;
  private final TaskLogPusher taskLogPusher;
  private final DruidNode node;
  private final AppenderatorsManager appenderatorsManager;
  private final MultipleFileTaskReportFileWriter taskReportFileWriter;
  private final ListeningExecutorService taskExecutor;
  private final ListeningExecutorService controlThreadExecutor;

  private volatile boolean stopping = false;

  @Inject
  public ThreadingTaskRunner(
      TaskToolboxFactory toolboxFactory,
      TaskConfig taskConfig,
      WorkerConfig workerConfig,
      TaskLogPusher taskLogPusher,
      ObjectMapper jsonMapper,
      AppenderatorsManager appenderatorsManager,
      TaskReportFileWriter taskReportFileWriter,
      @Self DruidNode node
  )
  {
    super(jsonMapper, taskConfig);
    this.toolboxFactory = toolboxFactory;
    this.taskLogPusher = taskLogPusher;
    this.node = node;
    this.appenderatorsManager = appenderatorsManager;
    this.taskReportFileWriter = (MultipleFileTaskReportFileWriter) taskReportFileWriter;
    this.taskExecutor = MoreExecutors.listeningDecorator(
        Execs.multiThreaded(workerConfig.getCapacity(), "threading-task-runner-executor-%d")
    );
    this.controlThreadExecutor = MoreExecutors.listeningDecorator(
        Execs.multiThreaded(workerConfig.getCapacity() * 2, "threading-task-runner-control-%d")
    );
  }

  @Override
  public Optional<ByteSource> streamTaskLog(String taskid, long offset) throws IOException
  {
    // task logs will appear in the main indexer log, streaming individual task logs is not supported
    return Optional.absent();
  }

  @Override
  public void start()
  {
    // Nothing to start.
  }

  @Override
  public ListenableFuture<TaskStatus> run(Task task)
  {
    synchronized (tasks) {
      tasks.computeIfAbsent(
          task.getId(), k ->
              new ThreadingTaskRunnerWorkItem(
                  task,
                  controlThreadExecutor.submit(
                      new Callable<TaskStatus>() {
                        @Override
                        public TaskStatus call()
                        {
                          final String attemptUUID = UUID.randomUUID().toString();
                          final File taskDir = taskConfig.getTaskDir(task.getId());
                          final File attemptDir = new File(taskDir, attemptUUID);

                          final TaskLocation taskLocation = TaskLocation.create(
                              node.getHost(),
                              node.getPlaintextPort(),
                              node.getTlsPort()
                          );

                          try {
                            final Closer closer = Closer.create();
                            try {
                              if (!attemptDir.mkdirs()) {
                                throw new IOE("Could not create directories: %s", attemptDir);
                              }

                              final File taskFile = new File(taskDir, "task.json");
                              final File reportsFile = new File(attemptDir, "report.json");
                              taskReportFileWriter.add(task.getId(), reportsFile);

                              final ThreadingTaskRunnerWorkItem taskWorkItem;
                              // time to adjust process holders
                              synchronized (tasks) {
                                taskWorkItem = tasks.get(task.getId());

                                if (taskWorkItem == null) {
                                  LOGGER.makeAlert("WTF?! TaskInfo disappeared!").addData("task", task.getId()).emit();
                                  throw new ISE("TaskInfo disappeared for task[%s]!", task.getId());
                                }

                                if (taskWorkItem.shutdown) {
                                  throw new IllegalStateException("Task has been shut down!");
                                }
                              }

                              if (!taskFile.exists()) {
                                jsonMapper.writeValue(taskFile, task);
                              }

                              // This will block for a while. So we append the thread information with more details
                              final String priorThreadName = Thread.currentThread().getName();
                              Thread.currentThread().setName(StringUtils.format("%s-[%s]", priorThreadName, task.getId()));

                              TaskStatus taskStatus = null;
                              final TaskToolbox toolbox = toolboxFactory.build(task);
                              try {
                                ListenableFuture<TaskStatus> taskStatusFuture = taskExecutor.submit(
                                    new Callable<TaskStatus>()
                                    {
                                      @Override
                                      public TaskStatus call()
                                      {
                                        taskWorkItem.setThread(Thread.currentThread());
                                        try {
                                          return task.run(toolbox);
                                        }
                                        catch (Exception e) {
                                          LOGGER.error(e, "Task[%s] exited with exception.", task.getId());
                                          return null;
                                        }
                                        finally {
                                          taskWorkItem.setThread(null);
                                        }
                                      }
                                    }
                                );
                                TaskRunnerUtils.notifyLocationChanged(listeners, task.getId(), taskLocation);
                                TaskRunnerUtils.notifyStatusChanged(
                                    listeners,
                                    task.getId(),
                                    TaskStatus.running(task.getId())
                                );
                                taskStatus = taskStatusFuture.get();
                              }
                              finally {
                                taskWorkItem.setFinished(true);
                                Thread.currentThread().setName(priorThreadName);
                                if (reportsFile.exists()) {
                                  taskLogPusher.pushTaskReports(task.getId(), reportsFile);
                                }
                                if (taskStatus == null) {
                                  taskStatus = TaskStatus.failure(task.getId());
                                }
                              }

                              TaskRunnerUtils.notifyStatusChanged(listeners, task.getId(), taskStatus);
                              return taskStatus;
                            }
                            catch (Throwable t) {
                              throw closer.rethrow(t);
                            }
                            finally {
                              closer.close();
                            }
                          }
                          catch (Throwable t) {
                            LOGGER.info(t, "Exception caught during execution");
                            throw new RuntimeException(t);
                          }
                          finally {
                            taskReportFileWriter.delete(task.getId());
                            appenderatorsManager.removeAppenderatorForTask(task.getId());

                            try {
                              synchronized (tasks) {
                                final ThreadingTaskRunnerWorkItem taskWorkItem = tasks.remove(task.getId());
                                if (!stopping) {
                                  saveRunningTasks();
                                }
                              }

                              try {
                                if (!stopping && taskDir.exists()) {
                                  LOGGER.info("Removing task directory: %s", taskDir);
                                  FileUtils.deleteDirectory(taskDir);
                                }
                              }
                              catch (Exception e) {
                                LOGGER.makeAlert(e, "Failed to delete task directory")
                                      .addData("taskDir", taskDir.toString())
                                      .addData("task", task.getId())
                                      .emit();
                              }
                            }
                            catch (Exception e) {
                              LOGGER.error(e, "Suppressing exception caught while cleaning up task");
                            }
                          }
                        }
                      }
                  )
              )
      );
      saveRunningTasks();
      return tasks.get(task.getId()).getResult();
    }
  }

  @Override
  public void shutdown(String taskid, String reason)
  {
    LOGGER.info("Shutdown [%s] because: [%s]", taskid, reason);
    final ThreadingTaskRunnerWorkItem taskInfo;

    synchronized (tasks) {
      taskInfo = tasks.get(taskid);

      if (taskInfo == null) {
        LOGGER.info("Ignoring request to cancel unknown task: %s", taskid);
        return;
      }

      if (taskInfo.shutdown) {
        LOGGER.info(
            "Task [%s] is already shutting down, ignoring duplicate shutdown request with reason [%s]",
            taskid,
            reason
        );
      } else {
        taskInfo.shutdown = true;
        scheduleTaskShutdown(taskInfo);
      }
    }
  }

  /**
   * Submits a callable to the control thread pool that attempts a task graceful shutdown,
   * if shutdown is not already scheduled.
   *
   * The shutdown will wait for the configured timeout and then interrupt the thread if the timeout is exceeded.
   */
  private ListenableFuture scheduleTaskShutdown(ThreadingTaskRunnerWorkItem taskInfo)
  {
    synchronized (tasks) {
      if (taskInfo.shutdownFuture != null) {
        return taskInfo.shutdownFuture;
      }

      ListenableFuture shutdownFuture = controlThreadExecutor.submit(
          new Callable<Void>()
          {
            @Override
            public Void call()
            {
              LOGGER.info("Stopping thread for task: %s", taskInfo.getTaskId());
              taskInfo.getTask().stopGracefully(taskConfig);

              try {
                TaskStatus status = taskInfo.getResult().get(
                    taskConfig.getGracefulShutdownTimeout().toStandardDuration().getMillis(),
                    TimeUnit.MILLISECONDS
                );

                if (status == null) {
                  if (taskInfo.thread != null) {
                    taskInfo.thread.interrupt();
                  }
                }
              }
              catch (Exception e) {
                LOGGER.info(e, "Encountered exception while waiting for task [%s] shutdown", taskInfo.getTaskId());
                if (taskInfo.thread != null) {
                  taskInfo.thread.interrupt();
                }
              }
              return null;
            }
          }
      );

      taskInfo.shutdownFuture = shutdownFuture;
      return shutdownFuture;
    }
  }

  /**
   * First shuts down the task execution pool and then schedules a graceful shutdown attempt for each active task.
   *
   * After the tasks shutdown gracefully or the graceful shutdown timeout is exceeded, the control thread pool
   * will be terminated (also waiting for the graceful shutdown period for this termination).
   */
  @Override
  public void stop()
  {
    stopping = true;
    taskExecutor.shutdown();

    List<ListenableFuture<Void>> shutdownFutures = new ArrayList<>();
    synchronized (tasks) {
      for (ThreadingTaskRunnerWorkItem taskWorkItem : tasks.values()) {
        shutdownFutures.add(scheduleTaskShutdown(taskWorkItem));
      }
    }
    controlThreadExecutor.shutdown();
    try {
      ListenableFuture<List<Void>> shutdownFuture = Futures.successfulAsList(shutdownFutures);
      shutdownFuture.get();
    }
    catch (Exception e) {
      LOGGER.error(e, "Encountered exception when stopping all tasks.");
    }

    final DateTime start = DateTimes.nowUtc();
    final long gracefulShutdownMillis = taskConfig.getGracefulShutdownTimeout().toStandardDuration().getMillis();

    LOGGER.info("Waiting up to %,dms for shutdown.", gracefulShutdownMillis);
    if (gracefulShutdownMillis > 0) {
      try {
        final boolean terminated = controlThreadExecutor.awaitTermination(
            gracefulShutdownMillis,
            TimeUnit.MILLISECONDS
        );
        final long elapsed = System.currentTimeMillis() - start.getMillis();
        if (terminated) {
          LOGGER.info("Finished stopping in %,dms.", elapsed);
        } else {
          final Set<String> stillRunning;
          synchronized (tasks) {
            stillRunning = ImmutableSet.copyOf(tasks.keySet());
          }
          LOGGER.makeAlert("Failed to stop task threads")
                .addData("stillRunning", stillRunning)
                .addData("elapsed", elapsed)
                .emit();

          LOGGER.warn(
              "Executor failed to stop after %,dms, not waiting for it! Tasks still running: [%s]",
              elapsed,
              Joiner.on("; ").join(stillRunning)
          );
        }
      }
      catch (InterruptedException e) {
        LOGGER.warn(e, "Interrupted while waiting for executor to finish.");
        Thread.currentThread().interrupt();
      }
    } else {
      LOGGER.warn("Ran out of time, not waiting for executor to finish!");
    }
  }

  @Override
  public Collection<TaskRunnerWorkItem> getRunningTasks()
  {
    synchronized (tasks) {
      final List<TaskRunnerWorkItem> ret = new ArrayList<>();
      for (final ThreadingTaskRunnerWorkItem taskWorkItem : tasks.values()) {
        if (taskWorkItem.getThread() != null) {
          ret.add(taskWorkItem);
        }
      }
      return ret;
    }
  }

  @Override
  public Collection<TaskRunnerWorkItem> getPendingTasks()
  {
    synchronized (tasks) {
      final List<TaskRunnerWorkItem> ret = new ArrayList<>();
      for (final ThreadingTaskRunnerWorkItem taskWorkItem : tasks.values()) {
        if (taskWorkItem.getThread() == null) {
          ret.add(taskWorkItem);
        }
      }
      return ret;
    }
  }

  @Nullable
  @Override
  public RunnerTaskState getRunnerTaskState(String taskId)
  {
    final ThreadingTaskRunnerWorkItem workItem = tasks.get(taskId);
    if (workItem == null) {
      return null;
    } else {
      if (workItem.getThread() == null) {
        return RunnerTaskState.PENDING;
      } else if (!workItem.isFinished()) {
        return RunnerTaskState.RUNNING;
      } else {
        return RunnerTaskState.NONE;
      }
    }
  }

  @Override
  public Optional<ScalingStats> getScalingStats()
  {
    return Optional.absent();
  }

  @Override
  public <T> QueryRunner<T> getQueryRunnerForIntervals(
      Query<T> query,
      Iterable<Interval> intervals
  )
  {
    return appenderatorsManager.getQueryRunnerForIntervals(query, intervals);
  }

  @Override
  public <T> QueryRunner<T> getQueryRunnerForSegments(
      Query<T> query,
      Iterable<SegmentDescriptor> specs
  )
  {
    return appenderatorsManager.getQueryRunnerForSegments(query, specs);
  }

  protected static class ThreadingTaskRunnerWorkItem extends TaskRunnerWorkItem
  {
    private final Task task;
    private volatile Thread thread;
    private boolean finished = false;
    private volatile boolean shutdown = false;
    private ListenableFuture shutdownFuture;

    private ThreadingTaskRunnerWorkItem(
        Task task,
        ListenableFuture<TaskStatus> statusFuture
    )
    {
      super(task.getId(), statusFuture);
      this.task = task;
    }

    public Task getTask()
    {
      return task;
    }

    @Override
    public TaskLocation getLocation()
    {
      return null;
    }

    @Override
    public String getTaskType()
    {
      return task.getType();
    }

    @Override
    public String getDataSource()
    {
      return task.getDataSource();
    }

    public void setThread(Thread thread)
    {
      this.thread = thread;
    }

    public Thread getThread()
    {
      return thread;
    }

    public boolean isFinished()
    {
      return finished;
    }

    public void setFinished(boolean finished)
    {
      this.finished = finished;
    }
  }
}

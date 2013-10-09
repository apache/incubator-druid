/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.indexer;

import com.google.api.client.util.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.OutputSupplier;
import com.metamx.common.logger.Logger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

/**
 */
public class JobHelper
{
  private static final Logger log = new Logger(JobHelper.class);

  private static final Set<Path> existing = Sets.newHashSet();


  public static void setupClasspath(
      HadoopDruidIndexerConfig config,
      Job groupByJob
  )
      throws IOException
  {
    String classpathProperty = System.getProperty("druid.hadoop.internal.classpath");
    if (classpathProperty == null) {
      classpathProperty = System.getProperty("java.class.path");
    }

    String[] jarFiles = classpathProperty.split(File.pathSeparator);

    final Configuration conf = groupByJob.getConfiguration();
    final FileSystem fs = FileSystem.get(conf);
    Path distributedClassPath = new Path(config.getJobOutputDir(), "classpath");

    if (fs instanceof LocalFileSystem) {
      return;
    }

    for (String jarFilePath : jarFiles) {
      File jarFile = new File(jarFilePath);
      if (jarFile.getName().endsWith(".jar")) {
        final Path hdfsPath = new Path(distributedClassPath, jarFile.getName());

        if (! existing.contains(hdfsPath)) {
          if (jarFile.getName().endsWith("SNAPSHOT.jar") || !fs.exists(hdfsPath)) {
            log.info("Uploading jar to path[%s]", hdfsPath);
            ByteStreams.copy(
                Files.newInputStreamSupplier(jarFile),
                new OutputSupplier<OutputStream>()
                {
                  @Override
                  public OutputStream getOutput() throws IOException
                  {
                    return fs.create(hdfsPath);
                  }
                }
            );
          }

          existing.add(hdfsPath);
        }

        DistributedCache.addFileToClassPath(hdfsPath, conf, fs);
      }
    }
  }
}

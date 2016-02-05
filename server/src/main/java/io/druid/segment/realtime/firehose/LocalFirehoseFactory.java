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

package io.druid.segment.realtime.firehose;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.metamx.common.CompressionUtils;
import com.metamx.common.IAE;
import com.metamx.common.ISE;
import com.metamx.emitter.EmittingLogger;
import io.druid.data.input.Firehose;
import io.druid.data.input.FirehoseFactory;
import io.druid.data.input.impl.FileIteratingFirehose;
import io.druid.data.input.impl.StringInputRowParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 */
public class LocalFirehoseFactory implements FirehoseFactory<StringInputRowParser>
{
	private static final EmittingLogger log = new EmittingLogger(LocalFirehoseFactory.class);

	private final File baseDir;
	private final String filter;
	private final StringInputRowParser parser;

	@JsonCreator
	public LocalFirehoseFactory(
	    @JsonProperty("baseDir") File baseDir,
	    @JsonProperty("filter") String filter,
	    // Backwards compatible
	    @JsonProperty("parser") StringInputRowParser parser)
	{
		this.baseDir = baseDir;
		this.filter = filter;
		this.parser = parser;
	}

	@JsonProperty
	public File getBaseDir()
	{
		return baseDir;
	}

	@JsonProperty
	public String getFilter()
	{
		return filter;
	}

	@JsonProperty
	public StringInputRowParser getParser()
	{
		return parser;
	}

	@Override
	public Firehose connect(StringInputRowParser firehoseParser) throws IOException
	{
		if (baseDir == null)
		{
			throw new IAE("baseDir is null");
		}
		log.info("Searching for all [%s] in and beneath [%s]", filter, baseDir.getAbsoluteFile());

		Collection<File> foundFiles = FileUtils.listFiles(
		    baseDir.getAbsoluteFile(),
		    new WildcardFileFilter(filter),
		    TrueFileFilter.INSTANCE);

		if (foundFiles == null || foundFiles.isEmpty())
		{
			throw new ISE("Found no files to ingest! Check your schema.");
		}
		log.info("Found files: " + foundFiles);

		final LinkedList<File> files = Lists.newLinkedList(
		    foundFiles);

		return new FileIteratingFirehose(
		    new Iterator<LineIterator>()
		    {
			    @Override
			    public boolean hasNext()
			    {
				    return !files.isEmpty();
			    }

			    @Override
			    public LineIterator next()
			    {
				    final File f = files.poll();
				    InputStream rawInputStream = null;
				    try
				    {
					    rawInputStream = new FileInputStream(f);
					    final InputStream inputStream;
					    String logMessage;
					    if (CompressionUtils.isGz(f.getName()))
					    {
						    logMessage = "Reading gzipped file [%s]";
						    inputStream = CompressionUtils.gzipInputStream(rawInputStream);
					    } else
					    {
						    logMessage = "Reading file [%s]";
						    inputStream = rawInputStream;
					    }

					    log.info(logMessage, f.getName());

					    return IOUtils.lineIterator(
		              new BufferedReader(
		                  new InputStreamReader(inputStream, Charsets.UTF_8)));
				    } catch (Exception e)
				    {
					    log.warn(e, "Failed to read file [%s]", f.getName());
					    if (rawInputStream != null)
					    {
						    try
						    {
							    rawInputStream.close();
						    } catch (IOException ioe)
						    {
							    Throwables.propagate(ioe);
						    }
					    }
					    throw Throwables.propagate(e);
				    }
			    }

			    @Override
			    public void remove()
			    {
				    throw new UnsupportedOperationException();
			    }
		    },
		    firehoseParser);
	}
}

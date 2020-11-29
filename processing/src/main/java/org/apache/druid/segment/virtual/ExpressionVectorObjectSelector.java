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

package org.apache.druid.segment.virtual;

import com.google.common.base.Preconditions;
import org.apache.druid.math.expr.Expr;
import org.apache.druid.math.expr.vector.ExprVectorProcessor;
import org.apache.druid.segment.vector.ReadableVectorOffset;
import org.apache.druid.segment.vector.VectorObjectSelector;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ExpressionVectorObjectSelector implements VectorObjectSelector
{
  private final Expr.VectorInputBinding bindings;
  private final ExprVectorProcessor<?> processor;

  @Nullable
  @MonotonicNonNull
  private Object[] cached = null;
  private int currentId = ReadableVectorOffset.NULL_ID;

  public ExpressionVectorObjectSelector(ExprVectorProcessor<?> processor, Expr.VectorInputBinding bindings)
  {
    this.processor = Preconditions.checkNotNull(processor, "processor");
    this.bindings = Preconditions.checkNotNull(bindings, "bindings");
  }

  @Override
  public Object[] getObjectVector()
  {
    if (bindings.getCurrentVectorId() != currentId) {
      currentId = bindings.getCurrentVectorId();
      cached = processor.evalVector(bindings).getObjectVector();
    }
    return cached;
  }

  @Override
  public int getMaxVectorSize()
  {
    return bindings.getMaxVectorSize();
  }

  @Override
  public int getCurrentVectorSize()
  {
    return bindings.getCurrentVectorSize();
  }
}

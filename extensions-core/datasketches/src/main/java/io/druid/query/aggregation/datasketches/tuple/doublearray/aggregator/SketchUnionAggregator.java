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

package io.druid.query.aggregation.datasketches.tuple.doublearray.aggregator;

import java.util.List;

import com.metamx.common.ISE;
import com.metamx.common.logger.Logger;
import com.yahoo.sketches.ResizeFactor;
import com.yahoo.sketches.memory.Memory;
import com.yahoo.sketches.tuple.ArrayOfDoublesIntersection;
import com.yahoo.sketches.tuple.ArrayOfDoublesSetOperationBuilder;
import com.yahoo.sketches.tuple.ArrayOfDoublesSketch;
import com.yahoo.sketches.tuple.ArrayOfDoublesUnion;
import com.yahoo.sketches.tuple.ArrayOfDoublesUpdatableSketch;
import com.yahoo.sketches.tuple.ArrayOfDoublesUpdatableSketchBuilder;

import io.druid.query.aggregation.Aggregator;
import io.druid.segment.FloatColumnSelector;
import io.druid.segment.ObjectColumnSelector;

/**
 * 
 * @author sunxin@rongcapital.cn
 *
 */
@SuppressWarnings({"rawtypes","unused"})
public class SketchUnionAggregator extends SketchAggregator {

    protected ArrayOfDoublesUnion union; 

    public SketchUnionAggregator(String name,ObjectColumnSelector selector,List<FloatColumnSelector> selectors,int size,int valuesCount) {
        super(name, selector, selectors, size, valuesCount);
        this.union = new ArrayOfDoublesSetOperationBuilder().setNominalEntries(size).setNumberOfValues(valuesCount).buildUnion();
    }

	@Override
	public void update(Object key) {
		updateUnion(union, key);
	}

    @Override
    public void reset() {
        union.reset();
    }

    @Override
    public Object get() {
        return union.getResult();
    }

    @Override
    public void close() {
    	union = null;
    }


}

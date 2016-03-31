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

package io.druid.query.aggregation.histogram;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 */
public class ApproximateHistogramHolder
{
  public static final int DEFAULT_HISTOGRAM_SIZE = 50;
  public static final int DEFAULT_BUCKET_SIZE = 7;

  // max size of the histogram (number of bincount/position pairs)
  int size;

  public float[] positions;
  public long[] bins;

  // used bincount
  int binCount;
  // min value that's been put into histogram
  float min;
  float max;
  // total number of values that have been put into histogram
  transient long count;

  // lower limit to maintain resolution
  // cutoff above which we merge bins is the difference of the limits / (size - 3)
  // so we'll set size = 203, lower limit = 0, upper limit = 10.00 if we don't want
  // to merge differences < 0.05
  transient float lowerLimit;
  transient float upperLimit;

  // use sign bit to indicate approximate bin and remaining bits for bin count
  protected static final long APPROX_FLAG_BIT = Long.MIN_VALUE;
  protected static final long COUNT_BITS = Long.MAX_VALUE;

  public ApproximateHistogramHolder(
      int size,
      float[] positions,
      long[] bins,
      int binCount,
      float min,
      float max,
      long count,
      float lowerLimit,
      float upperLimit
  )
  {
    Preconditions.checkArgument(positions.length == bins.length, "position and bin array must have same size");
    Preconditions.checkArgument(binCount <= size, "binCount must be less or equal to size");

    this.size = size;
    this.positions = positions;
    this.bins = bins;
    this.binCount = binCount;
    this.min = min;
    this.max = max;
    this.count = count;
    this.lowerLimit = lowerLimit;
    this.upperLimit = upperLimit;
  }

  public ApproximateHistogramHolder()
  {
    this(DEFAULT_HISTOGRAM_SIZE);
  }

  public ApproximateHistogramHolder(int size)
  {
    this(
        size,                    //size
        new float[size],         //positions
        new long[size],          //bins
        0,                       //binCount
        Float.POSITIVE_INFINITY, //min
        Float.NEGATIVE_INFINITY, //max
        0,                       //count
        Float.NEGATIVE_INFINITY, //lowerLimit
        Float.POSITIVE_INFINITY  //upperLimit
    );
  }

  public ApproximateHistogramHolder(int size, float lowerLimit, float upperLimit)
  {
    this(
        size,                    //size
        new float[size],         //positions
        new long[size],          //bins
        0,                       //binCount
        Float.POSITIVE_INFINITY, //min
        Float.NEGATIVE_INFINITY, //max
        0,                       //count
        lowerLimit,              //lowerLimit
        upperLimit               //upperLimit
    );
  }

  public ApproximateHistogramHolder(int binCount, float[] positions, long[] bins, float min, float max)
  {
    this(
        positions.length,        //size
        positions,               //positions
        bins,                    //bins
        binCount,                //binCount
        min,                     //min
        max,                     //max
        sumBins(bins, binCount), //count
        Float.NEGATIVE_INFINITY, //lowerLimit
        Float.POSITIVE_INFINITY  //upperLimit
    );
  }

  public ApproximateHistogramHolder(int size, int binCount, float[] positions, long[] bins, float min, float max)
  {
    this(
        size,        //size
        positions,               //positions
        bins,                    //bins
        binCount,                //binCount
        min,                     //min
        max,                     //max
        sumBins(bins, binCount), //count
        Float.NEGATIVE_INFINITY, //lowerLimit
        Float.POSITIVE_INFINITY  //upperLimit
    );
  }

  public void reset(int size)
  {
    this.size = size;
    this.binCount = 0;
    this.positions = new float[size];
    this.bins = new long[size];
    this.min = Float.POSITIVE_INFINITY;
    this.max = Float.NEGATIVE_INFINITY;
    this.count = 0;
    this.lowerLimit = Float.NEGATIVE_INFINITY;
    this.upperLimit = Float.POSITIVE_INFINITY;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ApproximateHistogramHolder)) {
      return false;
    }

    ApproximateHistogramHolder that = (ApproximateHistogramHolder) o;

    if (size != that.size) {
      return false;
    }
    if (binCount != that.binCount) {
      return false;
    }
    if (Float.compare(that.max, max) != 0) {
      return false;
    }
    if (Float.compare(that.min, min) != 0) {
      return false;
    }
    for (int i = 0; i < binCount; ++i) {
      if (positions[i] != that.positions[i]) {
        return false;
      }
    }
    for (int i = 0; i < binCount; ++i) {
      if (bins[i] != that.bins[i]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode()
  {
    int result = size;
    result = 31 * result + (positions != null ? ArrayUtils.hashCode(positions, 0, binCount) : 0);
    result = 31 * result + (bins != null ? ArrayUtils.hashCode(bins, 0, binCount) : 0);
    result = 31 * result + binCount;
    result = 31 * result + (min != +0.0f ? Float.floatToIntBits(min) : 0);
    result = 31 * result + (max != +0.0f ? Float.floatToIntBits(max) : 0);
    return result;
  }

  public long count() { return count; }

  public float min() { return min; }

  public float max() { return max; }

  public int binCount() { return binCount; }

  public int capacity() { return size; }

  public float[] positions() { return Arrays.copyOfRange(positions, 0, binCount); }

  public long[] bins()
  {
    long[] counts = new long[binCount];
    for (int i = 0; i < binCount; ++i) {
      counts[i] = bins[i] & COUNT_BITS;
    }
    return counts;
  }

  @Override
  public String toString()
  {
    return "ApproximateHistogramHolder{" +
           "size=" + size +
           ", lowerLimit=" + lowerLimit +
           ", upperLimit=" + upperLimit +
           ", positions=" + Arrays.toString(positions()) +
           ", bins=" + getBinsString() +
           ", binCount=" + binCount +
           ", min=" + min +
           ", max=" + max +
           ", count=" + count +
           '}';
  }

  public long getExactCount()
  {
    long exactCount = 0;
    for (int i = 0; i < binCount; ++i) {
      if ((bins[i] & APPROX_FLAG_BIT) == 0) {
        exactCount += (bins[i] & COUNT_BITS);
      }
    }
    return exactCount;
  }

  public float getMin() { return this.min;}

  public float getMax() { return this.max;}

  static long sumBins(long[] bins, int binCount)
  {
    long count = 0;
    for (int i = 0; i < binCount; ++i) {
      count += bins[i] & COUNT_BITS;
    }
    return count;
  }

  /**
   * @return a string representation of the actual bin counts
   */
  protected String getBinsString()
  {
    StringBuilder s = new StringBuilder();
    s.append('[');
    for (int i = 0; i < bins.length; ++i) {
      if (i > 0) {
        s.append(", ");
      }
      if ((bins[i] & APPROX_FLAG_BIT) != 0) {
        s.append("*");
      }
      s.append(bins[i] & COUNT_BITS);
    }
    s.append(']');
    return s.toString();
  }

  public void setLowerLimit(float lowerLimit)
  {
    this.lowerLimit = lowerLimit;
  }

  public void setUpperLimit(float upperLimit)
  {
    this.upperLimit = upperLimit;
  }

  /**
   * Adds the given value to the histogram
   *
   * @param value the value to be added
   */
  public void offer(float value)
  {
    // update min/max
    if (value < min) {
      min = value;
    }
    if (value > max) {
      max = value;
    }

    // initial value
    if (binCount == 0) {
      positions[0] = value;
      bins[0] = 1;
      count++;

      binCount++;
      return;
    }

    final int index = Arrays.binarySearch(positions, 0, binCount, value);

    if (index >= 0) {
      // we have an exact match, simply increase the count, but keep the approximate flag
      bins[index] = (bins[index] & APPROX_FLAG_BIT) | ((bins[index] & COUNT_BITS) + 1);
      count++;
      return;
    }

    // otherwise merge the value into a new or existing bin at the following position
    final int insertAt = -(index + 1);

    if (binCount < size) {
      // we have a spare slot, put the value into a new bin
      shiftRight(insertAt, binCount);

      positions[insertAt] = value;
      bins[insertAt] = 1;
      count++;

      binCount++;
      return;
    }

    // no more slots available merge the new value into and existing bin
    // or merge existing bins before inserting the new one

    int minPos = minDeltaIndex();
    float minDelta = minPos >= 0 ? positions[minPos + 1] - positions[minPos] : Float.MAX_VALUE;

    // determine the distance of new value to the nearest bins
    final float deltaRight = insertAt < binCount ? positions[insertAt] - value : Float.MAX_VALUE;
    final float deltaLeft = insertAt > 0 ? value - positions[insertAt - 1] : Float.MAX_VALUE;

    boolean mergeValue = false;
    if (deltaRight < minDelta) {
      minDelta = deltaRight;
      minPos = insertAt;
      mergeValue = true;
    }
    if (deltaLeft < minDelta) {
      minDelta = deltaLeft;
      minPos = insertAt - 1;
      mergeValue = true;
    }

    if (mergeValue) {
      // merge new value into an existing bin and set approximate flag
      final long k = bins[minPos] & COUNT_BITS;
      positions[minPos] = (positions[minPos] * k + value) / (k + 1);
      bins[minPos] = (k + 1) | APPROX_FLAG_BIT;
      count++;
    } else {
      // merge the closest bins together and insert new value as a separate bin
      mergeInsert(minPos, insertAt, value, 1);
    }
  }

  protected int minDeltaIndex()
  {
    // determine minimum distance between existing bins
    float minDelta = Float.MAX_VALUE;
    int minPos = -1;
    for (int i = 0; i < binCount - 1; ++i) {
      float delta = (positions[i + 1] - positions[i]);
      if (delta < minDelta) {
        minDelta = delta;
        minPos = i;
      }
    }
    return minPos;
  }

  /**
   * Merges the bin in the given position with the next bin
   *
   * @param index index of the bin to merge, index must satisfy 0 &lt;= index &lt; binCount - 1
   */
  protected void merge(final int index)
  {
    mergeInsert(index, -1, 0, 0);
  }

  /**
   * Merges the bin in the mergeAt position with the bin in position mergeAt+1
   * and simultaneously inserts the given bin (v,c) as a new bin at position insertAt
   *
   * @param mergeAt  index of the bin to be merged
   * @param insertAt index to insert the new bin at
   * @param v        bin position
   * @param c        bin count
   */
  protected void mergeInsert(final int mergeAt, int insertAt, final float v, final long c)
  {
    final long k0 = (bins[mergeAt] & COUNT_BITS);
    final long k1 = (bins[mergeAt + 1] & COUNT_BITS);
    final long sum = k0 + k1;

    // merge bin at given position with the next bin and set approximate flag
    positions[mergeAt] = (float) (((double) positions[mergeAt] * k0 + (double) positions[mergeAt + 1] * k1) / sum);
    bins[mergeAt] = sum | APPROX_FLAG_BIT;

    final int unusedIndex = mergeAt + 1;

    if (insertAt >= 0) {
      // use unused slot to shift array left or right and make space for the new bin to insert
      if (insertAt < unusedIndex) {
        shiftRight(insertAt, unusedIndex);
      } else if (insertAt >= unusedIndex) {
        shiftLeft(unusedIndex, insertAt - 1);
        insertAt--;
      }
      positions[insertAt] = v;
      bins[insertAt] = c;
      count++;
    } else {
      // simple merging of bins, shift everything left and free up the unused bin
      shiftLeft(unusedIndex, binCount - 1);
      binCount--;
    }
  }

  /**
   * Shifts the given range the histogram bins one slot to the right
   *
   * @param start index of the first bin to shift
   * @param end   index of the rightmost bin to shift into
   */
  protected void shiftRight(int start, int end)
  {
    float prevVal = positions[start];
    long prevCnt = bins[start];

    for (int i = start + 1; i <= end; ++i) {
      float tmpVal = positions[i];
      long tmpCnt = bins[i];

      positions[i] = prevVal;
      bins[i] = prevCnt;

      prevVal = tmpVal;
      prevCnt = tmpCnt;
    }
  }

  /**
   * Shifts the given range of histogram bins one slot to the left
   *
   * @param start index of the leftmost empty bin to shift into
   * @param end   index of the last bin to shift left
   */
  protected void shiftLeft(int start, int end)
  {
    for (int i = start; i < end; ++i) {
      positions[i] = positions[i + 1];
      bins[i] = bins[i + 1];
    }
  }

  public ApproximateHistogramHolder fold(ApproximateHistogramHolder h)
  {
    return fold(h, null, null, null);
  }

  public ApproximateHistogramHolder fold(
      ApproximateHistogramHolder h,
      float[] mergedPositions,
      long[] mergedBins,
      float[] deltas
  )
  {
    if (size == 0) {
      return copy(h);
    } else {
      return foldMin(h, mergedPositions, mergedBins, deltas);
    }
  }

  public ApproximateHistogramHolder foldFast(ApproximateHistogramHolder h)
  {
    return foldFast(h, null, null);
  }

  /**
   * @param h               histogram to be merged into the current histogram
   * @param mergedPositions temporary buffer of size greater or equal to this.capacity()
   * @param mergedBins      temporary buffer of size greater or equal to this.capacity()
   *
   * @return returns this histogram with h folded into it
   */
  public ApproximateHistogramHolder foldFast(ApproximateHistogramHolder h, float[] mergedPositions, long[] mergedBins)
  {
    if (size == 0) {
      return copy(h);
    } else {
      return foldRule(h, mergedPositions, mergedBins);
    }
  }

  /**
   * Copies histogram h into the current histogram.
   *
   * @param h ApproximateHistogramHolder to copy
   *
   * @return this histogram
   */
  public ApproximateHistogramHolder copy(ApproximateHistogramHolder h)
  {
    this.size = h.size;
    this.positions = new float[size];
    this.bins = new long[size];

    System.arraycopy(h.positions, 0, this.positions, 0, h.binCount);
    System.arraycopy(h.bins, 0, this.bins, 0, h.binCount);
    this.min = h.min;
    this.max = h.max;
    this.binCount = h.binCount;
    this.count = h.count;
    return this;
  }

  //approximate histogram solution using min heap to store location of min deltas
  protected ApproximateHistogramHolder foldMin(
      ApproximateHistogramHolder h,
      float[] mergedPositions,
      long[] mergedBins,
      float[] deltas
  )
  {
    // find common min / max
    float mergedMin = this.min < h.min ? this.min : h.min;
    float mergedMax = this.max > h.max ? this.max : h.max;
    long mergedCount = this.count + h.count;

    int maxSize = this.binCount + h.binCount;
    int[] next = new int[maxSize];
    int[] prev = new int[maxSize];

    // use preallocated arrays if passed
    if (mergedPositions == null || mergedBins == null || deltas == null) {
      mergedPositions = new float[maxSize];
      mergedBins = new long[maxSize];
      deltas = new float[maxSize];
    } else {
      Preconditions.checkArgument(
          mergedPositions.length >= maxSize,
          "temp buffer [mergedPositions] too small: length must be at least [%d], got [%d]",
          maxSize,
          mergedPositions.length
      );
      Preconditions.checkArgument(
          mergedBins.length >= maxSize,
          "temp buffer [mergedBins] too small: length must be at least [%d], got [%d]",
          maxSize,
          mergedPositions.length
      );
      Preconditions.checkArgument(
          deltas.length >= maxSize,
          "temp buffer [deltas] too small: length must be at least [%d], got [%d]",
          maxSize,
          mergedPositions.length
      );
    }

    int mergedBinCount = combineBins(
        this.binCount, this.positions, this.bins, h.binCount, h.positions, h.bins,
        mergedPositions, mergedBins, deltas
    );
    if (mergedBinCount == 0) {
      return this;
    }

    // determine how many bins to merge
    int numMerge = mergedBinCount - this.size;
    if (numMerge <= 0) {
      this.positions = mergedPositions;
      this.bins = mergedBins;
      this.binCount = mergedBinCount;
      this.min = mergedMin;
      this.max = mergedMax;
      this.count = mergedCount;
      return this;
    }

    // perform the required number of merges
    mergeBins(mergedBinCount, mergedPositions, mergedBins, deltas, numMerge, next, prev);

    // copy merged values
    int i = 0;
    int k = 0;
    while (i < mergedBinCount) {
      this.positions[k] = mergedPositions[i];
      this.bins[k] = mergedBins[i];
      ++k;
      i = next[i];
    }
    this.binCount = mergedBinCount - numMerge;
    this.min = mergedMin;
    this.max = mergedMax;
    this.count = mergedCount;
    return this;
  }

  protected ApproximateHistogramHolder foldRule(
      ApproximateHistogramHolder h,
      float[] mergedPositions,
      long[] mergedBins
  )
  {
    // ruleCombine bins requires at least one bin
    if (h.binCount == 0) {
      return this;
    }

    // find common min / max
    float mergedMin = this.min < h.min ? this.min : h.min;
    float mergedMax = this.max > h.max ? this.max : h.max;
    long mergedCount = this.count + h.count;
    this.min = mergedMin;
    this.max = mergedMax;

    // use preallocated arrays if passed
    if (mergedPositions == null) {
      mergedPositions = new float[this.size];
      mergedBins = new long[this.size];
    }

    int mergedBinCount;
    if (this.binCount + h.binCount <= this.size) {
      // no need to merge bins
      mergedBinCount = combineBins(
          this.binCount, this.positions, this.bins,
          h.binCount, h.positions, h.bins,
          mergedPositions, mergedBins, null
      );
    } else {
      mergedBinCount = ruleCombineBins(
          this.binCount, this.positions, this.bins, h.binCount, h.positions, h.bins,
          mergedPositions, mergedBins
      );
    }
    for (int i = 0; i < mergedBinCount; ++i) {
      this.positions[i] = mergedPositions[i];
      this.bins[i] = mergedBins[i];
    }

    this.binCount = mergedBinCount;
    this.count = mergedCount;

    return this;
  }

  protected int ruleCombineBins(
      int leftBinCount, float[] leftPositions, long[] leftBins,
      int rightBinCount, float[] rightPositions, long[] rightBins,
      float[] mergedPositions, long[] mergedBins
  )
  {
    final float cutoff;
    // assumes binCount is greater than one for both histograms
    // if upper and lower limits are set, we use the first and last used values of the arrays
    // for information below and above the limits, respectively
    if (this.upperLimit != Float.POSITIVE_INFINITY && this.lowerLimit != Float.NEGATIVE_INFINITY) {
      cutoff = (this.upperLimit - this.lowerLimit) / (size - 2 - 1);
    } else {
      if (this.upperLimit != Float.POSITIVE_INFINITY) {
        cutoff = (this.upperLimit - this.min) / (size - 2);
      } else if (this.lowerLimit != Float.NEGATIVE_INFINITY) {
        cutoff = (this.max - this.lowerLimit) / (size - 2);
      } else {
        cutoff = (this.max - this.min) / (size - 1);
      }
    }

    float lowerPosition = 0f;
    long lowerBin = 0;
    float upperPosition = 0f;
    long upperBin = 0;

    int j = 0;
    int k = 0;
    int pos = 0;

    // continuously merge the left histogram below the lower limit
    while (j != leftBinCount) {
      final float m1 = leftPositions[j];
      if (m1 < lowerLimit) {
        final long k1 = leftBins[j] & COUNT_BITS;
        float delta = (m1 - lowerPosition);
        final long k0 = lowerBin & COUNT_BITS;
        final long sum = k0 + k1;
        final float w = (float) k0 / (float) sum;
        lowerPosition = -delta * w + m1;
        // set approximate flag
        lowerBin = sum | APPROX_FLAG_BIT;
        ++j;
      } else {
        break;
      }
    }

    // continuously merge the right histogram below the lower limit
    while (k != rightBinCount) {
      final float m1 = rightPositions[k];
      if (m1 < lowerLimit) {
        final long k1 = rightBins[k] & COUNT_BITS;
        float delta = (m1 - lowerPosition);
        final long k0 = lowerBin & COUNT_BITS;
        final long sum = k0 + k1;
        final float w = (float) k0 / (float) sum;
        lowerPosition = -delta * w + m1;
        // set approximate flag
        lowerBin = sum | APPROX_FLAG_BIT;
        ++k;
      } else {
        break;
      }
    }

    // if there are values below the lower limit, store them in array position 0
    if ((lowerBin & COUNT_BITS) > 0) {
      mergedPositions[0] = lowerPosition;
      mergedBins[0] = lowerBin;
      pos = 1;
    }

    // if there are values below the lower limit, fill in array position 1
    // else array position 0
    while (j != leftBinCount || k != rightBinCount) {
      if (j != leftBinCount && (k == rightBinCount || leftPositions[j] < rightPositions[k])) {
        mergedPositions[pos] = leftPositions[j];
        mergedBins[pos] = leftBins[j];
        ++j;
        break;
      } else {
        mergedPositions[pos] = rightPositions[k];
        mergedBins[pos] = rightBins[k];
        ++k;
        break;
      }
    }

    while (j != leftBinCount || k != rightBinCount) {
      if (j != leftBinCount && (k == rightBinCount || leftPositions[j] < rightPositions[k])) {
        final float m1 = leftPositions[j];
        final long k1 = leftBins[j] & COUNT_BITS;

        // above the upper limit gets merged continuously in the left histogram
        if (m1 > upperLimit) {
          float delta = (m1 - upperPosition);
          final long k0 = upperBin & COUNT_BITS;
          final long sum = k0 + k1;
          final float w = (float) k0 / (float) sum;
          upperPosition = -delta * w + m1;
          // set approximate flag
          upperBin = sum | APPROX_FLAG_BIT;
          ++j;
          continue;
        }

        final float delta = (m1 - mergedPositions[pos]);

        if (delta <= cutoff) {
          final long k0 = mergedBins[pos] & COUNT_BITS;
          final long sum = k0 + k1;
          final float w = (float) k0 / (float) sum;
          mergedPositions[pos] = -delta * w + m1;
          // set approximate flag
          mergedBins[pos] = sum | APPROX_FLAG_BIT;
        } else {
          ++pos;
          mergedPositions[pos] = m1;
          mergedBins[pos] = k1;
        }
        ++j;
      } else {
        final float m1 = rightPositions[k];
        final long k1 = rightBins[k] & COUNT_BITS;

        // above the upper limit gets merged continuously in the right histogram
        if (m1 > upperLimit) {
          float delta = (m1 - upperPosition);
          final long k0 = upperBin & COUNT_BITS;
          final long sum = k0 + k1;
          final float w = (float) k0 / (float) sum;
          upperPosition = -delta * w + m1;
          // set approximate flag
          upperBin = sum | APPROX_FLAG_BIT;
          ++k;
          continue;
        }

        final float delta = (m1 - mergedPositions[pos]);

        if (delta <= cutoff) {
          final long k0 = mergedBins[pos] & COUNT_BITS;
          final long sum = k0 + k1;
          final float w = (float) k0 / (float) sum;
          mergedPositions[pos] = -delta * w + m1;
          mergedBins[pos] = sum | APPROX_FLAG_BIT;
        } else {
          ++pos;
          mergedPositions[pos] = m1;
          mergedBins[pos] = k1;
        }
        ++k;
      }
    }

    if ((upperBin & COUNT_BITS) > 0) {
      ++pos;
      mergedPositions[pos] = upperPosition;
      mergedBins[pos] = upperBin;
    }

    return pos + 1;
  }


  /**
   * mergeBins performs the given number of bin merge operations on the given histogram
   * <p/>
   * It repeatedly merges the two closest bins until it has performed the requested number of merge operations.
   * Merges are done in-place and unused bins have unknown state
   * <p/>
   * next / prev maintains a doubly-linked list of valid bin indices into the mergedBins array.
   * <p/>
   * Fast operation is achieved by building a min-heap of the deltas as opposed to repeatedly
   * scanning the array of deltas to find the minimum. A reverse index into the heap is maintained
   * to allow deleting and updating of specific deltas.
   * <p/>
   * next and prev arrays are used to maintain indices to the previous / next valid bin from a given bin index
   * <p/>
   * Its effect is equivalent to running the following code:
   * <p/>
   * <pre>
   *   ApproximateHistogramHolder merged = new ApproximateHistogramHolder(mergedBinCount, mergedPositions, mergedBins);
   *
   *   int targetSize = merged.binCount() - numMerge;
   *   while(merged.binCount() > targetSize) {
   *     merged.merge(merged.minDeltaIndex());
   *   }
   * </pre>
   *
   * @param mergedBinCount
   * @param mergedPositions
   * @param mergedBins
   * @param deltas
   * @param numMerge
   * @param next
   * @param prev
   *
   * @return the last valid index into the mergedPositions and mergedBins arrays
   */
  static void mergeBins(
      int mergedBinCount, float[] mergedPositions,
      long[] mergedBins,
      float[] deltas,
      int numMerge,
      int[] next,
      int[] prev
  )
  {
    // repeatedly search for two closest bins, merge them and update the corresponding deltas

    // maintain index to the last valid bin
    int lastValidIndex = mergedBinCount - 1;

    // initialize prev / next lookup arrays
    for (int i = 0; i < mergedBinCount; ++i) {
      next[i] = i + 1;
    }
    for (int i = 0; i < mergedBinCount; ++i) {
      prev[i] = i - 1;
    }

    // initialize min-heap of deltas and the reverse index into the heap
    int heapSize = mergedBinCount - 1;
    int[] heap = new int[heapSize];
    int[] reverseIndex = new int[heapSize];
    for (int i = 0; i < heapSize; ++i) {
      heap[i] = i;
    }
    for (int i = 0; i < heapSize; ++i) {
      reverseIndex[i] = i;
    }

    heapify(heap, reverseIndex, heapSize, deltas);

    {
      int i = 0;
      while (i < numMerge) {
        // find the smallest delta within the range used for bins

        // pick minimum delta by scanning array
        //int currentIndex = minIndex(deltas, lastValidIndex);

        // pick minimum delta index using min-heap
        int currentIndex = heap[0];

        final int nextIndex = next[currentIndex];
        final int prevIndex = prev[currentIndex];

        final long k0 = mergedBins[currentIndex] & COUNT_BITS;
        final long k1 = mergedBins[nextIndex] & COUNT_BITS;
        final float m0 = mergedPositions[currentIndex];
        final float m1 = mergedPositions[nextIndex];
        final float d1 = deltas[nextIndex];

        final long sum = k0 + k1;
        final float w = (float) k0 / (float) sum;

        // merge bin at given position with the next bin
        final float mm0 = (m0 - m1) * w + m1;

        mergedPositions[currentIndex] = mm0;
        //mergedPositions[nextIndex] = Float.MAX_VALUE; // for debugging

        mergedBins[currentIndex] = sum | APPROX_FLAG_BIT;
        //mergedBins[nextIndex] = -1; // for debugging

        // update deltas and min-heap
        if (nextIndex == lastValidIndex) {
          // merged bin is the last => remove the current bin delta from the heap
          heapSize = heapDelete(heap, reverseIndex, heapSize, reverseIndex[currentIndex], deltas);

          //deltas[currentIndex] = Float.MAX_VALUE; // for debugging
        } else {
          // merged bin is not the last => remove the merged bin delta from the heap
          heapSize = heapDelete(heap, reverseIndex, heapSize, reverseIndex[nextIndex], deltas);

          // updated current delta
          deltas[currentIndex] = m1 - mm0 + d1;

          // updated delta is necessarily larger than existing one, therefore we only need to push it down the heap
          siftDown(heap, reverseIndex, reverseIndex[currentIndex], heapSize - 1, deltas);
        }

        if (prevIndex >= 0) {
          // current bin is not the first, therefore update the previous bin delta
          deltas[prevIndex] = mm0 - mergedPositions[prevIndex];

          // updated previous bin delta is necessarily larger than its existing value => push down the heap
          siftDown(heap, reverseIndex, reverseIndex[prevIndex], heapSize - 1, deltas);
        }

        // mark the merged bin as invalid
        // deltas[nextIndex] = Float.MAX_VALUE; // for debugging

        // update last valid index if we merged the last bin
        if (nextIndex == lastValidIndex) {
          lastValidIndex = currentIndex;
        }

        next[currentIndex] = next[nextIndex];
        if (nextIndex < lastValidIndex) {
          prev[next[nextIndex]] = currentIndex;
        }

        ++i;
      }
    }
  }

  /**
   * Builds a min-heap and a reverseIndex into the heap from the given array of values
   *
   * @param heap         min-heap stored as indices into the array of values
   * @param reverseIndex reverse index from the array of values into the heap
   * @param count        current size of the heap
   * @param values       values to be stored in the heap
   */
  private static void heapify(int[] heap, int[] reverseIndex, int count, float[] values)
  {
    int start = (count - 2) / 2;
    while (start >= 0) {
      siftDown(heap, reverseIndex, start, count - 1, values);
      start--;
    }
  }

  /**
   * Rebalances the min-heap by pushing values from the top down and simultaneously updating the reverse index
   *
   * @param heap         min-heap stored as indices into the array of values
   * @param reverseIndex reverse index from the array of values into the heap
   * @param start        index to start re-balancing from
   * @param end          index to stop re-balancing at
   * @param values       values stored in the heap
   */
  private static void siftDown(int[] heap, int[] reverseIndex, int start, int end, float[] values)
  {
    int root = start;
    while (root * 2 + 1 <= end) {
      int child = root * 2 + 1;
      int swap = root;
      if (values[heap[swap]] > values[heap[child]]) {
        swap = child;
      }
      if (child + 1 <= end && values[heap[swap]] > values[heap[child + 1]]) {
        swap = child + 1;
      }
      if (swap != root) {
        // swap
        int tmp = heap[swap];
        heap[swap] = heap[root];
        heap[root] = tmp;

        // heap index from delta index
        reverseIndex[heap[swap]] = swap;
        reverseIndex[heap[root]] = root;

        root = swap;
      } else {
        return;
      }
    }
  }

  /**
   * Deletes an item from the min-heap and updates the reverse index
   *
   * @param heap         min-heap stored as indices into the array of values
   * @param reverseIndex reverse index from the array of values into the heap
   * @param count        current size of the heap
   * @param heapIndex    index of the item to be deleted
   * @param values       values stored in the heap
   */
  private static int heapDelete(int[] heap, int[] reverseIndex, int count, int heapIndex, float[] values)
  {
    int end = count - 1;

    reverseIndex[heap[heapIndex]] = -1;

    heap[heapIndex] = heap[end];
    reverseIndex[heap[heapIndex]] = heapIndex;

    end--;
    siftDown(heap, reverseIndex, heapIndex, end, values);
    return count - 1;
  }

  private static int minIndex(float[] deltas, int lastValidIndex)
  {
    int minIndex = -1;
    float min = Float.MAX_VALUE;
    for (int k = 0; k < lastValidIndex; ++k) {
      float value = deltas[k];
      if (value < min) {
        minIndex = k;
        min = value;
      }
    }
    return minIndex;
  }

  /**
   * Combines two sets of histogram bins using merge-sort and computes the delta between consecutive bin positions.
   * Duplicate bins are merged together.
   *
   * @param leftBinCount
   * @param leftPositions
   * @param leftBins
   * @param rightBinCount
   * @param rightPositions
   * @param rightBins
   * @param mergedPositions array to store the combined bin positions (size must be at least leftBinCount + rightBinCount)
   * @param mergedBins      array to store the combined bin counts (size must be at least leftBinCount + rightBinCount)
   * @param deltas          deltas between consecutive bin positions in the merged bins (size must be at least leftBinCount + rightBinCount)
   *
   * @return the number of combined bins
   */
  static int combineBins(
      int leftBinCount, float[] leftPositions, long[] leftBins,
      int rightBinCount, float[] rightPositions, long[] rightBins,
      float[] mergedPositions, long[] mergedBins, float[] deltas
  )
  {
    int i = 0;
    int j = 0;
    int k = 0;
    while (j < leftBinCount || k < rightBinCount) {
      if (j < leftBinCount && (k == rightBinCount || leftPositions[j] < rightPositions[k])) {
        mergedPositions[i] = leftPositions[j];
        mergedBins[i] = leftBins[j];
        ++j;
      } else if (k < rightBinCount && (j == leftBinCount || leftPositions[j] > rightPositions[k])) {
        mergedPositions[i] = rightPositions[k];
        mergedBins[i] = rightBins[k];
        ++k;
      } else {
        // combine overlapping bins
        mergedPositions[i] = leftPositions[j];
        mergedBins[i] = leftBins[j] + rightBins[k];
        ++j;
        ++k;
      }
      if (deltas != null && i > 0) {
        deltas[i - 1] = mergedPositions[i] - mergedPositions[i - 1];
      }
      ++i;
    }
    return i;
  }

  /**
   * Returns the approximate number of items less than or equal to b in the histogram
   *
   * @param b the cutoff
   *
   * @return the approximate number of items less than or equal to b
   */
  public double sum(final float b)
  {
    if (b < min) {
      return 0;
    }
    if (b >= max) {
      return count;
    }

    int index = Arrays.binarySearch(positions, 0, binCount, b);
    boolean exactMatch = index >= 0;
    index = exactMatch ? index : -(index + 1);

    // we want positions[index] <= b < positions[index+1]
    if (!exactMatch) {
      index--;
    }

    final boolean outerLeft = index < 0;
    final boolean outerRight = index >= (binCount - 1);

    final long m0 = outerLeft ? 0 : (bins[index] & COUNT_BITS);
    final long m1 = outerRight ? 0 : (bins[index + 1] & COUNT_BITS);
    final double p0 = outerLeft ? min : positions[index];
    final double p1 = outerRight ? max : positions[index + 1];
    final boolean exact0 = (!outerLeft && (bins[index] & APPROX_FLAG_BIT) == 0);
    final boolean exact1 = (!outerRight && (bins[index + 1] & APPROX_FLAG_BIT) == 0);

    // handle case when p0 = p1, which happens if the first bin = min or the last bin = max
    final double l = (p1 == p0) ? 0 : (b - p0) / (p1 - p0);

    // don't include exact counts in the trapezoid calculation
    long tm0 = m0;
    long tm1 = m1;
    if (exact0) {
      tm0 = 0;
    }
    if (exact1) {
      tm1 = 0;
    }
    final double mb = tm0 + (tm1 - tm0) * l;
    double s = 0.5 * (tm0 + mb) * l;

    for (int i = 0; i < index; ++i) {
      s += (bins[i] & COUNT_BITS);
    }

    // add full bin count if left bin count is exact
    if (exact0) {
      return (s + m0);
    }

    // otherwise add only the left half of the bin
    else {
      return (s + 0.5 * m0);
    }
  }

  /**
   * Returns the approximate quantiles corresponding to the given probabilities.
   * probabilities = [.5f] returns [median]
   * probabilities = [.25f, .5f, .75f] returns the quartiles, [25%ile, median, 75%ile]
   *
   * @param probabilities array of probabilities
   *
   * @return an array of length probabilities.length representing the the approximate sample quantiles
   * corresponding to the given probabilities
   */

  public float[] getQuantiles(float[] probabilities)
  {
    for (float p : probabilities) {
      Preconditions.checkArgument(0 < p & p < 1, "quantile probabilities must be strictly between 0 and 1");
    }

    float[] quantiles = new float[probabilities.length];
    Arrays.fill(quantiles, Float.NaN);

    if (this.count() == 0) {
      return quantiles;
    }

    final long[] bins = this.bins();

    for (int j = 0; j < probabilities.length; ++j) {
      final double s = probabilities[j] * this.count();

      int i = 0;
      int sum = 0;
      int k = 1;
      long count = 0;
      while (k <= this.binCount()) {
        count = bins[k - 1];
        if (sum + count > s) {
          i = k - 1;
          break;
        } else {
          sum += count;
        }
        ++k;
      }

      if (i == 0) {
        quantiles[j] = this.min();
      } else {
        final double d = s - sum;
        final double c = -2 * d;
        final long a = bins[i] - bins[i - 1];
        final long b = 2 * bins[i - 1];
        double z = 0;
        if (a == 0) {
          z = -c / b;
        } else {
          z = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
        }
        final double uj = this.positions[i - 1] + (this.positions[i] - this.positions[i - 1]) * z;
        quantiles[j] = (float) uj;
      }
    }

    return quantiles;
  }

  /**
   * Computes a visual representation of the approximate histogram with bins laid out according to the given breaks
   *
   * @param breaks breaks defining the histogram bins
   *
   * @return visual representation of the histogram
   */
  public Histogram toHistogram(final float[] breaks)
  {
    final double[] approximateBins = new double[breaks.length - 1];

    double prev = sum(breaks[0]);
    for (int i = 1; i < breaks.length; ++i) {
      double s = sum(breaks[i]);
      approximateBins[i - 1] = (float) (s - prev);
      prev = s;
    }

    return new Histogram(breaks, approximateBins);
  }

  /**
   * Computes a visual representation of the approximate histogram with a given number of equal-sized bins
   *
   * @param size number of equal-sized bins to divide the histogram into
   *
   * @return visual representation of the histogram
   */
  public Histogram toHistogram(int size)
  {
    Preconditions.checkArgument(size > 1, "histogram size must be greater than 1");

    float[] breaks = new float[size + 1];
    float delta = (max - min) / (size - 1);
    breaks[0] = min - delta;
    for (int i = 1; i < breaks.length - 1; ++i) {
      breaks[i] = breaks[i - 1] + delta;
    }
    breaks[breaks.length - 1] = max;
    return toHistogram(breaks);
  }

  /**
   * Computes a visual representation given an initial breakpoint, offset, and a bucket size.
   *
   * @param bucketSize the size of each bucket
   * @param offset     the location of one breakpoint
   *
   * @return visual representation of the histogram
   */
  public Histogram toHistogram(final float bucketSize, final float offset)
  {
    final float minFloor = (float) Math.floor((min() - offset) / bucketSize) * bucketSize + offset;
    final float lowerLimitFloor = (float) Math.floor((lowerLimit - offset) / bucketSize) * bucketSize + offset;
    final float firstBreak = Math.max(minFloor, lowerLimitFloor);

    final float maxCeil = (float) Math.ceil((max() - offset) / bucketSize) * bucketSize + offset;
    final float upperLimitCeil = (float) Math.ceil((upperLimit - offset) / bucketSize) * bucketSize + offset;
    final float lastBreak = Math.min(maxCeil, upperLimitCeil);

    final float cutoff = 0.1f;

    final ArrayList<Float> breaks = new ArrayList<Float>();

    // to deal with left inclusivity when the min is the same as a break
    final float bottomBreak = minFloor - bucketSize;
    if (bottomBreak != firstBreak && (sum(firstBreak) - sum(bottomBreak) > cutoff)) {
      breaks.add(bottomBreak);
    }

    float left = firstBreak;
    boolean leftSet = false;

    //the + bucketSize / 10 is because floating point addition is always slightly incorrect and so we need to account for that
    while (left + bucketSize <= lastBreak + (bucketSize / 10)) {
      final float right = left + bucketSize;

      if (sum(right) - sum(left) > cutoff) {
        if (!leftSet) {
          breaks.add(left);
        }
        breaks.add(right);
        leftSet = true;
      } else {
        leftSet = false;
      }

      left = right;
    }

    if (breaks.get(breaks.size() - 1) != maxCeil && (sum(maxCeil) - sum(breaks.get(breaks.size() - 1)) > cutoff)) {
      breaks.add(maxCeil);
    }

    return toHistogram(Floats.toArray(breaks));
  }

  public int getDenseStorageSize()
  {
    return Ints.BYTES * 2 + Floats.BYTES * size + Longs.BYTES * size + Floats.BYTES * 2;
  }

  public int getSparseStorageSize()
  {
    return Ints.BYTES * 2 + Floats.BYTES * binCount + Longs.BYTES * binCount + Floats.BYTES * 2;
  }

  /**
   * Constructs an Approximate Histogram object from the given byte-array representation
   *
   * @param bytes byte array to construct an ApproximateHistogram from
   *
   * @return ApproximateHistogram constructed from the given byte array
   */
  public ApproximateHistogramHolder fromBytes(byte[] bytes)
  {
    return fromBytes(ByteBuffer.wrap(bytes));
  }

  /**
   * Constructs an ApproximateHistogram object from the given byte-buffer representation
   *
   * @param buf ByteBuffer to construct an ApproximateHistogram from
   *
   * @return ApproximateHistogram constructed from the given ByteBuffer
   */
  public ApproximateHistogramHolder fromBytes(ByteBuffer buf)
  {
    throw new UnsupportedOperationException("fromBytes");
  }

  /**
   * Returns a byte-array representation of this ApproximateHistogram object
   *
   * @return byte array representation
   */

  public byte[] toBytes()
  {
    throw new UnsupportedOperationException("toBytes");
  }

  public int getMaxStorageSize()
  {
    return getDenseStorageSize();
  }
}

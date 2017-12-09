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

package io.druid.query.aggregation.datasketches.tuple;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Random;

import org.apache.commons.codec.binary.Base64;

import com.yahoo.sketches.tuple.ArrayOfDoublesUpdatableSketch;
import com.yahoo.sketches.tuple.ArrayOfDoublesUpdatableSketchBuilder;

//This is used for generating test data for ArrayOfDoublesSketchAggregationTest
public class GenerateTestData
{

  public static void main(String[] args) throws Exception
  {
    generateSketches();
    generateBucketTestData();
  }

  static void generateSketches() throws Exception
  {
    try (BufferedWriter out = new BufferedWriter(new FileWriter("array_of_doubles_sketch_data.tsv"))) {
      Random rand = new Random();
      int key = 0;
      for (int i = 0; i < 20; i++) {
        ArrayOfDoublesUpdatableSketch sketch = new ArrayOfDoublesUpdatableSketchBuilder().setNominalEntries(1024)
            .build();
        sketch.update(key++, new double[] {1});
        sketch.update(key++, new double[] {1});
        out.write("2015010101");
        out.write('\t');
        out.write("product_" + (rand.nextInt(10) + 1));
        out.write('\t');
        out.write(Base64.encodeBase64String(sketch.compact().toByteArray()));
        out.newLine();
      }
      out.close();
    }
  }

  // Data for two buckets: test and control.
  // Each user ID is associated with a numeric parameter
  // randomly drawn from normal distribution.
  // Buckets have different means.
  static void generateBucketTestData() throws Exception
  {
    double meanTest = 10;
    double meanControl = 10.2;
    try (BufferedWriter out = new BufferedWriter(new FileWriter("bucket_test_data.tsv"))) {
      Random rand = new Random();
      for (int i = 0; i < 1000; i++) {
        writeBucketTestRecord(out, "test", i, rand.nextGaussian() + meanTest);
        writeBucketTestRecord(out, "control", i, rand.nextGaussian() + meanControl);
      }
    }
  }

  static void writeBucketTestRecord(BufferedWriter out, String label, int id, double parameter) throws Exception
  {
    out.write("20170101");
    out.write("\t");
    out.write(label);
    out.write("\t");
    out.write(Integer.toString(id));
    out.write("\t");
    out.write(Double.toString(parameter));
    out.newLine();
  }

}

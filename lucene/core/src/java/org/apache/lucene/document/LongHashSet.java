/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.document;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.packed.PackedInts;

/** Set of longs, optimized for docvalues usage */
final class LongHashSet implements Accountable {
  private static final long BASE_RAM_BYTES =
      RamUsageEstimator.shallowSizeOfInstance(LongHashSet.class);

  private static final long MISSING = Long.MIN_VALUE;

  final long[] table;
  final int mask;
  final boolean hasMissingValue;
  final int size;
  /** minimum value in the set, or Long.MAX_VALUE for an empty set */
  final long minValue;
  /** maximum value in the set, or Long.MIN_VALUE for an empty set */
  final long maxValue;

  /** Construct a set. Values must be in sorted order. */
  LongHashSet(long[] values) {
    int tableSize = Math.toIntExact(values.length * 3L / 2);
    tableSize = 1 << PackedInts.bitsRequired(tableSize); // make it a power of 2
    assert tableSize >= values.length * 3L / 2;
    table = new long[tableSize];
    Arrays.fill(table, MISSING);
    mask = tableSize - 1;
    boolean hasMissingValue = false;
    int size = 0;
    long previousValue = Long.MIN_VALUE; // for assert
    for (long value : values) {
      if (value == MISSING || add(value)) {
        if (value == MISSING) {
          hasMissingValue = true;
        }
        ++size;
      }
      assert value >= previousValue : "values must be provided in sorted order";
      previousValue = value;
    }
    this.hasMissingValue = hasMissingValue;
    this.size = size;
    this.minValue = values.length == 0 ? Long.MAX_VALUE : values[0];
    this.maxValue = values.length == 0 ? Long.MIN_VALUE : values[values.length - 1];
  }

  private boolean add(long l) {
    assert l != MISSING;
    final int slot = Long.hashCode(l) & mask;
    for (int i = slot; ; i = (i + 1) & mask) {
      if (table[i] == MISSING) {
        table[i] = l;
        return true;
      } else if (table[i] == l) {
        // already added
        return false;
      }
    }
  }

  /**
   * check for membership in the set.
   *
   * <p>You should use {@link #minValue} and {@link #maxValue} to guide/terminate iteration before
   * calling this.
   */
  boolean contains(long l) {
    if (l == MISSING) {
      return hasMissingValue;
    }
    final int slot = Long.hashCode(l) & mask;
    for (int i = slot; ; i = (i + 1) & mask) {
      if (table[i] == MISSING) {
        return false;
      } else if (table[i] == l) {
        return true;
      }
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(size, minValue, maxValue, mask, hasMissingValue, Arrays.hashCode(table));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof LongHashSet) {
      LongHashSet that = (LongHashSet) obj;
      return size == that.size
          && minValue == that.minValue
          && maxValue == that.maxValue
          && mask == that.mask
          && hasMissingValue == that.hasMissingValue
          && Arrays.equals(table, that.table);
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[");
    boolean seenValue = false;
    if (hasMissingValue) {
      sb.append(MISSING);
      seenValue = true;
    }
    for (long v : table) {
      if (v != MISSING) {
        if (seenValue) {
          sb.append(", ");
        }
        sb.append(v);
        seenValue = true;
      }
    }
    sb.append("]");
    return sb.toString();
  }

  /** number of elements in the set */
  int size() {
    return size;
  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES + RamUsageEstimator.sizeOfObject(table);
  }

  // for testing only
  Set<Long> toSet() {
    Set<Long> set = new HashSet<>();
    if (hasMissingValue) {
      set.add(MISSING);
    }
    for (long v : table) {
      if (v != MISSING) {
        set.add(v);
      }
    }
    return set;
  }
}
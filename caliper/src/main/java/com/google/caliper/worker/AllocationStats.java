/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.caliper.worker;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.caliper.model.Measurement;
import com.google.caliper.model.Value;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;

import java.util.Collection;

/**
 * A set of statistics about the allocations performed by a benchmark method.
 */
class AllocationStats {
  private final int allocationCount;
  private final long allocationSize;
  private final int reps;
  private final ImmutableMultiset<Allocation> allocations;
  
  /**
   * Constructs a new {@link AllocationStats} with the given number of allocations 
   * ({@code allocationCount}), cumulative size of the allocations ({@code allocationSize}) and the
   * number of {@code reps} passed to the benchmark method.
   */
  AllocationStats(int allocationCount, long allocationSize, int reps) {
    this(allocationCount, allocationSize, reps, ImmutableMultiset.<Allocation>of());
  }
  
  /**
   * Constructs a new {@link AllocationStats} with the given allocations and the number of 
   * {@code reps} passed to the benchmark method.
   */
  AllocationStats(Collection<Allocation> allocations, int reps) {
    this(allocations.size(), Allocation.getTotalSize(allocations), reps, 
        ImmutableMultiset.copyOf(allocations));
  }

  private AllocationStats(int allocationCount, long allocationSize, int reps, 
      Multiset<Allocation> allocations) {
    checkArgument(allocationCount >= 0, "allocationCount (%s) was negative", allocationCount);
    this.allocationCount = allocationCount;
    checkArgument(allocationSize >= 0, "allocationSize (%s) was negative", allocationSize);
    this.allocationSize = allocationSize;
    checkArgument(reps >= 0, "reps (%s) was negative", reps);
    this.reps = reps;
    this.allocations = Multisets.copyHighestCountFirst(allocations);
  }
  
  int getAllocationCount() {
    return allocationCount;
  }
  
  long getAllocationSize() {
    return allocationSize;
  }
  
  /**
   * Computes and returns the difference between this measurement and the given 
   * {@code baseline} measurement. The {@code baseline} measurement must have a lower weight 
   * (fewer reps) than this measurement.
   */
  AllocationStats minus(AllocationStats baseline) {
    for (Entry<Allocation> entry : baseline.allocations.entrySet()) {
      int superCount = allocations.count(entry.getElement());
      if (superCount < entry.getCount()) {
        throw new IllegalStateException(
            String.format("Your benchmark appears to have non-deterministic allocation behavior. "
                + "Observed %d instance(s) of %s in the baseline but only %d in the actual "
                + "measurement", 
                entry.getCount(),
                entry.getElement(), 
                superCount));
      }
    }
    try {
      return new AllocationStats(allocationCount - baseline.allocationCount,
            allocationSize - baseline.allocationSize,
            reps - baseline.reps,
            Multisets.difference(allocations, baseline.allocations));
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(String.format(
          "Your benchmark appears to have non-deterministic allocation behavior. The difference "
          + "between the baseline %s and the measurement %s is invalid. Consider enabling "
          + "instrument.allocation.options.trackAllocations to get a more specific error message.", 
          baseline, this), e);
    }
  }
  
  /**
   * Returns a list of {@link Measurement measurements} based on this collection of stats.
   */
  ImmutableList<Measurement> toMeasurements() {
    for (Entry<Allocation> entry : allocations.entrySet()) {
      double allocsPerRep = ((double) entry.getCount()) / reps;
      System.out.printf("Allocated %f allocs per rep of %s%n", allocsPerRep, entry.getElement());
    }
    return ImmutableList.of(
        new Measurement.Builder()
            .value(Value.create(allocationCount, ""))
            .description("objects")
            .weight(reps)
            .build(),
        new Measurement.Builder()
            .value(Value.create(allocationSize, "B"))
            .weight(reps)
            .description("bytes")
            .build());
  }
  
  @Override public String toString() {
    return Objects.toStringHelper(this)
        .add("allocationCount", allocationCount)
        .add("allocationSize", allocationSize)
        .add("reps", reps)
        .toString();
  }
}
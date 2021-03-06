package org.jbenchx.collections.jcf;

import java.util.ArrayDeque;
import java.util.Collection;

import org.jbenchx.annotations.Bench;
import org.jbenchx.annotations.ForEachInt;

public class ArrayDequeBenchmark extends CollectionQueryBenchmark {

  public ArrayDequeBenchmark(@ForEachInt({10, 1000}) int size) {
    super(size);
  }

  @Bench
  public Collection<String> create() {
    return createEmptyCollection();
  }

  @Override
  protected Collection<String> createEmptyCollection() {
    return new ArrayDeque<String>();
  }
  
}

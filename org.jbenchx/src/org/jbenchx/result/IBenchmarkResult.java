/*
 * Created on 01.10.2011
 *
 */
package org.jbenchx.result;

import java.util.*;

import javax.annotation.*;

import org.jbenchx.*;

public interface IBenchmarkResult {

  public ITaskResult getResult(IBenchmarkTask task);

  @CheckForNull
  public IBenchmarkTask findTask(String name);

  public Set<IBenchmarkTask> getTasks();

}
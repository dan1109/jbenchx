package org.jbenchx;

import java.lang.reflect.*;

import org.jbenchx.result.*;
import org.jbenchx.util.*;
import org.jbenchx.vm.*;

public class BenchmarkTask implements IBenchmarkTask {

//  private static final int          MIN_TARGET_TIME = 1 * 1000 * 1000;

  private static final double       SQRT2 = Math.sqrt(2);

  private final String              fClassName;

  private final String              fMethodName;

  private final String              fBenchmarkName;

  private final BenchmarkParameters fParams;

  private final boolean             fSingleRun;

  public BenchmarkTask(String benchmarkName, String className, String methodName, BenchmarkParameters params, boolean singleRun) {
    fBenchmarkName = benchmarkName;
    fClassName = className;
    fMethodName = methodName;
    fParams = params;
    fSingleRun = singleRun;
  }

  @Override
  public void run(BenchmarkResult result, IBenchmarkContext context) {
    try {

      context.getProgressMonitor().started(this);

      TaskResult taskResult = internalRun(context);

      result.addResult(this, taskResult);
      context.getProgressMonitor().done(this);

    } catch (Exception e) {
      result.addResult(this, new TaskResult(context, new BenchmarkTimings(fParams), 0, new BenchmarkTaskFailure(this, e)));
      context.getProgressMonitor().failed(this);
    }
  }

  private TaskResult internalRun(IBenchmarkContext context) throws Exception {
    Object benchmark = createInstance();
    Method method = getBenchmarkMethod(benchmark);
    long iterationCount = findIterationCount(context, benchmark, method);

    BenchmarkTimings timings = new BenchmarkTimings(fParams);
    SystemUtil.cleanMemory();
    VmState preState = VmState.getCurrentState();
    long runtimePerIteration = Long.MAX_VALUE;
    do {

      GcStats preGcStats = SystemUtil.getGcStats();
      long time = singleRun(benchmark, method, iterationCount);
      GcStats postGcStats = SystemUtil.getGcStats();
      VmState postState = VmState.getCurrentState();
      runtimePerIteration = Math.min(runtimePerIteration, time / iterationCount);

      Timing timing = new Timing(time, preGcStats, postGcStats);
      if (preState.equals(postState)) {
        timings.add(timing);
      } else {
        // restart
        timings.clear();
        iterationCount = findIterationCount(context, benchmark, method);
        runtimePerIteration = Long.MAX_VALUE;
      }
      context.getProgressMonitor().run(this, timing, VmState.difference(preState, postState));
      preState = postState;

    } while (timings.needsMoreRuns());

//    long avgNs = Math.round(timings.getEstimatedTime() / iterationCount / fDivisor);
//    System.out.println();
//    System.out.println(this + ": " + TimeUtil.toString(avgNs));
    TaskResult result = new TaskResult(context, timings, iterationCount);
    long minSingleIterationTime = context.getMethodInvokeTime() * 10;
    if (runtimePerIteration < minSingleIterationTime) {
      result.addWarning(new BenchmarkWarning("Runtime of single iteration too short: " + runtimePerIteration
          + "ns, increase work in single iteration to run at least " + minSingleIterationTime + "ns"));
    }
    return result;
  }

  private long singleRun(Object benchmark, Method method, long iterationCount) throws IllegalArgumentException, IllegalAccessException,
      InvocationTargetException {
    Timer timer = new Timer();
    timer.start();
    for (long i = 0; i < iterationCount; ++i) {
      method.invoke(benchmark);
    }
    return timer.stopAndReset();
  }

  private long findIterationCount(IBenchmarkContext context, Object benchmark, Method method) throws IllegalAccessException, InvocationTargetException {
    if (fSingleRun) {
      return 1;
    }

    Timer timer = new Timer();
    long iterations = 1;
    long time;
    for (;;) {

      timer.start();
      for (int i = 0; i < iterations; ++i) {
        method.invoke(benchmark);
      }
      time = timer.stopAndReset();
      if (iterations == 1 && time > fParams.getTargetTimeNs()) {
        break;
      }
      if (time >= fParams.getTargetTimeNs() / SQRT2 && time < fParams.getTargetTimeNs() * SQRT2) {
        break;
      }
      time = Math.max(time, 2 * context.getTimerGranularity()); // at least two times the timer granularity
      double factor = (1.0 * fParams.getTargetTimeNs()) / time;
      iterations = Math.round(iterations * factor);
      iterations = Math.max(1, iterations);
    }
    return iterations;
  }

  private Method getBenchmarkMethod(Object benchmark) throws SecurityException, NoSuchMethodException {
    return benchmark.getClass().getMethod(fMethodName);
  }

  private Object createInstance() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    ClassLoader classLoader = ClassUtil.createClassLoader();
//    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Class<?> clazz = classLoader.loadClass(fClassName);
    return clazz.newInstance();
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public String getName() {
    return fBenchmarkName + "." + fMethodName;
  }

}

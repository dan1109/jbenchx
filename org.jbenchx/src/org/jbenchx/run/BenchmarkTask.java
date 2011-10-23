package org.jbenchx.run;

import java.lang.reflect.*;

import org.jbenchx.*;
import org.jbenchx.result.*;
import org.jbenchx.util.*;
import org.jbenchx.vm.*;

public class BenchmarkTask implements IBenchmarkTask {

  private static final double          SQRT2 = Math.sqrt(2);

  private final String                 fClassName;

  private final String                 fMethodName;

  private final String                 fBenchmarkName;

  private final BenchmarkParameters    fParams;

  private final boolean                fSingleRun;

  private final ParameterizationValues fMethodArguments;

  public BenchmarkTask(String benchmarkName, String className, String methodName, BenchmarkParameters params, boolean singleRun,
      ParameterizationValues methodArguments) {
    fBenchmarkName = benchmarkName;
    fClassName = className;
    fMethodName = methodName;
    fParams = params;
    fSingleRun = singleRun;
    fMethodArguments = methodArguments;
  }

  @Override
  public void run(BenchmarkResult result, IBenchmarkContext context) {
    try {

      context.getProgressMonitor().started(this);

      TaskResult taskResult = internalRun(context);

      result.addResult(this, taskResult);
      context.getProgressMonitor().done(this);

    } catch (Exception e) {
      result.addResult(this, new TaskResult(new BenchmarkTimings(fParams), 0, new BenchmarkTaskFailure(this, e)));
      context.getProgressMonitor().failed(this);
    }
  }

  private TaskResult internalRun(IBenchmarkContext context) throws Exception {
    long timerGranularity = 10 * TimeUtil.MS;
    long methodInvokeTime = 0;
    SystemInfo systemInfo = context.getSystemInfo();
    if (systemInfo != null) {
      timerGranularity = systemInfo.getTimerGranularity();
      methodInvokeTime = systemInfo.getMethodInvokeTime();
    }
    
    Object benchmark = createInstance();
    Method method = getBenchmarkMethod(benchmark);
    long iterationCount = findIterationCount(benchmark, method, timerGranularity);

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
        iterationCount = findIterationCount(benchmark, method, timerGranularity);
        runtimePerIteration = Long.MAX_VALUE;
      }
      context.getProgressMonitor().run(this, timing, VmState.difference(preState, postState));
      preState = postState;

    } while (timings.needsMoreRuns());

//    long avgNs = Math.round(timings.getEstimatedTime() / iterationCount / fDivisor);
//    System.out.println();
//    System.out.println(this + ": " + TimeUtil.toString(avgNs));
    TaskResult result = new TaskResult(timings, iterationCount);
    long minSingleIterationTime = methodInvokeTime * 10;
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
    Object[] arguments = fMethodArguments.getValues();
    for (long i = 0; i < iterationCount; ++i) {
      method.invoke(benchmark, arguments);
    }
    return timer.stopAndReset();
  }

  private long findIterationCount(Object benchmark, Method method, long timerGranularity) throws IllegalAccessException,
      InvocationTargetException {
    if (fSingleRun) {
      return 1;
    }
    
    Timer timer = new Timer();
    long iterations = 1;
    long time;
    for (;;) {

      Object[] arguments = fMethodArguments.getValues();
      timer.start();
      for (int i = 0; i < iterations; ++i) {
        method.invoke(benchmark, arguments);
      }
      time = timer.stopAndReset();
      if (iterations == 1 && time > fParams.getTargetTimeNs()) {
        break;
      }
      if (time >= fParams.getTargetTimeNs() / SQRT2 && time < fParams.getTargetTimeNs() * SQRT2) {
        break;
      }
      time = Math.max(time, 2 * timerGranularity); // at least two times the timer granularity
      double factor = (1.0 * fParams.getTargetTimeNs()) / time;
      iterations = Math.round(iterations * factor);
      iterations = Math.max(1, iterations);
    }
    return iterations;
  }

  private Method getBenchmarkMethod(Object benchmark) throws SecurityException, NoSuchMethodException {
    return benchmark.getClass().getMethod(fMethodName, fMethodArguments.getTypes());
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
    StringBuilder sb = new StringBuilder();
    sb.append(fBenchmarkName);
    sb.append('.');
    sb.append(fMethodName);
    if (fMethodArguments.getValues().length != 0) {
      sb.append('(');
      for (Object argument: fMethodArguments.getValues()) {
        sb.append(argument.toString());
        sb.append(',');
      }
      sb.setCharAt(sb.length() - 1, ')');
    }
    return sb.toString();
  }

}
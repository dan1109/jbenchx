package org.jbenchx.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

public class ClassUtil {

  public static boolean hasDefaultConstructor(Class<?> clazz) throws SecurityException {
    try {
      Constructor<?> constructor = clazz.getConstructor();
      if (!Modifier.isPublic(constructor.getModifiers())) {
        return false;
      }
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  public static ClassLoader createClassLoader() {
    return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {

      @Override
      public ClassLoader run() {
        return new BenchmarkClassLoader();
      }

    });
  }

  /**
   * Finds all annotations declared on a method in its class hierarchy.
   */
  public static <A extends Annotation> List<A> findMethodAnnotations(Method method, Class<A> annotationClass) throws SecurityException {
    ArrayList<A> result = new ArrayList<A>(4);
    findAnnotations(method, annotationClass, result);
    return result;
  }

  private static <A extends Annotation> void findAnnotations(Method method, Class<A> annotationClass, List<A> result) throws SecurityException {
    A annotation = method.getAnnotation(annotationClass);
    if (annotation != null) {
      result.add(annotation);
    }
    Class<?> parent = method.getDeclaringClass().getSuperclass();
    if (parent != null) {
      try {
        Method superMethod = parent.getMethod(method.getName(), method.getParameterTypes());
        findAnnotations(superMethod, annotationClass, result);
      } catch (NoSuchMethodException e) {
      }
    }
  }

}

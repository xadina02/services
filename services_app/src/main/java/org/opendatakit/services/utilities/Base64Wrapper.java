/*
 * Copyright (C) 2011-2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.services.utilities;

import org.opendatakit.logging.WebLogger;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper class for accessing Base64 functionality. This allows API Level 7
 * deployment of ODK Survey while enabling API Level 8 and higher phone to
 * support encryption.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class Base64Wrapper {

  private final String appName;
  private static final int FLAGS = 2;// NO_WRAP
  private Class<?> base64 = null;

  public Base64Wrapper(String appName) throws ClassNotFoundException {
    this.appName = appName;
    base64 = this.getClass().getClassLoader().loadClass("android.util.Base64");
  }

  public String encodeToString(byte[] ba) {
    Class<?>[] argClassList = new Class[] { byte[].class, int.class };
    try {
      Method m = base64.getDeclaredMethod("encode", argClassList);
      Object[] argList = new Object[] { ba, FLAGS };
      Object o = m.invoke(null, argList);
      byte[] outArray = (byte[]) o;
      String s = new String(outArray, StandardCharsets.UTF_8);
      return s;
    } catch (SecurityException e) {
      e.printStackTrace();
      throw new IllegalArgumentException(e.toString());
    } catch (NoSuchMethodException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalArgumentException(e.toString());
    } catch (IllegalAccessException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalArgumentException(e.toString());
    } catch (InvocationTargetException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalArgumentException(e.toString());
    }
  }

  public byte[] decode(String base64String) {
    Class<?>[] argClassList = new Class[] { String.class, int.class };
    Object o;
    try {
      Method m = base64.getDeclaredMethod("decode", argClassList);
      Object[] argList = new Object[] { base64String, FLAGS };
      o = m.invoke(null, argList);
    } catch (SecurityException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalArgumentException(e.toString());
    } catch (NoSuchMethodException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalArgumentException(e.toString());
    } catch (IllegalAccessException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalArgumentException(e.toString());
    } catch (InvocationTargetException e) {
      WebLogger.getLogger(appName).printStackTrace(e);
      throw new IllegalArgumentException(e.toString());
    }
    return (byte[]) o;
  }
}

/*
 * Copyright (c) 2012 Moodstocks SAS
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.moodstocks.android;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

/* class used to load the C library */
class Loader {
  private static boolean done = false;
  private static boolean compatible = true;

  /* enum to define the different possible CPU architectures */
  public static final class Architecture {
    public static final int NOT_ARM = -1;     // Unsupported architecture (X86 or MIPS)
    public static final int ARMv6 = 0;        // ARMv5TE to ARMv6 (included)
    public static final int ARMv7 = 1;        // ARMv7 *without* NEON support
    public static final int ARMv7_NEON = 2;   // ARMv7 *with* NEON support
  }

  protected static synchronized void load() {
    if (done)
      return;

    if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) {
      System.loadLibrary("jmoodstocks-sdk");

      switch (getCpuArch()) {
        case Architecture.ARMv6: System.loadLibrary("jmoodstocks-sdk-core-armv6");
                                 break;
        case Architecture.ARMv7: System.loadLibrary("jmoodstocks-sdk-core-armv7");
                                 break;
        case Architecture.ARMv7_NEON: System.loadLibrary("jmoodstocks-sdk-core-armv7-neon");
                                      break;
        default: compatible = false;
                 break;
      }
    }
    done = true;
  }

  protected static boolean isCompatible() {
    return compatible;
  }

  private static native int getCpuArch();
}

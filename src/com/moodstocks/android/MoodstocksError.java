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

/* Errors thrown if the C library encounters an error, for example:
 * - No internet connexion
 * - Invalid use of the library
 * - etc.
 *
 * These errors contain:
 * - an internal error code, accessible using getErrorCode().
 * - a description of the problem as a String, accessible
 *   using getMessage(), to help you solve the problem.
 */

public class MoodstocksError extends java.lang.Throwable {
  private static final long serialVersionUID = 1L;
  private int mErrorCode = 0;

  /***********************************************
   * set to `false` before release to avoid Logs *
   ***********************************************/
  private static final boolean DEBUG = true;

  public static final class Code {
    public static final int SUCCESS = 0;          /* success */
    public static final int ERROR = 1;            /* unspecified error */
    public static final int MISUSE = 2;           /* invalid use of the library */
    public static final int NOPERM = 3;           /* access permission denied */
    public static final int NOFILE = 4;           /* file not found */
    public static final int BUSY = 5;             /* database file locked */
    public static final int CORRUPT = 6;          /* database file corrupted */
    public static final int EMPTY = 7;            /* empty database */
    public static final int AUTH = 8;             /* authorization denied */
    public static final int NOCONN = 9;           /* no internet connection */
    public static final int TIMEOUT = 10;         /* operation timeout */
    public static final int THREAD = 11;          /* threading error */
    public static final int CREDMISMATCH = 12;    /* credentials mismatch */
    public static final int SLOWCONN = 13;        /* internet connection too slow */
    public static final int NOREC = 14;           /* record not found */
    public static final int ABORT = 15;           /* operation aborted */
    public static final int UNAVAIL = 16;         /* resource temporarily unavailable */
    public static final int IMG = 17;             /* image size or format not supported */
  }


  public MoodstocksError(String message, int code) {
    super(message);
    mErrorCode = code;
  }

  public int getErrorCode() {
    return mErrorCode;
  }

  /* log an error and its stack if DEBUG is true */
  public void log() {
    if (DEBUG) printStackTrace();
  }

}

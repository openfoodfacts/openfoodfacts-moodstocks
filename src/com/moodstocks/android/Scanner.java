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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

/* Scanner class.
 * The scanner offers an unified interface to perform:
 * - offline cache synchronization,
 * - search over the local cache of image records,
 * - remote search on Moodstocks API,
 * - 1D/2D barcode decoding.
 */
public final class Scanner {

  public static final String TAG = "Scanner";
  private static Scanner instance = null;
  private List<WeakReference<Sync.Listener>> extra_listeners = null;

  private ThreadPoolExecutor api_threadpool = null;
  private ExecutorService sync_thread = null;
  private Set<ApiSearch> api_searches = null;
  private Set<Sync> sync = null;

  private static final String DBFilename = "ms.db";

  private int ptr = 0;

  static {
    Loader.load();
    if (isCompatible()) init();
  }

  private Scanner() {
    super();
    // stores the extra Sync listeners.
    this.extra_listeners = new ArrayList<WeakReference<Sync.Listener>>();
    // stores the pending API Searches and sync.
    this.api_searches = new HashSet<ApiSearch>();
    this.sync = new HashSet<Sync>();
    // ThreadPool / Thread handling the Asynchronous Sync and API Searches.
    this.api_threadpool = (ThreadPoolExecutor)Executors.newCachedThreadPool();
    this.sync_thread = Executors.newSingleThreadExecutor();
  }

  /* singleton accessor */
  public static Scanner get()
      throws MoodstocksError {
    if (Scanner.instance == null) {
      synchronized(Scanner.class) {
        if (Scanner.instance == null) {
          Scanner.instance = new Scanner();
          if (isCompatible()) Scanner.instance.initialize();
        }
      }
    }
    return Scanner.instance;
  }

  /* destructor */
  public void destroy() {
    Scanner.instance.destruct();
    Scanner.instance = null;
  }

  /* Open the scanner and connect it to the database file.
   *****************************************************************
   * It also checks **at runtime** that the device is compatible
   * with Moodstocks SDK, aka that it runs Android 2.3 or over and
   * features an ARM CPU.
   * If it's not the case, it throws a RuntimeException.
   * We advise you to design your applications so it won't try to
   * use the scanner in such case, as the SDK will probably crash.
   *****************************************************************
   */
  public void open(Context context, String key, String secret)
      throws MoodstocksError {
    if (!isCompatible()) {
      throw new RuntimeException("DEVICE IS NOT COMPATIBLE WITH MOODSTOCKS SDK");
    }
    String path = context.getFilesDir().getAbsolutePath();
    this.open(path + "/" + DBFilename, key, secret);
  }

  /* close the scanner and disconnect it from the database file */
  public native void close()
      throws MoodstocksError;

  /* removes database */
  public static void clean(Context context, String filename)
      throws MoodstocksError {
    String path = context.getFilesDir().getAbsolutePath() + "/" + filename;
    clean(path);
  }

  /* Synchronize the cache.
   * This method runs in the background so you can safely call it from the UI thread.
   * Caller must implement Scanner.SyncListener interface. It will receive notifications
   * for this Sync only.
   * Returns false if a sync is already running.
   * NOTE: this method requires an Internet connection.
   */
  public boolean sync(Sync.Listener listener) {
    if (!isSyncing()) {
      sync_thread.submit(new Sync(listener, extra_listeners, sync));
      return true;
    }
    return false;
  }

  /* Add an extra SyncListener to the scanner. It will be used
   * every time a new sync is launched until it is removed.
   */
  public void addExtraSyncListener(Sync.Listener l) {
    Iterator<WeakReference<Sync.Listener>> it = extra_listeners.iterator();
    while(it.hasNext()) {
      WeakReference<Sync.Listener> listener = it.next();
      if (listener.get() == l)
        return;
    }
    this.extra_listeners.add(new WeakReference<Sync.Listener>(l));
  }

  /* Removes an extra SyncListener. It won't be notified anymore
   * of any sync.
   */
  public void removeExtraSyncListener(Sync.Listener l) {
    Iterator<WeakReference<Sync.Listener>> it = extra_listeners.iterator();
    while(it.hasNext()) {
      WeakReference<Sync.Listener> listener = it.next();
      if (listener.get() == l)
        it.remove();
    }
  }

  /* Returns true if the scanner is currently syncing, false otherwise */
  public boolean isSyncing() {
    if (sync.isEmpty()) {
      return false;
    }
    return true;
  }

  /* Perform a remote image search on Moodstocks API.
   * This method runs in the background so you can safely call it from the UI thread.
   * Caller must implement Scanner.ApiSearchListener to receive notifications and result.
   * NOTE: this method requires an Internet connection.
   */
  public void apiSearch(ApiSearch.Listener listener, Image qry) {
    api_threadpool.submit(new ApiSearch(listener, qry, api_searches));
  }

  /* Cancel any pending API Search. */
  public void apiSearchCancel() {
    Iterator<ApiSearch> it = api_searches.iterator();
    while(it.hasNext()) {
      it.next().cancel();
    }
  }

  /* Return the total number of images recorded into the local database */
  public native int count()
      throws MoodstocksError;

  /* Return an array of all images IDs found into the local database */
  public native List<byte[]> info()
      throws MoodstocksError;

  /* performs an offline image search among the local database*/
  public native Result search(Image qry)
      throws MoodstocksError;

  /* performs barcode decoding on the image, among the given formats */
  public native Result decode(Image qry, int formats)
      throws MoodstocksError;

  /* Match a query image against a reference from the local database */
  public native boolean match(Image qry, Result ref)
      throws MoodstocksError;

  /* check compatibility (Android level >= 2.3) */
  public static boolean isCompatible() {
    return ( VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD &&
             Loader.isCompatible() );
  }

  /* performs a *synchronous* synchronization */
  protected native void sync(Sync s)
      throws MoodstocksError;
  
  private static native void init();

  private native void initialize()
      throws MoodstocksError;

  private native void destruct();

  private native void open(String path, String key, String secret)
      throws MoodstocksError;

  private static native void clean(String path)
      throws MoodstocksError;

}

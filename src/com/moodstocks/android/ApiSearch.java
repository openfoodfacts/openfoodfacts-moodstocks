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
import java.util.Set;

import android.os.Handler;
import android.os.Message;

public class ApiSearch extends Handler implements Runnable {

  public static final String TAG = "ApiSearch";
  private WeakReference<Listener> listener;
  private Image qry;
  private Set<ApiSearch> set;
  private boolean cancelled = false;
  
  private int ptr = 0;
  
  public static interface Listener{
    /* notifies the caller that an API Search has been launched */
    public void onApiSearchStart();
    /* notifies the caller that an API Search has successfully ended.
     * result will be null if nothing was found.
     */
    public void onApiSearchComplete(Result result);
    /* notifies the caller that an API Search has failed with the given error */
    public void onApiSearchFailed(MoodstocksError e);
  }

  static {
    Loader.load();
    if (Scanner.isCompatible()) init();
  }

  private static final class MsgCode {
    private static final int START = 1;
    private static final int END = 2;
  }

  protected ApiSearch(Listener listener, Image qry, Set<ApiSearch> set) {
    super();
    this.listener = new WeakReference<Listener>(listener);
    this.qry = qry;
    qry.retain();
    this.set = set;
    set.add(this);
  }

  @Override
  public void run() {
    if (!cancelled) {
      startMessage();
      Result r = null;
      MoodstocksError err = null;
      try {
        r = search(Scanner.get(), qry);
      } catch (MoodstocksError e) {
        err = e;
      }
      qry.release();
      endMessage(r, err);
    }
    else
      qry.release();
  }
  
  protected void cancel() {
    cancelled = true;
    cancel_native();
  }

  private void startMessage() {
    Message.obtain(this, MsgCode.START).sendToTarget();
  }

  private void endMessage(Result r, MoodstocksError e) {
    Message.obtain(this, MsgCode.END, new ApiSearchMsg(r, e)).sendToTarget();
  }


  @Override
  public void handleMessage(Message msg) {
    Listener l = listener.get();
    switch (msg.what) {
      case MsgCode.START:
        if (l != null)
          l.onApiSearchStart();
        break;
      case MsgCode.END:
        set.remove(this);
        ApiSearchMsg m = (ApiSearchMsg)msg.obj;
        if (l != null && !cancelled) {
          if (m.error == null)
            l.onApiSearchComplete(m.result);
          else
            l.onApiSearchFailed(m.error);
        }
        break;
      default:
        break;
    }
  }

  private class ApiSearchMsg {
    public Result result;
    public MoodstocksError error;

    private ApiSearchMsg(Result r, MoodstocksError e) {
      super();
      this.result = r;
      this.error = e;
    }
  }

  private static native void init();

  private native Result search(Scanner s, Image qry)
      throws MoodstocksError;

  private native void cancel_native();

}

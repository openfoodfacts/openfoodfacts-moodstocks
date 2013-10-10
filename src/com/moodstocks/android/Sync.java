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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.os.Handler;
import android.os.Message;

public class Sync extends Handler implements Runnable {

  public static final String TAG = "Sync";
  private WeakReference<Listener> listener;
  private List<WeakReference<Listener>> extra;
  private Set<Sync> set;
  
  public static interface Listener{
    /* notifies the caller that a Sync has been launched */
    public void onSyncStart();
    /* notifies the caller that a Sync has successfully ended */
    public void onSyncComplete();
    /* notifies the caller that a Sync has failed with the given error */
    public void onSyncFailed(MoodstocksError e);
    /* notifies the caller of the Sync progression.
     * `total` is the total number of images that will be synced.
     * `current` is the current number of synced images.
     */
    public void onSyncProgress(int total, int current);
  }

  private static final class MsgCode {
    private static final int START = 1;
    private static final int END = 2;
    private static final int PROGRESS = 3;
  }

  protected Sync(Listener listener, List<WeakReference<Listener>> extra, Set<Sync> set) {
    super();
    this.extra = extra;
    Iterator<WeakReference<Listener>> it = extra.iterator();
    while (it.hasNext()) {
      WeakReference<Listener> l = it.next();
      if (l.get() == listener)
        listener = null;
      // clean dead references
      if (l.get() == null)
        it.remove();
    }
    this.listener = new WeakReference<Listener>(listener);
    this.set = set;
    set.add(this);
  }

  @Override
  public void run() {
    startMessage();
    MoodstocksError err = null;
    try {
      Scanner.get().sync(this);
    } catch (MoodstocksError e) {
      err = e;
    }
    endMessage(err);
  }

  private void startMessage() {
    Message.obtain(this, MsgCode.START).sendToTarget();
  }

  private void endMessage(MoodstocksError e) {
    Message.obtain(this, MsgCode.END, e).sendToTarget();
  }

  // Called from JNI.
  @SuppressWarnings("unused")
  private void progressMessage(int total, int current) {
    Message.obtain(this, MsgCode.PROGRESS, total, current).sendToTarget();
  }

  @Override
  public void handleMessage(Message msg) {
    switch (msg.what) {
      case MsgCode.START:
        start();
        break;
      case MsgCode.END:
        end((MoodstocksError)msg.obj);
        break;
      case MsgCode.PROGRESS:
        progress(msg.arg1, msg.arg2);
        break;
      default:
        break;
    }
  }

  private void start() {
    if (listener.get() != null)
      listener.get().onSyncStart();
    Iterator<WeakReference<Listener>> it = extra.iterator();
    while (it.hasNext()) {
    WeakReference<Listener> l = it.next();
      if (l.get() == null)
        it.remove();
      else
        l.get().onSyncStart();
    }
  }

  private void end(MoodstocksError e) {
    set.remove(this);
    if (e == null) {
      if (listener.get() != null)
        listener.get().onSyncComplete();
      Iterator<WeakReference<Listener>> it = extra.iterator();
      while (it.hasNext()) {
      WeakReference<Listener> l = it.next();
        if (l.get() == null)
          it.remove();
        else
          l.get().onSyncComplete();
      }
    }
    else {
      if (listener.get() != null)
        listener.get().onSyncFailed(e);
      Iterator<WeakReference<Listener>> it = extra.iterator();
      while (it.hasNext()) {
      WeakReference<Listener> l = it.next();
        if (l.get() == null)
          it.remove();
        else
          l.get().onSyncFailed(e);
      }
    }
  }

  private void progress(int total, int current) {
    if (listener.get() != null)
      listener.get().onSyncProgress(total, current);
    Iterator<WeakReference<Listener>> it = extra.iterator();
    while (it.hasNext()) {
    WeakReference<Listener> l = it.next();
      if (l.get() == null)
        it.remove();
      else
        l.get().onSyncProgress(total, current);
    }
  }

}

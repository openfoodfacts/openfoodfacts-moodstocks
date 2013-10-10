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

import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/* Looper that requests autofocus every 1.5 seconds. */
public class AutoFocusManager extends Handler implements Camera.AutoFocusCallback {

  public static final String TAG = "Autofocus";
  private Camera camera;
  private boolean is_focus = false;
  private boolean focussing = false;
  private static int FOCUS_REQUEST;

  private static final long FOCUS_DELAY = 1500;

  protected AutoFocusManager(Camera cam) {
    if (cam != null) {
      this.camera = cam;
    }
    else {
      Log.e(TAG, "AutofocusManager passed null camera");
    }
  }

  protected void start() {
    if (camera != null) {
      camera.autoFocus(this);
      focussing = true;
    }
  }

  protected void stop() {
    this.removeMessages(FOCUS_REQUEST);
  }

  protected boolean isFocussed() {
    return (is_focus && !focussing);
  }

  protected void requestFocus() {
    if (!focussing && !is_focus) {
      focussing = true;
      stop();
      start();
    }
  }

  @Override
  public void handleMessage(Message m) {
    if (m.what == FOCUS_REQUEST && camera != null) {
      camera.autoFocus(this);
      focussing = true;
    }
  }

  @Override
  public void onAutoFocus(boolean success, Camera camera) {
    this.sendEmptyMessageDelayed(FOCUS_REQUEST, FOCUS_DELAY);
    focussing = false;
    is_focus = success;
  }

}

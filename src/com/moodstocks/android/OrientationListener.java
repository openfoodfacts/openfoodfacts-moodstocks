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

import android.content.Context;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

/* This class detects the device physical orientation */
public class OrientationListener extends OrientationEventListener {

  public static final String TAG = "OrientationListener";
  private static OrientationListener instance = null;
  private static Context context = null;
  private int orientation;
  private Callback callback;
  private static int orientation_offset = 0;

  // defines the device physical orientation
  public static final class Orientation { // phone is held:
    public static final int UP = 0;     // portrait, upright
    public static final int RIGHT = 1;  // landscape, microphone to the left, speaker to the right
    public static final int DOWN = 2;   // portrait, upside-down
    public static final int LEFT = 3;   // landscape, microphone to the right, speaker to the left
  }

  private OrientationListener(Context context) {
    super(context);
    orientation = Orientation.UP+orientation_offset; //default if unknown
    callback = null;
  }

  //---------------------------------------------
  // **MUST** be called before any call to get()
  //---------------------------------------------
  protected static void init(Context c) {
    context = c;
    // adjust orientation if required
    switch (((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation()) {
    case Surface.ROTATION_90:
      orientation_offset = 90;
      break;
    case Surface.ROTATION_180:
      orientation_offset = 180;
      break;
    case Surface.ROTATION_270:
      orientation_offset = 270;
      break;
    default:
      orientation_offset = 0;
      break;
    }
  }

  /* singleton accessor */
  protected static OrientationListener get() {
    if (instance == null) {
      synchronized(OrientationListener.class) {
        if (instance == null) {
          if (context != null) {
            instance = new OrientationListener(context);
          }
          else {
            throw new RuntimeException("init() must be called before calling get()");
          }
        }
      }
    }
    return instance;
  }

  /* returns the device current orientation among the 4
   * canonical orientations defined in Orientation subclass.
   */
  protected int getOrientation() {
    return orientation;
  }

  @Override
  public void onOrientationChanged(int degrees) {
    if (degrees != OrientationListener.ORIENTATION_UNKNOWN) {
      int ori = ((degrees+45+orientation_offset)/90)%4; //corresponds to the given enum Orientation
      if (ori != orientation) {
        orientation = ori;
        if (callback != null) {
          callback.onOrientationChanged(ori);
        }
      }
    }
  }

  //--------------------
  // CALLBACK INTERFACE
  //--------------------

  /* Classes implementing OrientationListener.Callback
   * can be notified of orientation changes through
   * onOrientationChanged method.
   */
  protected static interface Callback {
    public void onOrientationChanged(int orientation);
  }

  /* Sets the callback class that will be notified
   * of orientation changes.
   * If a previous callback was set, it will be replaced
   * and will not receive notifications anymore.
   */
  protected void setCallback(Callback c) {
    this.callback = c;
  }



}

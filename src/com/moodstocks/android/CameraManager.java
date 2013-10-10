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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


import android.content.Context;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/* Wrapper class around the camera. */
public class CameraManager extends Handler implements SurfaceHolder.Callback, Camera.PreviewCallback {

  public static interface Listener extends Camera.PreviewCallback {
    /* notifies the listener of the camera frames size */
    public void onPreviewInfoFound(int w, int h, boolean front_facing);
    /* Notifies the listener that the camera could *not* be opened. */
    public void onCameraOpenFailed(int e);
  }
  
  public static final class CameraError {
    public static final int SUCCESS = 0;
    public static final int NO_CAMERA = 1;
    public static final int OPEN_ERROR = 2;
  }

  public static final String TAG = "CameraManager";
  private static CameraManager instance = null;
  private Listener listener;
  private Camera cam;
  private SurfaceView preview;
  private Method setBackground = null;
  private BitmapDrawable camera_waiter;
  private SurfaceHolder preview_holder;
  private AutoFocusManager focus_manager;
  private List<Size> banned;
    
  private static boolean front_facing = false;
  private int surface_width;
  private int surface_height;
  private int preview_width;
  private int preview_height;
  private byte[] buffer;

  private boolean frame_requested = false;
  private boolean ready = false;
  private int readyMsg = 42;

  private CameraManager() {
    super();
    banned = new ArrayList<Size>();
    banned.clear();   
     
    /* For compatibility purposes, we use reflection here to detect which
     * of SurfaceView.setBackground() or SurfaceView.setBackgroundDrawable()
     * can be used.
     */
    try {
      setBackground = SurfaceView.class.getMethod("setBackground", new Class[] {Drawable.class});
    } catch (NoSuchMethodException e) {
      Log.d(TAG,  "setBackground() not found");
      try {
        setBackground = SurfaceView.class.getMethod("setBackgroundDrawable", new Class[] {Drawable.class});
      } catch (NoSuchMethodException e1) {
        // fail silently
      }
    }
  }

  /* Singleton accessor */
  protected static CameraManager get() {
    if (CameraManager.instance == null) {
      synchronized(CameraManager.class) {
        if (CameraManager.instance == null) {
          CameraManager.instance = new CameraManager();
        }
      }
    }
    return CameraManager.instance;
  }

  /* Start the camera.
   * Parameters:
   * - A listener to receive frames
   * - A SurfaceView to display the preview
   * - A WindowManager to get the screen dimensions.
   */
  @SuppressWarnings("deprecation") // necessary to support Android 2.3+
  protected void start(Context c, Listener l, SurfaceView surface) {
    ready = false;
    frame_requested = false;
    listener = l;
    preview = surface;
    preview_holder = surface.getHolder();
    preview_holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    preview_holder.addCallback(this);
    Resources r = c.getResources();
    camera_waiter = new BitmapDrawable(r, BitmapFactory.decodeResource(r, android.R.drawable.ic_menu_camera));
    camera_waiter.setGravity(Gravity.CENTER);
  }

  /* Stops the camera and preview */
  protected void stop() {
    if (focus_manager != null)
      focus_manager.stop();
    if (cam != null) {
      cam.stopPreview();
      cam.setPreviewCallback(null);
      cam.cancelAutoFocus();
      cam.release();
      cam = null;
    }
    ready = false;
    frame_requested = false;
  }

  /* Asks for a new frame to be delivered
   * to the listener.
   */
  protected void requestNewFrame() {
    if (!ready)
      frame_requested = true;
    else
      cam.addCallbackBuffer(buffer);
  }

  /* check if the AutoFocusManager is currently
   * focussing, or if the image is already focussed.
   */
  protected boolean isFocussed() {
    return focus_manager.isFocussed();
  }

  /* Interrupts the AutoFocusManager loop and
   * requests an autofocus immediately if it is
   * necessary.
   */
  protected void requestFocus() {
    focus_manager.requestFocus();
  }

  private static Camera getCameraInstance() 
      throws Exception {
    Camera.CameraInfo info = new Camera.CameraInfo();
    int nbCameras = Camera.getNumberOfCameras();
    int back = -1;
    int front = -1;
    for (int i = 0; i < nbCameras; ++i) {
      Camera.getCameraInfo(i, info);
      if (back < 0 && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
        back = i;
      if (front < 0 && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
        front = i;
    }
    Camera c = null;
    if (back >= 0 || front >= 0) {
      int camera_id;
      if (back >= 0) {
        camera_id = back;
        front_facing = false;
      }
      else {
        camera_id = front;
        front_facing = true;
      }
      c = Camera.open(camera_id); // attempt to get a Camera instance
    }
    return c;
  }

  // compute best preview size: highest possible
  // with ratio within 10% of screen resolution
  private void findBestPreviewSize() {
    Parameters params  = cam.getParameters();
    float ratio = (float)surface_height/surface_width;
    // available preview sizes:
    List<Size> prev_sizes = params.getSupportedPreviewSizes();
    for (Size s : banned) {
      prev_sizes.remove(s);
    }
    int best_w = 0;
    int best_h = 0;
    for (Size s : prev_sizes) {
      int w = s.width;
      int h = s.height;
      if (w > 1280 || h > 1280) continue;
      float r = (float)w/(float)h;
      if (((r-ratio)*(r-ratio))/(ratio*ratio) < 0.01 && w > best_w) {
        best_w = w;
        best_h = h;
      }
    }
    // nothing found with good ratio? take biggest.
    // should rarely (never?) happen.
    if (best_w == 0) {
      for (Size s : prev_sizes) {
        int w = s.width;
        if (w > best_w) {
          best_w = w;
          best_h = s.height;
        }
      }
    }
    // set the values
    preview_width = best_w;
    preview_height = best_h;
    params.setPreviewSize(preview_width, preview_height);
    // we force the preview format to NV21
    params.setPreviewFormat(ImageFormat.NV21);
    cam.setParameters(params);
    // pre-allocate buffer of size #pixels x 3/2
    // as NV21 uses #pixels for grayscale and twice
    // #pixels/4 for chroma.
    buffer = new byte[preview_width*preview_height*3/2];
    // notify Listener
    listener.onPreviewInfoFound(preview_width, preview_height, front_facing);
  }

  //------------------------
  // SurfaceHolder.Callback
  //------------------------
  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    
    surface_width = width;
    surface_height = height;
        
    /* This thread allows to open the camera asynchronously, as advised in the Android documentation.
     * (See http://developer.android.com/reference/android/hardware/Camera.html#open(int) )
     * Upon completion, it sends a message to the handler of this class, right below this function.
     */
    
    new Thread() {
      Handler h;

      public Thread setHandler(Handler h) {
        this.h = h;
        return this;
      }

      @Override
      public void run() {
        int error = CameraError.SUCCESS;
        try {
          cam = getCameraInstance();
        } catch (Exception err) {
          error = CameraError.OPEN_ERROR;
        }
        if (error == CameraError.SUCCESS && cam == null)
          error = CameraError.NO_CAMERA;
        Integer success = Integer.valueOf(error);
        Message.obtain(h, readyMsg, success).sendToTarget();
      }

    }.setHandler(this).start();
  }
  

  @Override
  public void handleMessage(Message msg) {
    if (msg.what == readyMsg) {
      int e = ((Integer)msg.obj).intValue();
      if (e == CameraError.SUCCESS) {
        startPreview();
      }
      else {
        listener.onCameraOpenFailed(e);
      }
    }
  }

  private void startPreview() {
    findBestPreviewSize();
    cam.setPreviewCallback(this);
    // adapt preview orientation or portrait mode
    cam.setDisplayOrientation(90);
    focus_manager = new AutoFocusManager(cam);
    try {
      cam.setPreviewDisplay(preview_holder);
    } catch (IOException e) {
      Log.e(TAG, "ERROR: Could not start preview");
    }
    cam.startPreview();
    focus_manager.start();
    setCameraWaiter(null);
  }
  
  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    // void implementation
    setCameraWaiter(camera_waiter);
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    stop();
  }
  
  private void setCameraWaiter(Drawable d) {
    if (setBackground != null) {
      try {
        setBackground.invoke(preview, (Object)d);
      } catch (IllegalArgumentException e) {

      } catch (IllegalAccessException e) {

      } catch (InvocationTargetException e) {

      }
    }
  }

  /*******************************************************************
   * UGLY HACK due to some problems on older devices and custom ROMs
   * (e.g. HTC Desire Bravo + Cyanogenmod 7.1):
   * In some cases, Camera.getSupportedPreviewSizes() returns sizes
   * that are *not* available.
   * This checks that the chosen resolution is indeed available, and
   * chooses another one if it's not.
   *******************************************************************/
  @Override
  public void onPreviewFrame(byte[] data, Camera camera) {
    if (data.length != 3*preview_width*preview_height/2) {
      Size s = cam.new Size(preview_width,preview_height);
      banned.add(s);
      findBestPreviewSize();
    }
    else {
      cam.setPreviewCallbackWithBuffer(listener);
      ready = true;
      if (frame_requested)
        requestNewFrame();
    }
  }

}

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

import com.moodstocks.android.CameraManager.CameraError;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.SurfaceView;

public class ScannerSession extends Handler implements CameraManager.Listener {
  public static final String TAG = "ScannerSession";
  private Activity parent;
  private Scanner scanner = null;
  private WeakReference<Listener> listener;
  private WorkerThread worker;
  
  private boolean front_facing = false;
  private int frame_width;
  private int frame_height;
  private boolean running = false;
  private boolean snap = false;

  // default options: cache image recognition only.
  protected int options = Result.Type.IMAGE;

  /* Interface that must be implemented by the calling Activity.
   * Note that it extends ApiSearch.Listener. */
  public static interface Listener extends ApiSearch.Listener {
    /* Notifies the caller that a scan has ended. If a barcode was successfully
     * decoded, or an image from the offline cache recognized, result will be
     * non-null. Otherwise, it is null.
     */
    public void onScanComplete(Result result);
    /* Notifies the caller that a scan has failed with given error */
    public void onScanFailed(MoodstocksError error);
  }

  /* Constructor. Requires:
   * - a parent activity
   * - a listener to notify
   * - a SurfaceView to display camera preview.
   */
  public ScannerSession(Activity parent, Listener listener, SurfaceView preview) throws MoodstocksError {
    this.listener = new WeakReference<Listener>(listener);
    this.scanner = Scanner.get();
    this.parent = parent;
    this.worker = new WorkerThread(this);
    OrientationListener.init(parent);
    OrientationListener.get().enable();
    CameraManager.get().start(parent, this, preview);
    worker.start();
  }

  /* Set the operations you want the scan() function to perform,
   * among offline image recognition and barcode decoding.
   * `options` must be a list of bitwise-or separated options
   * chosen among Result.Type, e.g:
   * Result.type.IMAGE | Result.Type.QRCODE if you choose to
   * perform offline image recognition and QR-Codes decoding.
   */
  public void setOptions(int options) {
    this.options = options;
  }

  /* Launch an online search on the next frame.
   * Returns false if the operation could not be performed,
   * because either the session is paused or a previous call
   * to snap has not terminated yet.
   */
  public boolean snap() {
    if (running && !snap) {
      snap = true;
      return true;
    }
    return false;
  }

  /* Cancel previous call to snap().
   * Returns false if the operation could not be performed,
   * because either the session is paused or there is no
   * online search currently running.
   */
  public boolean cancel() {
    scanner.apiSearchCancel();
    if (running && snap) {
      CameraManager.get().requestNewFrame();
      snap = false;
      return true;
    }
    snap = false;
    return false;
  }

  /* Start/restart scanning.
   * Returns false if the scanner
   * session was already running.
   */
  public boolean resume() {
    if (!running) {
      worker.reset();
      running = true;
      CameraManager.get().requestNewFrame();
      return true;
    }
    return false;
  }

  /* Pause scanning.
   * Returns false if the scanner
   * session was already paused.
   */
  public boolean pause() {
    if (running) {
      running = false;
      return true;
    }
    return false;
  }

  /* close the session */
  public void close() {
    pause();
    cancel();
    OrientationListener.get().disable();
    CameraManager.get().stop();
    finishWorker(500L);
  }

  /* closes the worker thread, letting it `t` milliseconds to end */
  private void finishWorker(long t) {
    worker.getHandler().obtainMessage(MsgCode.QUIT).sendToTarget();
    try {
      worker.join(t);
    } catch (InterruptedException e) {
      
    }
  }

  //------------------------
  // CameraManager.Listener
  //------------------------
  @Override
  public void onPreviewInfoFound(int w, int h, boolean front_facing) {
    this.frame_width = w;
    this.frame_height = h;
    this.front_facing = front_facing;
  }
  
  /* This callback is here for the rare cases where the camera is not available.
   * Most often, this is caused by a past crash in another application (sometimes
   * the Android Camera app itself) that could not release the camera correctly.
   * In such a case, the only fix is to reboot the device: we inform the user and
   * exit the app.
   */
  @Override
  public void onCameraOpenFailed(int e) {
    AlertDialog.Builder builder = new AlertDialog.Builder(parent);
    builder.setCancelable(false);
    builder.setTitle("Camera Unavailable!");
    if (e == CameraError.NO_CAMERA)
      builder.setMessage("There seem to be no camera on your device.");
    else if (e == CameraError.OPEN_ERROR)
      builder.setMessage("If this problem persists, please reboot your device in order to fix it.");
    builder.setNeutralButton("Quit", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        parent.finish();
      }
    });
    builder.show();
  }

  @Override
  public void onPreviewFrame(byte[] data, Camera camera) {
    if (running) {
      if (snap) {
        if (CameraManager.get().isFocussed()) {
          worker.getHandler().obtainMessage(MsgCode.SNAP, data).sendToTarget();
        }
        else {
          CameraManager.get().requestFocus();
          CameraManager.get().requestNewFrame();
        }
      }
      else {
        worker.getHandler().obtainMessage(MsgCode.SCAN, data).sendToTarget();
      }
    }
  }
  
  @Override
  public void handleMessage(Message msg) {
    Listener l = listener.get();
    boolean newFrame = true;
    
    switch(msg.what) {
    
      case MsgCode.SUCCESS:
        if (l != null)
          l.onScanComplete((Result)msg.obj);
        break;
        
      case MsgCode.FAILED:
        if (l != null)
          l.onScanFailed((MoodstocksError)msg.obj);
        break;
        
      case MsgCode.API_START:
        if (l != null)
          l.onApiSearchStart();
        newFrame = false;
        break;
        
      case MsgCode.API_SUCCESS:
        snap = false;
        if (l != null)
          l.onApiSearchComplete((Result)msg.obj);
        break;
        
      case MsgCode.API_FAILED:
        snap = false;
        MoodstocksError error = (MoodstocksError)msg.obj;
        if (error.getErrorCode() != MoodstocksError.Code.ABORT && l != null)
          l.onApiSearchFailed(error);
        break;
  
      default:
        break;
        
    }
    
    if (newFrame && running)
      CameraManager.get().requestNewFrame();
    
  }
  
  private class WorkerThread extends Thread implements ApiSearch.Listener {
    
    private Handler handler;
    private ScannerSession session;
    // locking values:
    private Result _result = null;
    private int _losts = 0;
    
    private WorkerThread(ScannerSession session) {
      super();
      this.session = session;
    }
    
    @Override
    public void run() {
      Looper.prepare();
      handler = new WorkerHandler(this);
      Looper.loop();
    }
    
    private Handler getHandler() {
      return handler;
    }
    
    private void reset() {
      _result = null;
      _losts = 0;
    }
    
    private void quit() {
      Looper.myLooper().quit();
    }
    
    private void scan(byte[] data) {
      Result result = null;
      MoodstocksError error = null;
      try {
        int ori = OrientationListener.get().getOrientation();
        if (front_facing)
          ori = (6-ori)%4;
        result = scan(new Image(data, frame_width, frame_height, frame_width, ori));
      } catch (MoodstocksError e) {
        error = e;
      }
      if (error != null) {
        session.obtainMessage(MsgCode.FAILED, error).sendToTarget();
      }
      else {
        session.obtainMessage(MsgCode.SUCCESS, result).sendToTarget();
      }
    }
    
    /* Performs a search in the local cache, as well as
     * barcode decoding, according to the options previously set.
     */
    private Result scan(Image qry)
        throws MoodstocksError {
      
      qry.retain();
      Result result = null;

      //----------
      // LOCKING
      //----------
      try {
        boolean lock = false;
        if (_result != null && _losts < 2) {
          int found = 0;
          switch (_result.getType()) {
            case Result.Type.IMAGE:
              found = scanner.match(qry, _result) ? 1 : -1;
              break;
            case Result.Type.QRCODE:
              Result qr = scanner.decode(qry, Result.Type.QRCODE);
              if (qr != null) {
                found = qr.getValue().equals(_result.getValue()) ? 1 : -1;
              }
              else {
                found = -1;
              }
              break;
            case Result.Type.DATAMATRIX:
              Result dmtx = scanner.decode(qry, Result.Type.DATAMATRIX);
              if (dmtx != null) {
                found = dmtx.getValue().equals(_result.getValue()) ? 1 : -1;
              }
              else {
                found = -1;
              }
              break;
            default:
              break;
          }

          if (found == 1) {
            lock = true;
            _losts = 0;
          }
          else if (found == -1) {
            _losts++;
            lock = (_losts >= 2) ? false : true;
          }
        }
        if (lock) {
          result = _result;
        }
      } catch (MoodstocksError e) {
        e.log();
      }

      //---------------
      // IMAGE SEARCH
      //---------------
      try {
        if (result == null && ((options & Result.Type.IMAGE) != 0)) {
          result = scanner.search(qry);
          if (result != null) {
            _losts = 0;
          }
        }
      } catch (MoodstocksError e) {
        if (e.getErrorCode() != MoodstocksError.Code.EMPTY)
          throw e;
      }


      //-------------------
      // BARCODE DECODING
      //-------------------
      if (result == null &&
         ( (options & (Result.Type.QRCODE|Result.Type.EAN13|
                       Result.Type.EAN8|Result.Type.DATAMATRIX) ) != 0)) {
        result = scanner.decode(qry, options);
        if (result != null) {
          _losts = 0;
        }
      }
      
      //----------------
      // Locking update
      //---------------
      _result = result;
      
      qry.release();
      return result;
    }
    
    private void snap(byte[] data) {
      scanner.apiSearch(this, new Image(data, frame_width, frame_height, frame_width, OrientationListener.get().getOrientation()));
    }
    
    @Override
    public void onApiSearchStart() {
      session.obtainMessage(MsgCode.API_START).sendToTarget();
    }

    @Override
    public void onApiSearchComplete(Result result) {
      session.obtainMessage(MsgCode.API_SUCCESS, result).sendToTarget();  
    }

    @Override
    public void onApiSearchFailed(MoodstocksError e) {
      session.obtainMessage(MsgCode.API_FAILED, e).sendToTarget();  
    }
    
  }
  
  private static class WorkerHandler extends Handler {
    
    private final WeakReference<WorkerThread> worker;
    
    private WorkerHandler(WorkerThread worker) {
      super();
      this.worker = new WeakReference<WorkerThread>(worker);
    }
    
    @Override
    public void handleMessage(Message msg) {
      
      WorkerThread w = worker.get();
      
      if (w != null) {
        switch(msg.what) {
        
          case MsgCode.SCAN:
            w.scan((byte[])msg.obj);
            break;
            
          case MsgCode.SNAP:
            w.snap((byte[])msg.obj);
            break;
            
          case MsgCode.QUIT:
            w.quit();
            break;
            
          default:
            break;
            
        }
      }
    }
  }
  
  // Enum used for message passing between ScannerSession and its WorkerThread
  protected static final class MsgCode {
    public static final int SCAN = 0;
    public static final int SNAP = 1;
    public static final int QUIT = 2;
    public static final int SUCCESS = 3;
    public static final int FAILED = 4;
    public static final int API_SUCCESS = 5;
    public static final int API_FAILED = 6;
    public static final int API_START = 7;
  }

}

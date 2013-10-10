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

import com.moodstocks.android.OrientationListener.Orientation;

public class Image {

  //--------------
  // Pixel Format
  //--------------
  // Specifies the color format and encoding for each pixel in the image.
  //
  // RGB32
  // This is a packed-pixel format handled in an endian-specific manner.
  // An RGBA color is packed in one 32-bit integer as follow:
  //   (A << 24) | (R << 16) | (G << 8) | B
  // This is stored as BGRA on little-endian CPU architectures (e.g. iPhone)
  // and ARGB on big-endian CPUs.
  //
  // GRAY8
  // This specifies a 8-bit per pixel grayscale pixel format.
  //
  // NV21
  // This specifies the YUV pixel format with 1 plane for Y and 1 plane for the
  // UV components, which are interleaved: first byte V and the following byte U
  public static final class PixelFormat {
    public static final int RGB32 = 0;
    public static final int GRAY8 = 1;
    public static final int NV21 = 2;
    public static final int NB = 3;
  }

  //-------------------
  // Image Orientation
  //-------------------
  // Flags defining the real orientation of the image as found within
  // the EXIF specification
  //
  // Each flag specifies where the origin (0,0) of the image is located.
  // Use 0 (undefined) to ignore or 1 (the default) to keep the
  // image unchanged.
  public static final class ExifOrientation {
    // undefined orientation (i.e image is kept unchanged)
    public static final int UNDEFINED = 0;
    // 0th row is at the top, and 0th column is on the left (the default)
    public static final int TOP_LEFT = 1;
    // 0th row is at the bottom, and 0th column is on the right
    public static final int BOTTOM_RIGHT = 3;
    // 0th row is on the right, and 0th column is at the top
    public static final int RIGHT_TOP = 6;
    // 0th row is on the left, and 0th column is at the bottom
    public static final int LEFT_BOTTOM = 8;
  }

  private int ptr = 0;
  private int counter = 0;

  static {
    Loader.load();
  }

  /* Image constructor:
   * - data is the raw byte array.
   * - w and h are the image dimensions in pixels
   * - bpr is the number of bytes per row in case
   *   of memory alignment.
   * - orientation is the device physical orientation as
   *   given by OrientationListener.
   *
   *****************************************************
   * We recommend that you use 1280x720 images whenever
   * it's possible.
   *****************************************************
   */
  protected Image(byte[] data, int w, int h, int bpr, int orientation) {
    /* NV21 is the default Android format */
    int fmt = PixelFormat.NV21;
    int ori = 0;
    switch(orientation) {
    case Orientation.UP: ori = ExifOrientation.LEFT_BOTTOM;
    break;
    case Orientation.RIGHT: ori = ExifOrientation.BOTTOM_RIGHT;
    break;
    case Orientation.DOWN: ori = ExifOrientation.RIGHT_TOP;
    break;
    case Orientation.LEFT: ori = ExifOrientation.TOP_LEFT;
    break;
    }
    try {
      initialize(data, w, h, bpr, fmt, ori);
    } catch (MoodstocksError e) {
      e.log();
    }
  }

  /* Reference counting.
   * Any function taking an Image as an argument must call retain()
   * on it at at the beginning and call release() on it once it's
   * not needed anymore.
   */
  protected void retain() {
    this.counter++;
  }

  protected void release() {
    this.counter--;
    if (counter <= 0) this.destruct();
  }

  /* destroys the native Image object */
  private native void destruct();

  /* we override finalize() to ensure that
   * the garbage collector also destroys
   * the native Image object
   */
  @Override
  protected void finalize() throws Throwable {
    this.destruct();
    super.finalize();
  }

  private native void initialize(byte[] data, int width, int height,
      int bpr, int fmt, int ori)
          throws MoodstocksError;
}

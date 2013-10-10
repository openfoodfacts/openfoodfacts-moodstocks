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

/* Class holding the result of a scan.
 * It is composed of:
 * - its type among those listed in below `Type` class
 * - its value as a string:
 *    Image ID if type == IMAGE,
 *    Barcode numbers if type == EAN8/EAN13
 *    **Unparsed** QR Code content if type == QRCODE
 */
public class Result {

  /* defines the type of a result */
  public static final class Type {
    public static final int NONE = 0;
    public static final int EAN8 = 1 << 0;        // EAN8 linear barcode
    public static final int EAN13 = 1 << 1;       // EAN13 linear barcode
    public static final int QRCODE = 1 << 2;      // QR Code 2D Barcode
    public static final int DATAMATRIX = 1 << 3;  // Datamatrix 2D Barcode
    public static final int IMAGE = 1 << 31;      // Image
  }

  static {
    Loader.load();
  }

  private int type;
  private byte[] bytes;
  private int length;

  /* constructor */
  public Result(int type, byte[] bytes, int length) {
    this.type = type;
    this.bytes = bytes.clone();
    this.length = length;
  }

  /* Return the result type among those listed in Result.Type */
  public int getType() {
    return type;
  }

  /* Return the result as a string with UTF-8 encoding
   * Use `getData` if you intend to create a string with another
   * encoding or just want to interact with the raw bytes
   */
  public String getValue() {
    return new String(this.bytes);
  }

  /* Return the result as raw data (byte array) */
  public byte[] getData() {
    return this.bytes;
  }

  /* Return the result decoded using Base64url without padding decoding */
  public native byte[] getDataFromBase64URL();

  /* Decodes a UTF-8 String of Base64url without padding data */
  public static native byte[] dataFromBase64URLString(String s)
      throws MoodstocksError;

  @Override
  public boolean equals(Object o) {
    if (o.getClass()!=this.getClass())
      return false;
    Result r = (Result)o;
    return ((r.type==this.type) && (r.getValue().equals(this.getValue())) &&
            (r.length==this.length));
  }

}

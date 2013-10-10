/*
 * Copyright (c) 2013 Moodstocks SAS
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

package com.moodstocks.phonegap.plugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.api.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.graphics.Color;
import android.util.Log;

import com.moodstocks.android.MoodstocksError;
import com.moodstocks.android.Scanner;
import com.moodstocks.android.Sync;

public class MoodstocksPlugin extends CordovaPlugin implements Sync.Listener {

	public static final String TAG = "MoodstocksPlugin";

	public static final String OPEN = "open";
	public static final String SYNC = "sync";
	public static final String SCAN = "scan";
	public static final String PAUSE = "pause";
	public static final String RESUME = "resume";
	public static final String DISMISS = "dismiss";

	public static final String PLUGINACTION = "pluginAction";
	public static final String MESSAGE = "message";
	public static final String STATUS = "status";
	public static final String PROGRESS =  "progress";

	private CallbackContext syncCallback;
	private static CallbackContext scanCallback;
	private static CordovaWebView overlay;

	private boolean scannerStarted = false;
	public boolean compatible = false;
	private Scanner scanner = null;

	//--------------------------------
	// Moodstocks API key/secret pair
	//--------------------------------
	private static final String API_KEY    = "secret";
	private static final String API_SECRET = "secret";

	public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
			throws JSONException {

		if (action.equals(OPEN)) {
			this.open(callbackContext);

			return true;
		}
		else if (action.equals(SYNC)) {
			this.syncCallback = callbackContext;
			this.sync();

			return true;
		}
		else if (action.equals(SCAN)) {
			MoodstocksPlugin.setOverlay(this.webView);
			MoodstocksPlugin.overlay.setBackgroundColor(Color.TRANSPARENT);

			MoodstocksPlugin.setScanCallback(callbackContext);
			this.scan(args);

			return true;
		}
		else if (action.equals(PAUSE)) {
			this.pause(callbackContext);

			return true;
		}
		else if (action.equals(RESUME)) {
			this.resume(callbackContext);

			return true;
		}
		else if (action.equals(DISMISS)) {
			this.dismiss(callbackContext);

			return true;
		}

		return true;
	}

	private void open(CallbackContext callbackContext) {
		compatible = Scanner.isCompatible();
		if (compatible) {
			try {
				this.scanner = Scanner.get();
				scanner.open(this.cordova.getActivity().getApplicationContext(), API_KEY, API_SECRET);
				callbackContext.success();

			} catch (MoodstocksError e) {
				if (e.getErrorCode() == MoodstocksError.Code.CREDMISMATCH) {
					// == DO NOT USE IN PRODUCTION: THIS IS A HELP MESSAGE FOR DEVELOPERS
					String errmsg = "there is a problem with your key/secret pair: "+
							"the current pair does NOT match with the one recorded within the on-disk datastore. "+
							"This could happen if:\n"+
							" * you have first build & run the app without replacing the default"+
							" \"ApIkEy\" and \"ApIsEcReT\" pair, and later on replaced with your real key/secret,\n"+
							" * or, you have first made a typo on the key/secret pair, build & run the"+
							" app, and later on fixed the typo and re-deployed.\n"+
							"\n"+
							"To solve your problem:\n"+
							" 1) uninstall the app from your device,\n"+
							" 2) make sure to properly configure your key/secret pair within Scanner.java\n"+
							" 3) re-build & run\n";
					MoodstocksError err = new MoodstocksError(errmsg, MoodstocksError.Code.CREDMISMATCH);
					err.log();
					// == DO NOT USE IN PRODUCTION: THIS WAS A HELP MESSAGE FOR DEVELOPERS
					callbackContext.error(errmsg);
				}
				else {
					e.log();
					callbackContext.error(e.getErrorCode());
				}
			}
		}
		else {
			/* device is not compatible */
			// error callback with message to display on web view
			callbackContext.error("DEVICE NOT COMPATIBLE");
		}
	}

	private void sync() {
		scanner.sync(this);
	}

	public void scan(JSONArray args) throws JSONException {
		Log.d(TAG, "scan action");

		Intent scanIntent = new Intent(cordova.getActivity(), MoodstocksScanActivity.class);
		scanIntent.putExtra("activity", "MoodstocksScanActivity");
		scanIntent.putExtra("scanOptions", args.getInt(0));

		// NOTE: the original startActivityForResult() will pause PhoneGap app's js code
		// the one we use here is a override-version
		this.cordova.startActivityForResult(this, scanIntent, 1);

		scannerStarted = true;
	}

	public void pause(CallbackContext callbackContext) {
		if(scannerStarted) {
			Intent pauseIntent = new Intent(PLUGINACTION);
			pauseIntent.putExtra(PLUGINACTION, PAUSE);

			this.cordova.getActivity().sendBroadcast(pauseIntent);
			callbackContext.success();
		}
	}

	public void resume(CallbackContext callbackContext) {
		if(scannerStarted) {
			Intent resumeIntent = new Intent(PLUGINACTION);
			resumeIntent.putExtra(PLUGINACTION, RESUME);

			this.cordova.getActivity().sendBroadcast(resumeIntent);
			callbackContext.success();
		}
	}

	public void dismiss(CallbackContext callbackContext) {
		if(scannerStarted) {
			Intent dismissIntent = new Intent(PLUGINACTION);
			dismissIntent.putExtra(PLUGINACTION, DISMISS);

			this.cordova.getActivity().sendBroadcast(dismissIntent);
			callbackContext.success();
		}
	}

	// Sync Listener

	@Override
	public void onSyncStart() {
		// Developer logs, do not use in production
		Log.d(TAG, " [SYNC] Starting...");

		JSONObject obj = new JSONObject();

		try {
			obj.put(MESSAGE, "");
			obj.put(STATUS, 1);
			obj.put(PROGRESS, 0);
		} catch(JSONException e) {}

		PluginResult r = new PluginResult(PluginResult.Status.OK, obj);
		r.setKeepCallback(true);
		this.syncCallback.sendPluginResult(r);
	}

	@Override
	public void onSyncComplete() {
		// Developer logs, do not use in production
		Log.d(TAG, " [SYNC] Complete!");

		JSONObject obj = new JSONObject();

		try {
			obj.put(MESSAGE, "");
			obj.put(STATUS, 3);
			obj.put(PROGRESS, 100);
		} catch(JSONException e) {}

		PluginResult r = new PluginResult(PluginResult.Status.OK, obj);
		r.setKeepCallback(false);
		this.syncCallback.sendPluginResult(r);
	}

	@Override
	public void onSyncFailed(MoodstocksError e) {
		// fail silently, the user has online search callback.
		e.log();

		JSONObject obj = new JSONObject();

		try {
			obj.put(MESSAGE, "");
			obj.put(STATUS, 0);
			obj.put(PROGRESS, 0);
		} catch(JSONException e1) {}

		PluginResult r = new PluginResult(PluginResult.Status.ERROR, obj);
		r.setKeepCallback(false);
		this.syncCallback.sendPluginResult(r);
	}

	@Override
	public void onSyncProgress(int total, int current) {
		// Developer logs, do not use in production
		Log.d(TAG, "[SYNC] " + current + "/" + total);

		JSONObject obj = new JSONObject();

		try {
			obj.put(MESSAGE, "");
			obj.put(STATUS, 2);
			obj.put(PROGRESS, 100 * (current/total));
		} catch(JSONException e) {}

		PluginResult r = new PluginResult(PluginResult.Status.OK, obj);
		r.setKeepCallback(true);
		this.syncCallback.sendPluginResult(r);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (compatible) {
			try {
				/* you must close the scanner before exiting */
				scanner.close();
			} catch (MoodstocksError e) {
				e.log();
			}
		}
	}

	public static MoodstocksWebView getOverlay() {
		return (MoodstocksWebView)overlay;
	}

	private static void setOverlay(CordovaWebView webView) {
		MoodstocksPlugin.overlay = webView;
	}

	public static CallbackContext getScanCallback() {
		return scanCallback;
	}

	public static void setScanCallback(CallbackContext scanCallback) {
		MoodstocksPlugin.scanCallback = scanCallback;
	}

}

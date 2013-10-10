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

import org.apache.cordova.api.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import com.moodstocks.android.MoodstocksError;
import com.moodstocks.android.Result;
import com.moodstocks.android.ScannerSession;
import org.openfoodfacts.scanner2.R;
import android.os.Build;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.SurfaceView;
import android.webkit.WebView;
import android.widget.RelativeLayout;

public class MoodstocksScanActivity extends Activity implements ScannerSession.Listener {
    
	public static final String TAG = "ScanActivity";
	public static final String PLUGINACTION = "pluginAction";
	public static final String PAUSE = "pause";
	public static final String RESUME = "resume";
	public static final String DISMISS = "dismiss";
	
	public static final String FORMAT = "format";
	public static final String VALUE = "value";

	private int ScanOptions = Result.Type.IMAGE;
	private ScannerSession session;
	private RelativeLayout webContainer;
	private boolean backPressActivated = true;
	private ActionReceiver MoodstocksActionReceiver;
	
    private class ActionReceiver extends BroadcastReceiver {
    	
		@Override
		public void onReceive(Context ctx, Intent intent) {
			String receivedAction = intent.getExtras().getString(PLUGINACTION);
			
			if (receivedAction.equals(PAUSE)) {
		        session.pause();
		        backPressActivated = false;
		    }
			
			if (receivedAction.equals(RESUME)) {
				session.resume();
				backPressActivated = true;
			}
			
			if (receivedAction.equals(DISMISS)) {
				backPressActivated = true;
				onBackPressed();
			}
		}
    }
		
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.scan);
	    
	    // Get the camera preview surface
	    SurfaceView preview = (SurfaceView) findViewById(R.id.preview);
	    
	    // Insert CordovaWebview as the overlay into the web container
	    webContainer = (RelativeLayout) findViewById(R.id.webContainer);
	    webContainer.addView(MoodstocksPlugin.getOverlay(), 0);
	    
	    // bug of android 3.0 - 4.0.3: issue with transparent webview
	    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB 
	    		&& android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
	    	webContainer.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
	    }
	    
	    // Create a scanner session
	    try {
	    	session = new ScannerSession(this, this, preview);
	    } catch (MoodstocksError e) {
	    	e.log();
	    }
	    
	    // Get scan options
	    ScanOptions = getIntent().getExtras().getInt("scanOptions");
	    session.setOptions(ScanOptions);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// start scanning!
		session.resume();
		
		// Setup the an action receiver for receiving broadcast intent
		if (MoodstocksActionReceiver == null) 
			MoodstocksActionReceiver = new ActionReceiver();
		
		// Filter on the key word pluginAction to make sure we only take plugin actions
		// a.k.a MoodstocksPlugin - resume(), pause(), dismiss()
		IntentFilter intentFilter = new IntentFilter(PLUGINACTION);
		registerReceiver(MoodstocksActionReceiver, intentFilter);
		
		// Update the flag in MoodstocksWebView
		MoodstocksPlugin.getOverlay().onStatusUpdate(true);
	}

	@Override
	protected void onPause() {		
		super.onPause();
		session.pause();
		
		// Unregister the receiver when the scanner is on pause
		if (MoodstocksActionReceiver != null) unregisterReceiver(MoodstocksActionReceiver);
		
		// Update the flag in MoodstocksWebView
		MoodstocksPlugin.getOverlay().onStatusUpdate(false);
	}
	
	@Override
	protected void onDestroy() {			
		super.onDestroy();
		session.close();
	}
	
	@Override
	public void onBackPressed() {
		if(backPressActivated) {
			session.pause();
	
			JSONObject obj = new JSONObject();
			try {
				obj.put(FORMAT, null);
				obj.put(VALUE, null);
			} catch (JSONException e) {}
	
			PluginResult r = new PluginResult(PluginResult.Status.OK, obj);
			r.setKeepCallback(false);
	
			MoodstocksPlugin.getScanCallback().sendPluginResult(r);
	
			// Remove the web view from web container when we quit the scan activity
			webContainer.removeView(MoodstocksPlugin.getOverlay());
			super.onBackPressed();
		}
	}
	
	//-------------------------
	// ScannerSession.Listener
	//-------------------------

	@Override
	public void onScanComplete(Result result) {
		if (result != null) {
			// result found, send to overlay
			JSONObject obj = new JSONObject();
			try {
				obj.put(FORMAT, result.getType());
				obj.put(VALUE, result.getValue());
			} catch (JSONException e) {}
			
			PluginResult r = new PluginResult(PluginResult.Status.OK, obj);
			r.setKeepCallback(true);
			
			MoodstocksPlugin.getScanCallback().sendPluginResult(r);
		}
	}
	
	@Override
	public void onScanFailed(MoodstocksError error) {
		// in this sample code, we just log the errors.
		error.log();
	}

	@Override
	public void onApiSearchStart() {
		// void implementation
		
	}

	@Override
	public void onApiSearchComplete(Result result) {
		// void implementation
		
	}

	@Override
	public void onApiSearchFailed(MoodstocksError e) {
		// void implementation
		
	}
}
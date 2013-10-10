package org.openfoodfacts.scanner2;

import org.apache.cordova.CordovaChromeClient;
import org.apache.cordova.CordovaWebViewClient;
import org.apache.cordova.DroidGap;
import org.apache.cordova.IceCreamCordovaWebViewClient;
import org.apache.cordova.api.CordovaPlugin;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewManager;

import com.moodstocks.phonegap.plugin.MoodstocksWebView;

public class MainActivity extends DroidGap {
	private boolean scanActivityStarted = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.loadUrl("file:///android_asset/www/index.html");
    }
    
    @Override
    public void init() {
    	MoodstocksWebView webView = new MoodstocksWebView(MainActivity.this);
        CordovaWebViewClient webViewClient;
        
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
            webViewClient = new CordovaWebViewClient(this, webView);
        }
        else {
            webViewClient = new IceCreamCordovaWebViewClient(this, webView);
        }
        
        this.init(webView, webViewClient, new CordovaChromeClient(this, webView));
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	
    	// Remove the web view from the root view when we launch the Moodstocks scanner
    	if (scanActivityStarted) {
    		super.root.removeView(super.appView);
    	}
    }
    
	@Override
    public void onResume() {
    	super.onResume();
    	
    	// this case is occurred when the scanActivity fails at launching
    	// the failure of launching scanner is often caused by the camera's unavailability
    	// in this case we retrieve & reload the web view before inserting it back
    	if (scanActivityStarted && (super.appView.getParent() != null)) {
    		((ViewManager)super.appView.getParent()).removeView(super.appView);
    		super.appView.reload();
    	}
    	
    	// Reset the web view to root container when we dismiss the Moodstocks scanner 
    	if (scanActivityStarted && (super.appView.getParent() == null)) {
    		super.root.addView(super.appView);
    		scanActivityStarted = false;
    	}
    }
    
    @Override
    public void startActivityForResult(CordovaPlugin command, Intent intent, int requestCode) {
    	// If the intent indicate the upcoming activity is a Moodtsocks scan activity
    	// We will launch the activity and keep the js/native code running on the background
    	if(intent.getExtras().getString("activity").equals("MoodstocksScanActivity"))  {
    		scanActivityStarted = true;
    		this.startActivityForResult(intent, requestCode);
    	}
    	else {
    		super.startActivityForResult(command, intent, requestCode);
    	}
    }
}

package com.cn6000.callrec;

import java.util.ArrayList;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class CallRecorderActivity extends Activity {
	private static final String TAG = CallRecorderActivity.class.getName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Button b = (Button) findViewById(R.id.btnStartStop);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.d(TAG, "start or stop service");
				Intent i = new Intent(CallRecorderService.ACTION);
				if (isServiceRunning()) {
					stopService(i);
				} else {
					startService(i);
				}
				updateButtonState((Button)v);
			}
		});
		updateButtonState(b);
	}

	private void updateButtonState(Button b) {
		if (isServiceRunning()) {
			b.setText(R.string.stop_record);
		} else {
			b.setText(R.string.start_record);
		}
	}
	
	private boolean isServiceRunning() {
		ActivityManager myManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		ArrayList<RunningServiceInfo> runningService = (ArrayList<RunningServiceInfo>) myManager
				.getRunningServices(30);
		for (int i = 0; i < runningService.size(); i++) {
			if (runningService.get(i).service.getClassName().equals(
					CallRecorderService.class.getName())) {
				return true;
			}
		}
		return false;
	}
}
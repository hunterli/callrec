package com.cn6000.callrec;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AutoRunReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context paramContext, Intent paramIntent) {
		Intent i = new Intent(CallRecorderService.ACTION);
		paramContext.startService(i);
		Log.d(CallRecorderService.TAG, "call recorder service starting...");
	}
}

package com.cn6000.callrec;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.TelephonyManager;
import android.util.Log;

public class CallRecorderService extends Service {
	public static final String ACTION = "com.cn6000.callrec.CALL_RECORD";
	protected static final String TAG = CallRecorderService.class.getName();
	private static final String AMR_DIR = "/callrec/";
	private static final String IDLE = "";
	private static final String INCOMING_CALL = "_i";
	private static final String OUTGOING_CALL = "_o";

	private Context cntx;
	private volatile String fileNamePrefix = IDLE;
	private volatile BroadcastReceiver receiver;
	private volatile MediaRecorder recorder;
	private volatile boolean isMounted = false;
	private volatile boolean isInRecording = false;

	@Override
	public IBinder onBind(Intent i) {
		return null;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		cntx = getApplicationContext();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "service destory");
		this.stopRecord();
		this.unregisterReceiver();
		cntx = null;
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (null != intent && ACTION.equals(intent.getAction())) {
			Log.d(TAG, "service start");
			prepareAmrDir();
			registerReceiver();
		}
		return super.onStartCommand(intent, flags, startId);
	}

	public Context getContext() {
		return cntx;
	}

	private void stopRecord() {
		if (isInRecording) {
			isInRecording = false;
			recorder.stop();
			recorder.release();
			recorder = null;
			Log.d(TAG, "call recording stopped");
		}
	}

	private String getDateTimeString() {
		SimpleDateFormat localSimpleDateFormat = new SimpleDateFormat(
				"yyyyMMdd'_'HHmmss");
		Date localDate = new Date();
		return localSimpleDateFormat.format(localDate);
	}

	private void callRecording() {
		if (!isMounted)
			return;
		stopRecord();
		try {
			Log.d(TAG, "prepare MediaRecorder");
			recorder = new MediaRecorder();
			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			File amr = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ AMR_DIR
					+ getDateTimeString()
					+ "_"
					+ fileNamePrefix + ".amr");
			recorder.setOutputFile(amr.getAbsolutePath());
			recorder.prepare();
			recorder.start();
			isInRecording = true;
			Log.d(TAG, "call recording in file " + amr.getAbsolutePath());
		} catch (Exception e) {
			Log.w(TAG, e);
		}
	}

	private void registerReceiver() {
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG, intent.getAction() + " extra state["
						+ TelephonyManager.EXTRA_STATE_IDLE + "|"
						+ TelephonyManager.EXTRA_STATE_RINGING + "|"
						+ TelephonyManager.EXTRA_STATE_OFFHOOK + "]");

				if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)
						|| intent.getAction().equals(
								Intent.ACTION_MEDIA_REMOVED)) {
					String state = Environment.getExternalStorageState();
					if (Environment.MEDIA_MOUNTED.equals(state)) {
						isMounted = true;
					} else {
						isMounted = false;
					}
				} else if (intent.getAction().equals(
						Intent.ACTION_NEW_OUTGOING_CALL)) {
					String state = intent
							.getStringExtra(TelephonyManager.EXTRA_STATE);
					String phoneNo = getResultData();
					if (null == phoneNo) {
						phoneNo = intent
								.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
					}
					Log.d(TAG,
							"Outgoing call, extra_phone_number: "
									+ phoneNo
									+ " extra_incoming_number: "
									+ intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
									+ " State: " + state);
					fileNamePrefix = getContactName(context, phoneNo)
							+ OUTGOING_CALL;
					Log.d(TAG, fileNamePrefix);
				} else if (intent.getAction().equals(
						TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
					String state = intent
							.getStringExtra(TelephonyManager.EXTRA_STATE);
					Log.d(TAG,
							"Phone state changed, extra_phone_number: "
									+ intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
									+ " extra_incoming_number: "
									+ intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
									+ " State: " + state);
					if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
						Log.d(TAG, "offhook, start recording...");
						callRecording();
					} else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
						Log.d(TAG, "idle, stop recording...");
						stopRecord();
						fileNamePrefix = IDLE;
						Log.d(TAG, fileNamePrefix);
					} else if (TelephonyManager.EXTRA_STATE_RINGING
							.equals(state)) {
						Log.d(TAG, "ringing, book incoming number...");
						fileNamePrefix = getContactName(
								context,
								intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER))
								+ INCOMING_CALL;
						Log.d(TAG, fileNamePrefix);
					}
				}
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		filter.addAction(Intent.ACTION_MEDIA_REMOVED);
		filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
		registerReceiver(receiver, filter);
	}

	private void unregisterReceiver() {
		if (null != receiver) {
			unregisterReceiver(receiver);
			receiver = null;
		}
	}

	private void prepareAmrDir() {
		isMounted = Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED);
		if (!isMounted)
			return;
		File amrRoot = new File(Environment.getExternalStorageDirectory()
				.getAbsolutePath() + AMR_DIR);
		if (!amrRoot.isDirectory())
			amrRoot.mkdir();
	}

	public String getContactName(Context cntx, String phoneNo) {
		Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
				Uri.encode(phoneNo));
		ContentResolver cr = cntx.getContentResolver();
		Cursor c = cr.query(uri, new String[] { PhoneLookup.DISPLAY_NAME },
				null, null, null);
		Log.d(TAG, "phoneNo: " + phoneNo + c);
		if (null == c)
			return phoneNo;
		try {
			if (c.moveToFirst()) {
				Log.d(TAG, "phoneNo: " + phoneNo + " name: " + c.getShort(0));
				return c.getString(0);
			} else {
				return phoneNo;
			}
		} finally {
			c.close();
		}
	}
}

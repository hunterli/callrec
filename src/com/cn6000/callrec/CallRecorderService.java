package com.cn6000.callrec;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

public class CallRecorderService extends Service {
	public static final String ACTION = "com.cn6000.callrec.CALL_RECORD";
	public static final String STATE = "STATE";
	public static final String START = "START";
	public static final String STORAGE = "STORAGE";
	public static final String INCOMING = "INCOMING";
	public static final String OUTGOING = "OUTGOING";
	public static final String BEGIN = "BEGIN";
	public static final String END = "END";

	protected static final String TAG = CallRecorderService.class.getName();
	private static final String AMR_DIR = "/callrec/";
	private static final String IDLE = "";
	private static final String INCOMING_CALL_SUFFIX = "_i";
	private static final String OUTGOING_CALL_SUFFIX = "_o";

	private Context cntx;
	private volatile String fileNamePrefix = IDLE;
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
		this.cntx = getApplicationContext();
		this.prepareAmrDir();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "service destory");
		this.stopRecording();
		this.cntx = null;
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (null == intent || !ACTION.equals(intent.getAction())) {
			return super.onStartCommand(intent, flags, startId);
		}
		String state = intent.getStringExtra(STATE);
		if (OUTGOING.equals(state)) {
			String phoneNo = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
			fileNamePrefix = getContactName(this.getContext(), phoneNo)
					+ OUTGOING_CALL_SUFFIX;
		} else if (INCOMING.equals(state)) {
			String phoneNo = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
			fileNamePrefix = getContactName(this.getContext(), phoneNo)
					+ INCOMING_CALL_SUFFIX;
		} else if (BEGIN.equals(state)) {
			startRecording();
		} else if (END.equals(state)) {
			stopRecording();
		} else if (STORAGE.equals(state)) {
			String mountState = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(mountState)) {
				prepareAmrDir();
			} else {
				isMounted = false;
			}
		}
		return START_STICKY;
	}

	public Context getContext() {
		return cntx;
	}

	private void stopRecording() {
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

	private void startRecording() {
		if (!isMounted)
			return;
		stopRecording();
		try {
			File amr = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ AMR_DIR
					+ getDateTimeString()
					+ "_"
					+ fileNamePrefix + ".amr");
			Log.d(TAG, "Prepare recording in " + amr.getAbsolutePath());
			recorder = new MediaRecorder();
			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			recorder.setOutputFile(amr.getAbsolutePath());
			recorder.prepare();
			recorder.start();
			isInRecording = true;
			Log.d(TAG, "Recording in " + amr.getAbsolutePath());
		} catch (Exception e) {
			Log.w(TAG, e);
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

	private String getContactName(Context cntx, String phoneNo) {
		if (null == phoneNo) return "";
		Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
				Uri.encode(phoneNo));
		ContentResolver cr = cntx.getContentResolver();
		Cursor c = cr.query(uri, new String[] { PhoneLookup.DISPLAY_NAME },
				null, null, null);
		if (null == c) {
			Log.d(TAG,
					"getContactName: The cursor was null when query phoneNo = "
							+ phoneNo);
			return phoneNo;
		}
		try {
			if (c.moveToFirst()) {
				String name = c.getString(0);
				Log.d(TAG, "getContactName: phoneNo: " + phoneNo + " name: "
						+ name);
				return name;
			} else {
				Log.d(TAG, "getContactName: Contact name of phoneNo = "
						+ phoneNo + " was not found.");
				return phoneNo;
			}
		} finally {
			c.close();
		}
	}
}

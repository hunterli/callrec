package com.cn6000.callrec;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import android.os.PowerManager;
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
	protected static final boolean DEBUG = false;

	private static final String AMR_DIR = "/callrec/";
	private static final String IDLE = "";
	private static final String INCOMING_CALL_SUFFIX = "_i";
	private static final String OUTGOING_CALL_SUFFIX = "_o";

	private Context cntx;
	private volatile String fileNamePrefix = IDLE;
	private volatile MediaRecorder recorder;
	private volatile PowerManager.WakeLock wakeLock;
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
		log("service create");
	}

	@Override
	public void onDestroy() {
		log("service destory");
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
		String phoneNo = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
		log("state: " + state + " phoneNo: " + phoneNo);
		if (OUTGOING.equals(state)) {
			fileNamePrefix = getContactName(this.getContext(), phoneNo)
					+ OUTGOING_CALL_SUFFIX;
		} else if (INCOMING.equals(state)) {
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
			if (!isInRecording) {
				stopSelf();
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
			releaseWakeLock();
			stopSelf();
			log("call recording stopped");
		}
	}

	private String getDateTimeString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'_'HHmmss");
		Date now = new Date();
		return sdf.format(now);
	}

	private String getMonthString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMM");
		Date now = new Date();
		return sdf.format(now);
	}

	private String getDateString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		Date now = new Date();
		return sdf.format(now);
	}

	private String getTimeString() {
		SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");
		Date now = new Date();
		return sdf.format(now);
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
			log("Prepare recording in " + amr.getAbsolutePath());
			recorder = new MediaRecorder();
			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			recorder.setOutputFile(amr.getAbsolutePath());
			recorder.prepare();
			recorder.start();
			isInRecording = true;
			acquireWakeLock();
			log("Recording in " + amr.getAbsolutePath());
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
		if (null == phoneNo)
			return "";
		Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
				Uri.encode(phoneNo));
		ContentResolver cr = cntx.getContentResolver();
		Cursor c = cr.query(uri, new String[] { PhoneLookup.DISPLAY_NAME },
				null, null, null);
		if (null == c) {
			log("getContactName: The cursor was null when query phoneNo = "
					+ phoneNo);
			return phoneNo;
		}
		try {
			if (c.moveToFirst()) {
				String name = c.getString(0);
				name = name.replaceAll("(\\||\\\\|\\?|\\*|<|:|\"|>)", "");
				log("getContactName: phoneNo: " + phoneNo + " name: " + name);
				return name;
			} else {
				log("getContactName: Contact name of phoneNo = " + phoneNo
						+ " was not found.");
				return phoneNo;
			}
		} finally {
			c.close();
		}
	}

	private void log(String info) {
		if (DEBUG && isMounted) {
			File log = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath()
					+ AMR_DIR
					+ "log_"
					+ getMonthString()
					+ ".txt");
			try {
				BufferedWriter out = new BufferedWriter(new FileWriter(log,
						true));
				try {
					synchronized (out) {
						out.write(getDateString()+getTimeString());
						out.write(" ");
						out.write(info);
						out.newLine();
					}
				} finally {
					out.close();
				}
			} catch (IOException e) {
				Log.w(TAG, e);
			}
		}
	}

	private void acquireWakeLock() {
		if (wakeLock == null) {
			log("Acquiring wake lock");
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this
					.getClass().getCanonicalName());
			wakeLock.acquire();
		}

	}

	private void releaseWakeLock() {
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
			wakeLock = null;
			log("Wake lock released");
		}

	}
}

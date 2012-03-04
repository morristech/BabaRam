package com.babaramdashcam;

import android.app.Activity;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.File;
import java.lang.Exception;

public class BabaRamCamera extends SurfaceView
	implements SurfaceHolder.Callback
{
	private static final String TAG = "BabaRamCamera";
	private Camera mCamera = null;
	private MediaRecorder mRecorder = null;
	private Activity mAct;
	private int mCameraId;
	private SurfaceHolder mHolder;

	public BabaRamCamera(Context context, int id) {
		super(context);

		mAct = (Activity) context;
		mCameraId = id;

		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		start();
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		stop();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	}

	public void start() {
		if (mCamera == null) {
			try {
				mCamera = Camera.open(mCameraId);
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();
				mCamera.setDisplayOrientation(getCameraDisplayOrientation());
				mCamera.unlock();
			} catch (Exception e) {
				Log.d(TAG, "Camera start: " + e.getMessage());
				Toast.makeText(
					mAct,
					getResources().getString(R.string.camera_error),
					Toast.LENGTH_SHORT
				).show();
				return;
			}
		}

		startRecorder();
	}

	public void startRecorder() {
		if (mRecorder == null) {
			try {
				mRecorder = new MediaRecorder();
				mRecorder.setCamera(mCamera);
				mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
				mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
				mRecorder.setProfile(
					CamcorderProfile.get(
						mCameraId,
						CamcorderProfile.QUALITY_HIGH
					)
				);
				mRecorder.setOutputFile(getOutputMediaPath());
				mRecorder.setPreviewDisplay(mHolder.getSurface());
				mRecorder.setOrientationHint(getCameraDisplayOrientation());
				mRecorder.setMaxDuration(1800000);
				mRecorder.setOnInfoListener(
					new MediaRecorder.OnInfoListener() {
						public void onInfo(MediaRecorder mr, int w, int ex) {
							if (w == MediaRecorder.
								MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)
							{
								restart(false);
							}
						}
					}
				);
				mRecorder.setOnErrorListener(
					new MediaRecorder.OnErrorListener() {
						public void onError(MediaRecorder mr, int w, int ex) {
							restart(true);
						}
					}
				);
				mRecorder.prepare();
				mRecorder.start();
			} catch (Exception e) {
				Log.d(TAG, "Recorder start: " + e.getMessage());
			}
		}
	}

	public void stop() {
		stopRecorder();

		if (mCamera != null) {
			mCamera.lock();
			mCamera.stopPreview();
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
		}
	}

	public void stopRecorder() {
		if (mRecorder != null) {
			try {
				mRecorder.stop();
				mRecorder.reset();
				mRecorder.release();
			} catch (Exception e) {
				Log.d(TAG, "Recorder stop: " + e.getMessage());
			}
			mRecorder = null;
		}
	}

	public void restart(boolean forceOverwrite) {
		stop();
		start();

		Toast.makeText(
			mAct,
			getResources().getString(R.string.restart),
			Toast.LENGTH_SHORT
		).show();
	}

	private int getCameraDisplayOrientation() {
		android.hardware.Camera.CameraInfo info =
			new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(mCameraId, info);
		int rotate = mAct.getWindowManager().getDefaultDisplay().getRotation();

		int degrees = 0;
		switch (rotate) {
			case Surface.ROTATION_0: degrees = 0; break;
			case Surface.ROTATION_90: degrees = 90; break;
			case Surface.ROTATION_180: degrees = 180; break;
			case Surface.ROTATION_270: degrees = 270; break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360;
		}
		else {
			result = (info.orientation - degrees + 360) % 360;
		}

		return result;
	}

	private String getOutputMediaPath() {
		File mediaStorageDir = new File(
			Environment.getExternalStorageDirectory(),
			"BabaRam"
		);

		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				return null;
			}
		}

		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
			.format(new Date());
		File mediaFile = new File(
			mediaStorageDir.getPath() + File.separator + timeStamp + ".mp4"
		);

		return mediaFile.toString();
	}
}

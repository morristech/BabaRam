package com.babaramdashcam;

import android.app.Activity;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.Exception;

public class BabaRamCamera extends SurfaceView
	implements SurfaceHolder.Callback
{
	private static final String TAG = "BabaRamCamera";
	private static final String JPG = ".jpg";
	private static final String MP4 = ".mp4";
	private static final int MAXDURATION = 1200000;
	private static final int MAXHISTORY = 3600000;
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
		deleteOldVideos(false);
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	}

	public void start() {
		if (mCamera == null) {
			try {
				mCamera = Camera.open(mCameraId);
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();
				mCamera.setDisplayOrientation(getCameraOrientation(true));
				mCamera.unlock();
			} catch (Exception e) {
				Log.d(TAG, "Camera start: " + e.getMessage());
				stop();
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
			// Initialize.
			mRecorder = new MediaRecorder();
			mRecorder.setCamera(mCamera);

			// Set sources.
			mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
			mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

			// Set profile.
			mRecorder.setProfile(CamcorderProfile.get(
				mCameraId,
				CamcorderProfile.QUALITY_HIGH
			));

			// Set the rest of the properties.
			mRecorder.setOrientationHint(getCameraOrientation(false));
			mRecorder.setOutputFile(getOutputFile().toString());
			mRecorder.setPreviewDisplay(mHolder.getSurface());
			mRecorder.setMaxDuration(MAXDURATION);
			mRecorder.setOnInfoListener(
				new MediaRecorder.OnInfoListener() {
					public void onInfo(MediaRecorder mr, int w, int ex) {
						if (w == MediaRecorder.
							MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)
						{
							stop();
							deleteOldVideos(false);
							start();
						}
					}
				}
			);
			mRecorder.setOnErrorListener(
				new MediaRecorder.OnErrorListener() {
					public void onError(MediaRecorder mr, int w, int ex) {
						stop();
						deleteOldVideos(true);
						start();
					}
				}
			);

			// Begin recording.
			try {
				mRecorder.prepare();
				mRecorder.start();
			} catch (Exception e) {
				Log.d(TAG, "Recorder start: " + e.getMessage());
				stop();
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

	public void flip() {
		if (Camera.getNumberOfCameras() < 2) {
			return;
		}

		mCameraId = (mCameraId == 0) ? 1 : 0;
		stop();
		start();
	}

	public static void saveThumbnail(File file) {
		File output = getThumbnailFile(file);
		if (output == null) {
			return;
		}

		Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(
			file.toString(),
			MediaStore.Video.Thumbnails.MINI_KIND
		);

		try {
			FileOutputStream out = new FileOutputStream(output);
			bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
			out.flush();
			out.close();
		} catch (Exception e) {}
	}

	public static Bitmap readThumbnail(File file) {
		Bitmap bitmap = null;
		try {
			FileInputStream fis = new FileInputStream(file);
			bitmap = BitmapFactory.decodeFileDescriptor(fis.getFD());
		} catch (Exception e) {}
		return bitmap;
	}

	private void deleteOldVideos(boolean force) {
		// Get all the files in the directory and sort by last modified.
		File[] files = getOutputDir().listFiles();
		Arrays.sort(files, new Comparator<File>(){
			public int compare(File f1, File f2) {
				return Long.valueOf(f1.lastModified())
					.compareTo(f2.lastModified());
			}
		});

		// Get their durations and delete if necessary.
		if (files.length > 0) {
			long[] durations = new long[files.length];
			long totalDuration = 0;
			for (int i = 0; i < files.length; i++) {
				File thumb = getThumbnailFile(files[i]);
				if (!thumb.exists()) {
					saveThumbnail(files[i]);
				}

				try {
					MediaMetadataRetriever mmr = new MediaMetadataRetriever();
					FileInputStream fis = new FileInputStream(files[i]);
					mmr.setDataSource(fis.getFD());
					String duration = mmr.extractMetadata(
						MediaMetadataRetriever.METADATA_KEY_DURATION
					);

					durations[i] = Long.parseLong(duration);
					totalDuration += Long.parseLong(duration);
				} catch (Exception e) {
					files[i].delete();
					if (thumb.exists()) {
						thumb.delete();
					}
				}
			}

			int deleteCount = 0;
			for (int i = 0; i < files.length; i++) {
				if (totalDuration > MAXHISTORY) {
					totalDuration -= durations[i];
					deleteCount++;
				}
			}

			if (deleteCount == 0 && force) {
				deleteCount = 1;
			}

			for (int i = 0; i < deleteCount; i++) {
				files[i].delete();
			}
		}
	}

	public static File getThumbnailFile(File videoFile) {
		File dir = getThumbsDir();
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				return null;
			}
		}

		int index = videoFile.getName().lastIndexOf(".");
		String name = videoFile.getName();
		File output = new File(
			dir, (index > 0 ? name.substring(0, index) : name) + JPG
		);
		return output;
	}

	public static File getVideoFile(File thumbFile) {
		File dir = getOutputDir();
		int index = thumbFile.getName().lastIndexOf(".");
		String name = thumbFile.getName();
		File output = new File(
			dir, (index > 0 ? name.substring(0, index) : name) + MP4
		);
		return output;
	}

	private int getCameraOrientation(boolean display) {
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
		if ((display || degrees != 0) &&
			info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
		{
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360;
		}
		else {
			result = (info.orientation - degrees + 360) % 360;
		}

		return result;
	}

	public static File getOutputDir() {
		return new File(
			Environment.getExternalStorageDirectory(),
			"BabaRam"
		);
	}

	public static File getThumbsDir() {
		return new File(
			Environment.getExternalStorageDirectory(),
			"BabaRam" + File.separator + "thumbs"
		);
	}

	private File getOutputFile() {
		File mediaStorageDir = getOutputDir();
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				return null;
			}
		}

		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
			.format(new Date());
		File mediaFile = new File(mediaStorageDir, timeStamp + MP4);

		return mediaFile;
	}
}

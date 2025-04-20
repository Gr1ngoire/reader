package com.example.reader.services;

import static android.graphics.ImageFormat.YUV_420_888;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.reader.R;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class CameraForegroundService extends Service {
    private static final String TAG = "FaceDetectionService";
    private FrameProcessingService frameProcessingService;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;
    public CameraForegroundService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Context context = getApplicationContext();

            CascadeClassifier faceCascade = loadCascade(context, "haarcascade_frontalface_default.xml");
            CascadeClassifier eyesCascade = loadCascade(context, "haarcascade_eye.xml");
            PupilsDetectionService pupilsDetectionService = new PupilsDetectionService(faceCascade, eyesCascade);

            CommunicationService communicationService = new CommunicationService(this);
            this.frameProcessingService = new FrameProcessingService(pupilsDetectionService, communicationService);

            startForegroundService();
            startBackgroundThread();
            openFrontCamera();
        } catch (IOException error) {
            Log.e(TAG, "Error loading detectors", error);
        } catch (CameraAccessException error) {
            Log.e(TAG, "Can not access the camera", error);
        }
    }

    private void startForegroundService() {
        String notificationChannelId = "eye_tracking";
        String notificationChannelName = "Eye Tracking";
        NotificationChannel channel = new NotificationChannel(notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        String notificationContentTitle = "Camera Processing";
        String notificationContentText = "Processing frames...";
        Notification notification = new Notification.Builder(this, notificationChannelId)
                .setContentTitle(notificationContentTitle)
                .setContentText(notificationContentText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
    }

    private void startBackgroundThread() {
        String backgroundThreadName = "CameraBackground";
        backgroundThread = new HandlerThread(backgroundThreadName);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void openFrontCamera() throws CameraAccessException {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String frontCameraId = getFrontCameraId();
        imageReader = ImageReader.newInstance(640, 480, YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(this::processFrame, backgroundHandler);

        if (frontCameraId == null) {
            throw new IllegalArgumentException("frontCameraId can not be null!");
        }

        cameraManager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                startCameraPreview();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                camera.close();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                camera.close();
            }
        }, backgroundHandler);
    }

    private String getFrontCameraId() throws CameraAccessException {
        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId;
            }
        }
        return null;
    }

    private void startCameraPreview() {
        try {
            CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Failed to build preview");
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to start camera preview");
        }
    }

    private void processFrame(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;

        Mat mat = this.frameProcessingService.convertYUVtoMat(image);
        image.close();

        // Wrap it into a CvCameraViewFrame
        CvCameraFrameWrapper frameWrapper = new CvCameraFrameWrapper(mat);

        this.frameProcessingService.processFrame(frameWrapper);
        image.close();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
        super.onDestroy();
    }
    private CascadeClassifier loadCascade(Context context, String cascadeFileName) throws IOException {
        InputStream is = context.getAssets().open(cascadeFileName);
        File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
        File cascadeFile = new File(cascadeDir, cascadeFileName);

        FileOutputStream fos = new FileOutputStream(cascadeFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        is.close();
        fos.close();

        CascadeClassifier loadedCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
        if (loadedCascade.empty()) {
            Log.e(TAG, "Failed to load cascade classifier");
        }

        Log.d(TAG, "Cascade classifier loaded successfully");
        cascadeDir.delete();

        return loadedCascade;
    }
}

class CvCameraFrameWrapper implements CameraBridgeViewBase.CvCameraViewFrame {
    private final Mat rgbaMat;

    public CvCameraFrameWrapper(Mat mat) {
        this.rgbaMat = mat;
    }

    @Override
    public Mat rgba() {
        return rgbaMat;
    }

    @Override
    public Mat gray() {
        Mat grayMat = new Mat();
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY);
        return grayMat;
    }

    @Override
    public void release() {
        if (rgbaMat != null) {
            rgbaMat.release();
        }
    }
}
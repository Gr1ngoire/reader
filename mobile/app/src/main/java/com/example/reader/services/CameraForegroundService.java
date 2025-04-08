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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.reader.R;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.SimpleBlobDetector;
import org.opencv.features2d.SimpleBlobDetector_Params;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CameraForegroundService extends Service {
    private static final String TAG = "FaceDetectionService";
    private EyesTrackingService eyesTrackingService;
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
            this.eyesTrackingService = new EyesTrackingService(faceCascade, eyesCascade);

        } catch (IOException error) {
            Log.e(TAG, "Error loading detectors", error);
        }

        startForegroundService();
        startBackgroundThread();
        openFrontCamera();
    }

    private void startForegroundService() {
        NotificationChannel channel = new NotificationChannel("eye_tracking", "Eye Tracking", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this, "eye_tracking")
                .setContentTitle("Camera Processing")
                .setContentText("Processing frames...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void openFrontCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            String frontCameraId = getFrontCameraId();
            imageReader = ImageReader.newInstance(640, 480, YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(image -> processFrame(image), backgroundHandler);

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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void processFrame(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;

        Mat mat = convertYUVtoMat(image);
        image.close();

        // Wrap it into a CvCameraViewFrame
        CvCameraFrameWrapper frameWrapper = new CvCameraFrameWrapper(mat);

        processOpenCVFrame(frameWrapper);
        image.close();
    }

    private Mat convertYUVtoMat(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        // Get row strides and pixel strides
        int yRowStride = yPlane.getRowStride();
        int uvRowStride = uPlane.getRowStride(); // U and V have same stride
        int uvPixelStride = uPlane.getPixelStride();

        // Create a Mat for YUV
        Mat yuvMat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        byte[] yuvData = new byte[yuvMat.rows() * yuvMat.cols()];
        int pos = 0;

        // Copy Y plane
        for (int row = 0; row < height; row++) {
            yBuffer.position(row * yRowStride);
            yBuffer.get(yuvData, pos, width);
            pos += width;
        }

        // Copy UV planes with proper handling of pixel stride
        for (int row = 0; row < height / 2; row++) {
            uBuffer.position(row * uvRowStride);
            vBuffer.position(row * uvRowStride);

            for (int col = 0; col < width / 2; col++) {
                yuvData[pos++] = vBuffer.get(); // V
                yuvData[pos++] = uBuffer.get(); // U
            }
        }

        // Put into Mat
        yuvMat.put(0, 0, yuvData);

        // Convert to RGBA
        Mat rgbMat = new Mat();
        // For my phone
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_I420);
        // For usb web cam
//        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGBA_NV21);

        // Rotate to correct orientation
        Mat rotatedMat = new Mat();
        // For my phone
        Core.rotate(rgbMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE);
        // For usb web cam
//        Core.rotate(rgbMat, rotatedMat, Core.ROTATE_90_CLOCKWISE);

        return rotatedMat;
    }

    private void processOpenCVFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat frame = new Mat();
        inputFrame.rgba().copyTo(frame);

        Rect[] faces = this.eyesTrackingService.detectFaces(frame);
        for (Rect face : faces) {
            Imgproc.rectangle(frame, face.tl(), face.br(), new Scalar(255, 0, 0), 2);

            Mat faceFrame = frame.submat(face);

            // Detect eyes within the face
            Rect[] eyes = this.eyesTrackingService.detectEyes(faceFrame);
            for (Rect eye : eyes) {
                Imgproc.rectangle(faceFrame, eye.tl(), eye.br(), new Scalar(0, 255, 0), 2);

                Mat eyeFrame = faceFrame.submat(eye);

                // Cut eyebrows and process pupils
                Mat eyeWithoutBrows = this.cutEyebrows(eyeFrame);
                MatOfKeyPoint pupils = this.eyesTrackingService.detectPupils(eyeWithoutBrows, eye);
                sendPupilData(pupils, eye, face);

                for (KeyPoint pupil : pupils.toArray()) {
                    Point pupilCenter = new Point(pupil.pt.x, pupil.pt.y);
                    Imgproc.circle(eyeWithoutBrows, pupilCenter, 10, new Scalar(0, 255, 0), 2);
                }
            }
        }


    }

    private void sendPupilData(MatOfKeyPoint pupils, Rect eye, Rect face) {
        Intent intent = new Intent("PUPIL_MOVEMENT");
        double eyeCenterY = face.y + eye.y + ((double) eye.height / 3.4);

        for (KeyPoint keyPoint : pupils.toList()) {
            double pupilY = face.y + eye.y + keyPoint.pt.y;

            // Send the offset values using Broadcast
            intent.putExtra("pupilY", (float) pupilY);
            intent.putExtra("eyeLineY", (float) eyeCenterY);
            //Toast.makeText(this, "PUPIL EYE: " + pupilY + " " + eyeCenterY, Toast.LENGTH_SHORT).show();
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }

        if (pupils.toList().toArray().length == 0) {
            intent.putExtra("pupilY", 0);
            intent.putExtra("eyeLineY", (float) eyeCenterY);
        }
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

    public Mat cutEyebrows(Mat eyeFrame) {
        int height = eyeFrame.rows();
        int eyebrowHeight = height / 4;
        return eyeFrame.submat(eyebrowHeight, height, 0, eyeFrame.cols());
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
    private SimpleBlobDetector loadPupilDetector() {
        SimpleBlobDetector_Params params = new SimpleBlobDetector_Params();
        params.set_filterByArea(true);
        params.set_maxArea(1500);

        return SimpleBlobDetector.create(params);
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
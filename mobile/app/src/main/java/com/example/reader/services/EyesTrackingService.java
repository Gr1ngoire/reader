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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.reader.R;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
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
import org.opencv.videoio.VideoCapture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EyesTrackingService extends Service {
    private static final String TAG = "FaceDetectionService";
    private CascadeClassifier faceCascade;
    private CascadeClassifier eyesCascade;
    private SimpleBlobDetector pupilDetector;
    private CameraBridgeViewBase cameraView;
    private VideoCapture videoCapture;
    private Handler handler;
    private boolean isRunning = false;
    private final int CAMERA_INDEX = 1;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private ImageReader imageReader;
    public EyesTrackingService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Context context = getApplicationContext();
            this.faceCascade = loadCascade(context, "haarcascade_frontalface_default.xml");
            this.eyesCascade = loadCascade(context, "haarcascade_eye.xml");
            this.pupilDetector = loadPupilDetector();
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
                Log.e("CAMERA PERMISSION", "NOT GRANTED BIIIIIIITCH");
                return; // Don't proceed if permission isn't granted
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

        Rect[] faces = this.detectFaces(frame);
        for (Rect face : faces) {
            Imgproc.rectangle(frame, face.tl(), face.br(), new Scalar(255, 0, 0), 2);

            Mat faceFrame = frame.submat(face);

            // Detect eyes within the face
            Rect[] eyes = this.detectEyes(faceFrame);
            for (Rect eye : eyes) {
                Imgproc.rectangle(faceFrame, eye.tl(), eye.br(), new Scalar(0, 255, 0), 2);

                Mat eyeFrame = faceFrame.submat(eye);
//                Log.d("CameraActivity", "Detected eyes: " + eyes.length);

                // Cut eyebrows and process pupils
                Mat eyeWithoutBrows = this.cutEyebrows(eyeFrame);
                MatOfKeyPoint pupils = this.detectPupils(eyeWithoutBrows, eye);

                if (pupils.toArray().length > 0) {
//                    Log.d("CameraActivity", "Detected pupils: " + pupils.toArray().length);
                    sendPupilData(pupils, eye, face);
                }

                for (KeyPoint pupil : pupils.toArray()) {
                    Point pupilCenter = new Point(pupil.pt.x, pupil.pt.y);
                    Imgproc.circle(eyeWithoutBrows, pupilCenter, 10, new Scalar(0, 255, 0), 2);
                }
            }
        }


    }

    private void sendPupilData(MatOfKeyPoint pupils, Rect eye, Rect face) {
        for (KeyPoint keyPoint : pupils.toList()) {
            double pupilY = face.y + eye.y + keyPoint.pt.y;
            double eyeCenterY = face.y + eye.y + ((double) eye.height / 3.3);

//            Log.d("EYE PARTS", eye.y + " " + eye.height);
//            Log.d("FACE PARTS", face.y + " " + face.height);

            // Send the offset values using Broadcast
            Intent intent = new Intent("PUPIL_MOVEMENT");
            intent.putExtra("pupilY", (float) pupilY);
            intent.putExtra("eyeLineY", (float) eyeCenterY);
            //Toast.makeText(this, "PUPIL EYE: " + pupilY + " " + eyeCenterY, Toast.LENGTH_SHORT).show();
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
        isRunning = false;
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
        super.onDestroy();
    }
    public Rect[] detectFaces(Mat inputFrame) {
        MatOfRect faces = new MatOfRect();
        int cameraViewWidth = inputFrame.cols();
        int cameraViewHeight = inputFrame.rows();

        faceCascade.detectMultiScale(inputFrame, faces, 1.05, 3, 2, new Size(30, 30), new Size());

        double minFaceWidth = cameraViewWidth * 0.4;
        double minFaceHeight = cameraViewHeight * 0.4;

        return Arrays.stream(faces.toArray())
                .filter(face -> face.width >= minFaceWidth && face.height >= minFaceHeight)
                .toArray(Rect[]::new);
    }

    private boolean isOverlapping(Rect r1, Rect r2) {
        return !(r1.x + r1.width < r2.x || r2.x + r2.width < r1.x || r1.y + r1.height < r2.y || r2.y + r2.height < r1.y);
    }
    public Rect[] detectEyes(Mat faceFrame) {
        Mat gray = new Mat();
        // Define a region that covers the upper part of the face frame
        int roiHeight = faceFrame.rows() / 2;  // Get the upper half of the face
        Mat upperFace = new Mat(faceFrame, new Rect(0, 0, faceFrame.cols(), roiHeight));

        Imgproc.cvtColor(upperFace, gray, Imgproc.COLOR_BGR2GRAY);
        MatOfRect eyes = new MatOfRect();
        eyesCascade.detectMultiScale(gray, eyes, 1.1, 2, 0, new Size(15, 15), new Size());

        Rect[] detectedEyes = eyes.toArray();
        List<Rect> filteredEyes = new ArrayList<>();

        for (Rect detectedEye : detectedEyes) {
            boolean isDuplicate = false;

            // Check for overlap with previously added eyes
            for (Rect existingEye : filteredEyes) {
                // If the detected eye overlaps with any previously added eye, skip it
                if (isOverlapping(existingEye, detectedEye)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                // Add the valid eye (up to a maximum of 2 eyes)
                filteredEyes.add(detectedEye);
                if (filteredEyes.size() == 2) {
                    break;  // Stop after detecting two valid eyes
                }
            }
        }

        return filteredEyes.toArray(new Rect[0]);
    }

    public MatOfKeyPoint detectPupils(Mat eyeFrame, Rect eye) {
        Point eyeCenter = new Point(eye.x + eye.width / 2.0, eye.y + eye.height / 2.0);
        Mat gray = new Mat();
        Imgproc.cvtColor(eyeFrame, gray, Imgproc.COLOR_BGR2GRAY);

        double meanBrightness = Core.mean(gray).val[0];
        double clipLimit = meanBrightness < 50 ? 5.0 : 1.5;

        Imgproc.equalizeHist(gray, gray);

        Mat claheOutput = new Mat();
        CLAHE clahe = Imgproc.createCLAHE();
        clahe.setClipLimit(clipLimit); // Adjust contrast limit
        clahe.setTilesGridSize(new Size(8, 8)); // Adjust grid size
        clahe.apply(gray, claheOutput);

        Mat adaptiveThresholded = new Mat();
        Imgproc.adaptiveThreshold(claheOutput, adaptiveThresholded, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV, 15, 5);

        Imgproc.erode(adaptiveThresholded, adaptiveThresholded, new Mat(), new Point(-1, -1), 2);
        Imgproc.dilate(adaptiveThresholded, adaptiveThresholded, new Mat(), new Point(-1, -1), 4);
        Imgproc.medianBlur(adaptiveThresholded, adaptiveThresholded, 5);

        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        pupilDetector.detect(adaptiveThresholded, keyPoints);

        // Step 7: Filter the detected keypoints based on proximity to the eye center
        List<KeyPoint> filteredKeyPoints = new ArrayList<>();
        for (KeyPoint keyPoint : keyPoints.toList()) {
            // Calculate the distance of the keypoint from the eye center
            double distance = Math.sqrt(Math.pow(keyPoint.pt.x - eyeCenter.x, 2) +
                    Math.pow(keyPoint.pt.y - eyeCenter.y, 2));
//            Log.d("DISTANCO", "" + distance);
            // Keep only the keypoints that are within a certain distance from the eye center
//            if (distance < 400) {  // Adjust this threshold as necessary
//                filteredKeyPoints.add(keyPoint);
//            }
            filteredKeyPoints.add(keyPoint);
        }

        // Step 8: Sort the keypoints by size (larger blobs are more likely to be the pupil)
        filteredKeyPoints.sort((k1, k2) -> Double.compare(k2.size, k1.size));

        // Step 9: Limit the number of detected pupils to 2 (one for each eye)
        MatOfKeyPoint filteredKeypointsMat = new MatOfKeyPoint();
        if (filteredKeyPoints.size() > 2) {
            // Keep the largest two keypoints (pupils)
            filteredKeyPoints = filteredKeyPoints.subList(0, 2);
        }
        filteredKeypointsMat.fromList(filteredKeyPoints);

        // Return the keypoints of the detected pupils
        return filteredKeypointsMat;
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
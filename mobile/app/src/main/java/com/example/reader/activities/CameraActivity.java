package com.example.reader.activities;

import static org.opencv.videoio.Videoio.CAP_ANDROID;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.reader.R;
import com.example.reader.services.EyesTrackingService;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

public class CameraActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private EyesTrackingService eyesTrackingService;
    private VideoCapture videoCapture;
    private Mat frame;
    private ImageView imageView;
    private CameraBridgeViewBase openCvCameraView;
    private Handler handler;
    private boolean isCameraRunning = false;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private final int CAMERA_INDEX = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            return;
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully");
        }

        imageView = findViewById(R.id.cameraFrame);
        openCvCameraView = findViewById(R.id.cameraView);

        frame = new Mat();
        handler = new Handler(Looper.getMainLooper());
        this.eyesTrackingService = new EyesTrackingService(this);

        // Request Camera Permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            Log.d("CameraActivity", "Camera permission already granted");
            new Handler().postDelayed(this::startCamera, 1000);
        }

        openCvCameraView.setCameraIndex(1);
        openCvCameraView.setCvCameraViewListener(this);
        openCvCameraView.setCameraPermissionGranted();
        openCvCameraView.enableView();


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("CameraActivity", "Camera permission granted!");
                // Initialize VideoCapture (Ensure the correct camera index)
                new Handler().postDelayed(this::startCamera, 1000);
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        Log.d("CameraActivity", "Attempting to open camera...");

        videoCapture = new VideoCapture(CAMERA_INDEX, CAP_ANDROID);

        if (videoCapture.isOpened()) {
            Log.d("CameraActivity", "Camera opened successfully");
        } else {
            Log.e("CameraActivity", "Failed to open camera");
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        inputFrame.rgba().copyTo(frame);

        // Detect faces
        Rect[] faces = eyesTrackingService.detectFaces(frame);
        Log.d("CameraActivity", "Detected faces: " + faces.length);
        for (Rect face : faces) {
            Imgproc.rectangle(frame, face.tl(), face.br(), new Scalar(255, 0, 0), 2);

            Mat faceFrame = frame.submat(face);

            // Detect eyes within the face
            Rect[] eyes = eyesTrackingService.detectEyes(faceFrame);
            for (Rect eye : eyes) {
                Imgproc.rectangle(faceFrame, eye.tl(), eye.br(), new Scalar(0, 255, 0), 2);

                Mat eyeFrame = faceFrame.submat(eye);
                Log.d("CameraActivity", "Detected eyes: " + eyes.length);

                // Cut eyebrows and process pupils
                Mat eyeWithoutBrows = eyesTrackingService.cutEyebrows(eyeFrame);
                MatOfKeyPoint pupils = eyesTrackingService.detectPupils(eyeWithoutBrows, eye);

                Log.d("CameraActivity", "Detected pupils: " + pupils.toArray().length);

                // Log pupil positions
                Log.d("Frame processing", "Detected pupils: " + pupils.toArray().length);

                for (KeyPoint pupil : pupils.toArray()) {
                    Point pupilCenter = new Point(pupil.pt.x, pupil.pt.y);
                    Imgproc.circle(eyeWithoutBrows, pupilCenter, 10, new Scalar(0, 255, 0), 2);
                }
            }
        }

        Log.d("DEBUG", "Frame processed");

        Bitmap bitmapToDisplay = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frame, bitmapToDisplay);
        runOnUiThread(() -> imageView.setImageBitmap(bitmapToDisplay));

        return frame;
    }

    @Override
    protected void onPause() {
        super.onPause();

        openCvCameraView.disableView();
        if (videoCapture != null) {
            if (videoCapture.isOpened()) {
                videoCapture.release();
                videoCapture = null;
            }
            isCameraRunning = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        openCvCameraView.disableView();
        if (videoCapture != null) {
            videoCapture.release();
            videoCapture = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        openCvCameraView.enableView();
        if (videoCapture == null) {
            videoCapture = new VideoCapture(CAMERA_INDEX); // Ensure it's properly created
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        frame = new Mat(height, width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        frame.release();
    }
}

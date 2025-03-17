package com.example.reader.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EyesTrackingService {
    private static final String TAG = "FaceDetectionService";
    private CascadeClassifier faceCascade;
    private CascadeClassifier eyesCascade;
    private SimpleBlobDetector pupilDetector;
    public EyesTrackingService(Context context) {
        try {
            this.faceCascade = loadCascade(context, "haarcascade_frontalface_default.xml");
            this.eyesCascade = loadCascade(context, "haarcascade_eye.xml");
            this.pupilDetector = loadPupilDetector();
        } catch (IOException error) {
            Log.e(TAG, "Error loading detectors", error);
        }
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

    private void displayProcessedImage(Mat processedMat, ImageView imageView) {
        // Convert Mat to Bitmap
        Bitmap bitmap = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(processedMat, bitmap);

        // Set the Bitmap to ImageView
        imageView.post(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(bitmap);
            }
        });
    }

    public MatOfKeyPoint detectPupils(Mat eyeFrame, Rect eye, ImageView imageView) {
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
        displayProcessedImage(claheOutput, imageView);

        Mat adaptiveThresholded = new Mat();
        Imgproc.adaptiveThreshold(claheOutput, adaptiveThresholded, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV, 15, 5);

//        Core.bitwise_not(adaptiveThresholded, adaptiveThresholded);
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
            Log.d("DISTANCO", "" + distance);
            // Keep only the keypoints that are within a certain distance from the eye center
            if (distance < 270) {  // Adjust this threshold as necessary
                filteredKeyPoints.add(keyPoint);
            }
//            filteredKeyPoints.add(keyPoint);
//            if (eye.contains(new Point(keyPoint.pt.x, keyPoint.pt.y))) {  // Ensure the keypoint is inside the detected eye region
//                filteredKeyPoints.add(keyPoint);
//            }
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

//        params.set_minArea(10); // You can adjust this value to suit the pupil size
//        params.set_maxArea(1500);
//        // Filter by circularity (pupil is generally round)
//        params.set_filterByCircularity(false);
//        params.set_minCircularity(0.6f); // Higher values are more circular
//        // Filter by convexity (pupil is convex)
//        params.set_filterByConvexity(true);
//        params.set_minConvexity(0.8f);
//
//        // Filter by inertia (optional, can help reject non-pupil blobs)
//        params.set_filterByInertia(true);
//        params.set_minInertiaRatio(0.5f);

        return SimpleBlobDetector.create(params);
    }
}

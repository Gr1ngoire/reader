package com.example.reader.services;

import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PupilsDetectionService {
    private final CascadeClassifier faceCascade;
    private final CascadeClassifier eyesCascade;
    public PupilsDetectionService(CascadeClassifier faceCascade, CascadeClassifier eyesCascade) {
        this.faceCascade = faceCascade;
        this.eyesCascade = eyesCascade;
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
        // Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(eyeFrame, gray, Imgproc.COLOR_BGR2GRAY);

        // Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(gray, gray, new Size(7, 7), 0);

        // Apply inverse thresholding to highlight dark pupils
        Imgproc.threshold(gray, gray, 30, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(gray, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find the largest circular contour (likely the pupil)
        double maxArea = 0;
        Point bestPupilCenter = null;
        float bestPupilRadius = 0;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > 50 && area < 5000) { // Filter out noise and large objects
                // Fit an enclosing circle around the contour
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                Point center = new Point();
                float[] radius = new float[1];
                Imgproc.minEnclosingCircle(contour2f, center, radius);

                // Ensure the detected region is roughly circular
                double circularity = 4 * Math.PI * (area / (Math.pow(radius[0] * 2, 2)));
                if (circularity > 0.5 && area > maxArea) {
                    maxArea = area;
                    bestPupilCenter = center;
                    bestPupilRadius = radius[0];
                }
            }
        }

        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        if (bestPupilCenter == null) {
            return keypoints;
        }

        double distance = Math.sqrt(
                Math.pow(bestPupilCenter.x - eyeCenter.x, 2) +
                        Math.pow(bestPupilCenter.y - eyeCenter.y, 2));

        // Keep only the keypoints that are within a certain distance from the eye center
        int maximalPupilDistanceToEyeCenter = 200;
        if (distance > maximalPupilDistanceToEyeCenter) {
            return keypoints;
        }

        // Store detected pupil as a keypoint
        KeyPoint keypoint = new KeyPoint((float) bestPupilCenter.x, (float) bestPupilCenter.y, bestPupilRadius * 2);
        keypoints.fromArray(keypoint);

        return keypoints;
    }
}

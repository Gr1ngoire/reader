package com.example.reader.services;

import android.media.Image;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;

public class FrameProcessingService {
    private final PupilsDetectionService pupilsDetectionService;
    private final CommunicationService communicationService;
    public FrameProcessingService(PupilsDetectionService pupilsDetectionService, CommunicationService communicationService) {
        this.pupilsDetectionService = pupilsDetectionService;
        this.communicationService = communicationService;
    }
    public Mat convertYUVtoMat(Image image) {
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
        int uvRowStride = uPlane.getRowStride();
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

    public void processFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat frame = new Mat();
        inputFrame.rgba().copyTo(frame);

        Rect[] faces = this.pupilsDetectionService.detectFaces(frame);

        if (faces.length == 0) {
            this.communicationService.sendPupilPresenceData(false);
        }

        for (Rect face : faces) {
            Imgproc.rectangle(frame, face.tl(), face.br(), new Scalar(255, 0, 0), 2);

            Mat faceFrame = frame.submat(face);

            // Detect eyes within the face
            Rect[] eyes = this.pupilsDetectionService.detectEyes(faceFrame);
            for (Rect eye : eyes) {
                Imgproc.rectangle(faceFrame, eye.tl(), eye.br(), new Scalar(0, 255, 0), 2);

                Mat eyeFrame = faceFrame.submat(eye);

                // Cut eyebrows and process pupils
                Mat eyeWithoutBrows = this.cutEyebrows(eyeFrame);
                MatOfKeyPoint pupils = this.pupilsDetectionService.detectPupils(eyeWithoutBrows, eye);
                this.communicationService.sendPupilData(pupils, eye, face);
                this.communicationService.sendPupilPresenceData(pupils.toArray().length > 0);

                for (KeyPoint pupil : pupils.toArray()) {
                    Point pupilCenter = new Point(pupil.pt.x, pupil.pt.y);
                    Imgproc.circle(eyeWithoutBrows, pupilCenter, 10, new Scalar(0, 255, 0), 2);
                }
            }
        }
    }

    private Mat cutEyebrows(Mat eyeFrame) {
        int height = eyeFrame.rows();
        int eyebrowHeight = height / 4;
        return eyeFrame.submat(eyebrowHeight, height, 0, eyeFrame.cols());
    }
}

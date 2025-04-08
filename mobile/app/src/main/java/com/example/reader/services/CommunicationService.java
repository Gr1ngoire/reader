package com.example.reader.services;

import static com.example.reader.CommunicationConstants.EYE_CENTER_ORDINATE_PARAMETER_NAME;
import static com.example.reader.CommunicationConstants.PUPIL_MOVEMENT_INTENT_NAME;
import static com.example.reader.CommunicationConstants.PUPIL_ORDINATE_PARAMETER_NAME;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.opencv.core.KeyPoint;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Rect;

public class CommunicationService {
    private final Context context;
    public CommunicationService(Context context) {
        this.context = context;
    }
    public void sendPupilData(MatOfKeyPoint pupils, Rect eye, Rect face) {
        Intent intent = new Intent(PUPIL_MOVEMENT_INTENT_NAME);
        double eyeCenterY = face.y + eye.y + ((double) eye.height / 3.4);

        for (KeyPoint keyPoint : pupils.toList()) {
            double pupilY = face.y + eye.y + keyPoint.pt.y;

            // Send the offset values using Broadcast
            intent.putExtra(PUPIL_ORDINATE_PARAMETER_NAME, (float) pupilY);
            intent.putExtra(EYE_CENTER_ORDINATE_PARAMETER_NAME, (float) eyeCenterY);
            LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
        }

        if (pupils.toList().toArray().length == 0) {
            intent.putExtra(PUPIL_ORDINATE_PARAMETER_NAME, 0);
            intent.putExtra(EYE_CENTER_ORDINATE_PARAMETER_NAME, (float) eyeCenterY);
        }
    }
}

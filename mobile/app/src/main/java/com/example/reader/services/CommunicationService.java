package com.example.reader.services;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.opencv.core.KeyPoint;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Rect;

public class CommunicationService {
    public static final String PUPIL_MOVEMENT_INTENT_NAME = "PUPIL_MOVEMENT";
    public static final String PUPIL_ORDINATE_PARAMETER_NAME = "PUPIL_Y";
    public static final String EYE_CENTER_ORDINATE_PARAMETER_NAME = "EYE_CENTER_Y";

    public static final String PUPIL_DETECTION_INTENT_NAME = "PUPIL_DETECTION_INTENT_NAME";
    public static final String PUPIL_PRESENCE_PARAMETER_NAME = "IS_PUPIL_PRESENT";
    private final Context context;
    public CommunicationService(Context context) {
        this.context = context;
    }
    public void sendPupilData(MatOfKeyPoint pupils, Rect eye, Rect face) {
        Intent intent = new Intent(PUPIL_MOVEMENT_INTENT_NAME);
        double eyeCenterY = this.prepareEyeCenterOrdinate(eye, face);

        for (KeyPoint keyPoint : pupils.toList()) {
            double pupilY = this.preparePupilCenterOrdinate(keyPoint, eye, face);

            // Send the offset values using Broadcast
            intent.putExtra(PUPIL_ORDINATE_PARAMETER_NAME, (float) pupilY);
            intent.putExtra(EYE_CENTER_ORDINATE_PARAMETER_NAME, (float) eyeCenterY);
            LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
        }

        if (pupils.toList().toArray().length == 0) {
            intent.putExtra(PUPIL_ORDINATE_PARAMETER_NAME, 0);
            intent.putExtra(EYE_CENTER_ORDINATE_PARAMETER_NAME, (float) eyeCenterY);
            LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
        }
    }

    public void sendPupilPresenceData(boolean isPupilPresent) {
        Intent intent = new Intent(PUPIL_DETECTION_INTENT_NAME);
        intent.putExtra(PUPIL_PRESENCE_PARAMETER_NAME, isPupilPresent);
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
    }

    private double prepareEyeCenterOrdinate(Rect eye, Rect face) {
        return face.y + eye.y + ((double) eye.height / 3.4);
    }

    private double preparePupilCenterOrdinate(KeyPoint pupil, Rect eye, Rect face) {
        return face.y + eye.y + pupil.pt.y;
    }
}

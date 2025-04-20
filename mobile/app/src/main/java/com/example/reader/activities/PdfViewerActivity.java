package com.example.reader.activities;

import static com.example.reader.CommunicationConstants.EYE_CENTER_ORDINATE_PARAMETER_NAME;
import static com.example.reader.CommunicationConstants.PUPIL_DETECTION_INTENT_NAME;
import static com.example.reader.CommunicationConstants.PUPIL_MOVEMENT_INTENT_NAME;
import static com.example.reader.CommunicationConstants.PUPIL_ORDINATE_PARAMETER_NAME;
import static com.example.reader.CommunicationConstants.PUPIL_PRESENCE_PARAMETER_NAME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.reader.R;
import com.example.reader.services.CameraForegroundService;
import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;

public class PdfViewerActivity extends AppCompatActivity {
    private PDFView pdfView;
    private float previousPupilY = 0;

    private final BroadcastReceiver pupilMovementReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float pupilY = intent.getFloatExtra(PUPIL_ORDINATE_PARAMETER_NAME, 0);
            float eyeLineY = intent.getFloatExtra(EYE_CENTER_ORDINATE_PARAMETER_NAME, 0);

            // Scroll the PDF based on detected pupil movement
            scrollPdf(pupilY, eyeLineY);
        }
    };

    private final BroadcastReceiver pupilPresenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isPupilPresent = intent.getBooleanExtra(PUPIL_PRESENCE_PARAMETER_NAME, false);

            this.displayEyeIconOnDetectedPupil(isPupilPresent);
        }

        private void displayEyeIconOnDetectedPupil(boolean isPupilPresent) {
            ImageView eyeIcon = findViewById(R.id.eyeIcon);
            runOnUiThread(() -> {
                if (isPupilPresent) {
                    eyeIcon.setVisibility(View.VISIBLE);
                } else {
                    eyeIcon.setVisibility(View.GONE);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        pdfView = findViewById(R.id.pdfView);
        Intent intent = getIntent();
        String filePath = intent.getStringExtra("filePath");
        File file = new File(filePath);

        try {
            pdfView.fromFile(file).enableSwipe(true).enableDoubletap(true).load();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(pupilMovementReceiver, new IntentFilter(PUPIL_MOVEMENT_INTENT_NAME));
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilPresenceReceiver, new IntentFilter(PUPIL_DETECTION_INTENT_NAME));
        Intent serviceIntent = new Intent(this, CameraForegroundService.class);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilMovementReceiver, new IntentFilter(PUPIL_MOVEMENT_INTENT_NAME));
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilPresenceReceiver, new IntentFilter(PUPIL_DETECTION_INTENT_NAME));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pupilMovementReceiver);
        unregisterReceiver(pupilPresenceReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pupilMovementReceiver);
        unregisterReceiver(pupilPresenceReceiver);
    }

    private void scrollPdf(float pupilY, float eyeLineY) {
        float pupilYToUse = pupilY == 0 ? this.previousPupilY : pupilY;
        float deltaByEyeLine = eyeLineY - pupilYToUse;
        this.previousPupilY = pupilYToUse;

        Toast.makeText(this, "DELTA" + " " + deltaByEyeLine, Toast.LENGTH_SHORT).show();

        WindowMetrics metrics = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getCurrentWindowMetrics();
        Rect bounds = metrics.getBounds();
        float screenHeight = bounds.height();

        float lowerBound = screenHeight / (float) -564.7;
        // -3.4
        if (deltaByEyeLine <= 0 && deltaByEyeLine >= -3.4) {
            return;
        }

        float upperBound = screenHeight / 768;
        // 2.5
        if (deltaByEyeLine >= 0 && deltaByEyeLine <= 2.5) {
            return;
        }

        pdfView.moveRelativeTo(0, deltaByEyeLine * 3); // Adjust sensitivity factor
        pdfView.post(() -> pdfView.loadPages());
    }
}

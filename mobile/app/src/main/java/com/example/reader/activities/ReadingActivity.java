package com.example.reader.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.reader.R;
import com.example.reader.services.CameraForegroundService;
import com.example.reader.services.CommunicationService;
import com.example.reader.services.ReadProgressService;
import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;

public class ReadingActivity extends AppCompatActivity {
    private float previousEyeY = 0;
    private float previousPupilY = 0;
    private PDFView pdfView;
    private ReadProgressService readProgressService;

    private final BroadcastReceiver pupilMovementReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float pupilY = intent.getFloatExtra(CommunicationService.PUPIL_ORDINATE_PARAMETER_NAME, 0);
            float eyeLineY = intent.getFloatExtra(CommunicationService.EYE_CENTER_ORDINATE_PARAMETER_NAME, 0);

            // Scroll the PDF based on detected pupil movement
            scrollPdf(pupilY, eyeLineY);
        }
    };

    private final BroadcastReceiver pupilPresenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isPupilPresent = intent.getBooleanExtra(CommunicationService.PUPIL_PRESENCE_PARAMETER_NAME, false);

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

        this.readProgressService = new ReadProgressService(this);
        int lastPage = this.readProgressService.getLastReadPage(filePath);

        try {
            pdfView.fromFile(file).defaultPage(lastPage).enableSwipe(true).enableDoubletap(true).onLoad(allPages -> {
                this.readProgressService.saveAllPagesCount(filePath, allPages);
                ImageButton backButton = findViewById(R.id.back_button);
                backButton.setOnClickListener(v -> {
                    int currentPage = pdfView.getCurrentPage();
                    this.readProgressService.saveLastReadPage(filePath, currentPage);
                    finish();
                });
            }).load();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilMovementReceiver, new IntentFilter(CommunicationService.PUPIL_MOVEMENT_INTENT_NAME));
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilPresenceReceiver, new IntentFilter(CommunicationService.PUPIL_DETECTION_INTENT_NAME));
        Intent serviceIntent = new Intent(this, CameraForegroundService.class);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilMovementReceiver, new IntentFilter(CommunicationService.PUPIL_MOVEMENT_INTENT_NAME));
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilPresenceReceiver, new IntentFilter(CommunicationService.PUPIL_DETECTION_INTENT_NAME));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pupilMovementReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pupilPresenceReceiver);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pupilMovementReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pupilPresenceReceiver);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void scrollPdf(float pupilY, float eyeY) {
        float pupilYToUse = pupilY == 0 ? this.previousPupilY : pupilY;
        float eyeYToUse = this.previousEyeY == 0 ? eyeY : (eyeY + this.previousEyeY) / 2;
        if (pupilY == 0) {
            eyeYToUse = this.previousEyeY;
        }

        float deltaByEyeLine = eyeYToUse - pupilYToUse;

        this.previousPupilY = pupilYToUse;
        this.previousEyeY = eyeYToUse;

        //Toast.makeText(this, "DELTA " + deltaByEyeLine + " EYE " + eyeY + " PUPIL " + pupilY, Toast.LENGTH_SHORT).show();

        WindowMetrics metrics = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getCurrentWindowMetrics();
        Rect bounds = metrics.getBounds();
        float screenHeight = bounds.height();

        float lowerBound = screenHeight / (float) -564.7;
        // -3.4
        if (deltaByEyeLine <= 0 && deltaByEyeLine >= -3) {
            return;
        }

        float upperBound = screenHeight / 768;
        // 2.5
        if (deltaByEyeLine >= 0 && deltaByEyeLine <= 2.5) {
            return;
        }

        pdfView.moveRelativeTo(0, -deltaByEyeLine * 2); // Adjust sensitivity factor
        pdfView.post(() -> pdfView.loadPages());
    }
}

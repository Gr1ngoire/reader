package com.example.reader.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.reader.R;
import com.example.reader.services.EyesTrackingService;
import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;

public class PdfViewerActivity extends AppCompatActivity {
    private PDFView pdfView;
    private float eyesLine = 0;
    private float previousPupilY = 0;

    private final BroadcastReceiver pupilMovementReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float pupilY = intent.getFloatExtra("pupilY", 0);
            float eyeLineY = intent.getFloatExtra("eyeLineY", 0);

            // Scroll the PDF based on detected pupil movement
            scrollPdf(pupilY, eyeLineY);
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

        LocalBroadcastManager.getInstance(this).registerReceiver(pupilMovementReceiver, new IntentFilter("PUPIL_MOVEMENT"));
        Intent serviceIntent = new Intent(this, EyesTrackingService.class);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(pupilMovementReceiver, new IntentFilter("PUPIL_MOVEMENT"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pupilMovementReceiver);
        this.eyesLine = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pupilMovementReceiver);
        this.eyesLine = 0;
    }

    private void scrollPdf(float pupilY, float eyeLineY) {
        float pupilYToUse = pupilY == 0 ? this.previousPupilY : pupilY;
        //float previousEyeLine = this.eyesLine > 0 ? this.eyesLine : eyeLineY;
        //float deltaByPreviousEyeLine = previousEyeLine - this.previousPupilY;
        //this.eyesLine = (previousEyeLine + eyeLineY) / 2;
        float deltaByEyeLine = -(eyeLineY - pupilYToUse);
        this.previousPupilY = pupilYToUse;

        Toast.makeText(this, "DELTA" + " " + deltaByEyeLine, Toast.LENGTH_SHORT).show();

        // Limit abs diff between previousDeltaByEyeLine and deltaByEyeLine

//        float result = Math.abs(deltaByEyeLine - deltaByPreviousEyeLine);
//        if (result <= 0.5) {
//            return;
//        }

        WindowMetrics metrics = ((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getCurrentWindowMetrics();
        Rect bounds = metrics.getBounds();
        float screenHeight = bounds.height();

        float lowerBound = screenHeight / (float) -564.7;
        // -3.4
        if (deltaByEyeLine <= 0 && deltaByEyeLine >= lowerBound) {
            return;
        }

        float upperBound = screenHeight / 768;
        // 2.5
        if (deltaByEyeLine >= 0 && deltaByEyeLine <= upperBound) {
            return;
        }

        //Log.d("PUPIL Y", this.pupilY + "");
        //Log.d("EYE LINE", this.eyesLine + "");

//        Toast.makeText(this, "DELTA" + " " + deltaByEyeLine, Toast.LENGTH_SHORT).show();
        pdfView.moveRelativeTo(0, deltaByEyeLine * 3); // Adjust sensitivity factor
        pdfView.post(() -> pdfView.loadPages());
    }
}

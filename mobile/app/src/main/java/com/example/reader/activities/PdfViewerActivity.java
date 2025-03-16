package com.example.reader.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.reader.R;
import com.example.reader.services.EyesTrackingService;
import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;

public class PdfViewerActivity extends AppCompatActivity {
    private PDFView pdfView;

    private final BroadcastReceiver pupilMovementReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float offsetY = intent.getFloatExtra("offsetY", 0);
            Log.d("DELTA", String.format(String.valueOf(offsetY)));

            // Scroll the PDF based on detected pupil movement
            scrollPdf(offsetY);
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
    }

    private void scrollPdf(float deltaY) {
        float cellarSensitivity = 100;
        float cellarSensitivityNormalizer = cellarSensitivity;
        float bottomSensitivity = -cellarSensitivity;
        float bottomSensitivityNormalizer = 105;
        float moveDelta = 0;
        if (deltaY <= -bottomSensitivityNormalizer) {
            moveDelta = (deltaY + bottomSensitivityNormalizer) + bottomSensitivity;
        } else if (deltaY >= -cellarSensitivityNormalizer) {
            moveDelta = (deltaY + cellarSensitivityNormalizer) + cellarSensitivity;
        } else {
            moveDelta = 0;
        }

        pdfView.moveRelativeTo(0, moveDelta); // Adjust sensitivity factor
    }
}

package com.example.reader.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.reader.R;
import com.example.reader.services.EyesTrackingService;
import com.github.barteksc.pdfviewer.PDFView;

import org.opencv.android.OpenCVLoader;

import java.io.File;

public class PdfViewerActivity extends AppCompatActivity {
    private PDFView pdfView;

    private final BroadcastReceiver pupilMovementReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float offsetX = intent.getFloatExtra("offsetX", 0);
            float offsetY = intent.getFloatExtra("offsetY", 0);
            Log.i("COOORIDNATEEEEEEEEES", String.format(String.valueOf(offsetY), offsetX));

            // Scroll the PDF based on detected pupil movement
            scrollPdf(offsetY);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        registerReceiver(pupilMovementReceiver, new IntentFilter("PUPIL_MOVEMENT"), Context.RECEIVER_NOT_EXPORTED);

        PDFView pdfView = findViewById(R.id.pdfView);
        Intent intent = getIntent();
        String filePath = intent.getStringExtra("filePath");
        File file = new File(filePath);

        try {
            pdfView.fromFile(file).enableSwipe(true).enableDoubletap(true).load();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
        }

        Intent serviceIntent = new Intent(this, EyesTrackingService.class);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(pupilMovementReceiver, new IntentFilter("PUPIL_MOVEMENT"), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pupilMovementReceiver);
    }

    private void scrollPdf(float deltaY) {
        pdfView.moveRelativeTo(0, -deltaY * 100); // Adjust sensitivity factor
    }
}

package com.example.reader;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.barteksc.pdfviewer.PDFView;

public class PdfViewerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        PDFView pdfView = findViewById(R.id.pdfView);
        String pdfFile = getIntent().getStringExtra("PDF_FILE");

        try {
            pdfView.fromAsset(pdfFile).load();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
        }
    }
}

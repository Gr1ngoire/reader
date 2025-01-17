package com.example.reader;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.github.barteksc.pdfviewer.PDFView;
import java.io.File;

public class PdfViewerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        PDFView pdfView = findViewById(R.id.pdfView);
        Intent intent = getIntent();
        String filePath = intent.getStringExtra("filePath");
        File file = new File(filePath);

        try {
            pdfView.fromFile(file).load();
        } catch (Exception e) {
            Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show();
        }
    }
}

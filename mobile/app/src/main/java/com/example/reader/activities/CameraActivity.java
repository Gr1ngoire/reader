package com.example.reader.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.reader.R;
import com.example.reader.services.EyesTrackingService;

import org.opencv.android.OpenCVLoader;

public class CameraActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private final int CAMERA_INDEX = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
    }
}
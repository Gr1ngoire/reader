package com.example.reader.services;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BooksService {
    private final Context context;
    private static final String TAG = "MainActivity";
    private static final String BACKEND_URL = "http://10.0.2.2:3000/get-book-content";

    public BooksService(Context context) {
        this.context = context;
    }
    public String getBookContent(String bookFileName) throws IOException {
        try {
            // Build the request URL
            URL url = new URL(BACKEND_URL  + "?fileName=" + bookFileName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Error fetching file: " + connection.getResponseCode());
                throw new Error("Response code is" + responseCode);
            }

            File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null) {
                throw new Error("Unable to access downloads directory.");
            }

            File pdfFile = new File(downloadsDir, bookFileName);
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(pdfFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

//            connection.disconnect();
            Log.i(TAG, "Loaded book");
            return pdfFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching PDF", e);
            throw e;
        }
    }
}

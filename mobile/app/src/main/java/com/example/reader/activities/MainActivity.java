package com.example.reader.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.reader.adapters.BookAdapter;
import com.example.reader.R;
import com.example.reader.entities.Book;
import com.example.reader.services.BooksService;
import com.google.android.material.button.MaterialButton;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private boolean areMyBooksSelected = false;
    private BooksService booksService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            return;
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully");
        }

        // Request Camera Permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            Log.d("CameraActivity", "Camera permission granted");
        } else {
            Log.d("CameraActivity", "Camera permission already granted");
        }

        this.booksService = new BooksService(this);

        setContentView(R.layout.activity_main);
        this.initMyBooksFiltersButton();
        this.displayAllBooks();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (areMyBooksSelected) {
            this.displayMyBooks();
        } else {
            this.displayAllBooks();
        }

    }

    private void initAllBooksLayout(List<Book> books) {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);

        while (recyclerView.getItemDecorationCount() > 0) {
            recyclerView.removeItemDecorationAt(0);
        }

        // Get screen dimensions
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;

        // Define the desired rectangle aspect ratio and spacing
        float rectangleAspectRatio = 1.5f; // Height = 60% of width
        int spacing = (int) (screenWidth * 0.04); // 4% of the screen width as spacing

        // Calculate the number of rectangles per row
        int minRectangleWidth = 400;
        int maxRectangleWidth = 500;
        int rectanglesPerRow = Math.max(1, screenWidth / (minRectangleWidth + spacing));
        int totalSpacing = (rectanglesPerRow - 1) * spacing;
        int calculatedRectangleWidth = (screenWidth - totalSpacing) / rectanglesPerRow;
        int rectangleWidth = Math.min(maxRectangleWidth, calculatedRectangleWidth);

        int rectangleHeight = (int) (rectangleWidth * rectangleAspectRatio);

        // Create a GridLayoutManager
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, rectanglesPerRow);
        recyclerView.setLayoutManager(gridLayoutManager);

        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect,@NonNull View view,@NonNull RecyclerView parent,@NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);

                outRect.left = spacing / 2;
                outRect.right = spacing / 2;

                // Add spacing to the top except for the first row
                if (position >= rectanglesPerRow) {
                    outRect.top = spacing;
                }
            }
        });

        BookAdapter adapter = new BookAdapter(books);
        adapter.setRectangleDimensions(rectangleWidth, rectangleHeight);

        recyclerView.setAdapter(adapter);
    }

    private void initMyBooksFiltersButton() {
        MaterialButton allBooksButton = findViewById(R.id.all_books_button);
        MaterialButton myBooksButton = findViewById(R.id.my_books_button);
        this.updateBooksFilterButtonsColours(allBooksButton, myBooksButton);

        allBooksButton.setOnClickListener(event -> {
            this.areMyBooksSelected = false;
            this.updateBooksFilterButtonsColours(allBooksButton, myBooksButton);
            this.displayAllBooks();
        });
        myBooksButton.setOnClickListener(event -> {
            this.areMyBooksSelected = true;
            this.updateBooksFilterButtonsColours(allBooksButton, myBooksButton);
            this.displayMyBooks();
        });
    }

    private void displayAllBooks() {
        new Thread(() -> {
            List<Book> allBooks = this.booksService.getAllBooks();
            List<Book> downloadedBooks = this.booksService.getDownloadedBooks();
            List<Book> booksToDisplay = allBooks.stream().filter(book -> !downloadedBooks.contains(book)).collect(Collectors.toList());
            runOnUiThread(() -> this.initAllBooksLayout(booksToDisplay));
        }).start();
    }

    private void displayMyBooks() {
        new Thread(() -> {
            List<Book> books = this.booksService.getDownloadedBooks();
            runOnUiThread(() -> this.initAllBooksLayout(books));
        }).start();
    }

    private void clearPrivateDownloads() {
        File downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir != null && downloadsDir.isDirectory()) {
            File[] files = downloadsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        boolean deleted = file.delete();
                        Log.d("ClearDownloads", "Deleted " + file.getName() + ": " + deleted);
                    }
                }
            }
        }
    }

    private void updateBooksFilterButtonsColours(MaterialButton allBooksButton, MaterialButton myBooksButton) {
        int allBooksButtonColour = getColor(this.areMyBooksSelected ? R.color.inactive : R.color.active);
        int myBooksButtonColour = getColor(this.areMyBooksSelected ? R.color.active : R.color.inactive);
        allBooksButton.setBackgroundTintList(ColorStateList.valueOf(allBooksButtonColour));
        allBooksButton.setTextColor(ColorStateList.valueOf(myBooksButtonColour));
        myBooksButton.setBackgroundTintList(ColorStateList.valueOf(myBooksButtonColour));
        myBooksButton.setTextColor(ColorStateList.valueOf(allBooksButtonColour));
    }

}
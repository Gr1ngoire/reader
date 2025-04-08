package com.example.reader;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
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

import com.example.reader.entities.Book;
import com.google.android.material.button.MaterialButton;

import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_CODE = 100;
    private boolean isMyShelfSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            return;
        } else {
            Log.d("OpenCV", "OpenCV initialized successfully");
        }

        // Request Camera Permission if not granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            Log.d("CameraActivity", "Camera permission granted");
        } else {
            Log.d("CameraActivity", "Camera permission already granted");
            // Start the pupil detection service in the background
        }

        setContentView(R.layout.activity_main);

        this.initMyMyBooksFiltersButton();

        List<Book> mockedBooks = new ArrayList<>();
        mockedBooks.add(new Book("Atlas shrugged", "Ayn Rand", "atlas_shrugged.pdf"));
        mockedBooks.add(new Book("The call of Cthulhu", "Howard Lovecraft", "the_call_of_cthulhu.pdf"));
        mockedBooks.add(new Book("Meditations", "Marcus Aurelius", "meditations.pdf"));
        mockedBooks.add(new Book("Harry Potter and the Philosopher's Stone", "J.K. Rowling", "harry_potter_and_the_philosophers_stone.pdf"));
        this.initAllBooksLayout(mockedBooks);
    }

    private void initAllBooksLayout(List<Book> books) {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);

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

    private void initMyMyBooksFiltersButton() {
        MaterialButton allBooksButton = findViewById(R.id.all_books_button);
        MaterialButton myBooksButton = findViewById(R.id.my_books_button);
        this.updateBooksFilterButtonsColours(allBooksButton, myBooksButton);

        allBooksButton.setOnClickListener(event -> {
            this.isMyShelfSelected = false;
            this.updateBooksFilterButtonsColours(allBooksButton, myBooksButton);
        });
        myBooksButton.setOnClickListener(event -> {
            this.isMyShelfSelected = true;
            this.updateBooksFilterButtonsColours(allBooksButton, myBooksButton);
        });
    }

    private void updateBooksFilterButtonsColours(MaterialButton allBooksButton, MaterialButton myBooksButton) {
        int allBooksButtonColour = getColor(this.isMyShelfSelected ? R.color.inactive : R.color.active);
        int myBooksButtonColour = getColor(this.isMyShelfSelected ? R.color.active : R.color.inactive);
        allBooksButton.setBackgroundTintList(ColorStateList.valueOf(allBooksButtonColour));
        allBooksButton.setTextColor(ColorStateList.valueOf(myBooksButtonColour));
        myBooksButton.setBackgroundTintList(ColorStateList.valueOf(myBooksButtonColour));
        myBooksButton.setTextColor(ColorStateList.valueOf(allBooksButtonColour));
    }

}
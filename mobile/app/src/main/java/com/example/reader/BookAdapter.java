package com.example.reader;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.reader.activities.PdfViewerActivity;
import com.example.reader.entities.Book;
import com.example.reader.services.BooksService;

import java.io.IOException;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
    private final List<Book> books;
    private int rectangleWidth;
    private int rectangleHeight;
    private BooksService booksService;

    public BookAdapter(List<Book> books) {
        this.books = books;
    }

    // Method to set dynamic dimensions
    public void setRectangleDimensions(int width, int height) {
        this.rectangleWidth = width;
        this.rectangleHeight = height;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.book_cover_layout, parent, false);

        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) view.getLayoutParams();
        params.width = rectangleWidth;
        params.height = rectangleHeight;
        view.setLayoutParams(params);

        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        booksService = new BooksService(context);
        Book book = books.get(position);
        holder.name.setText(book.getName());
        holder.author.setText(book.getAuthor());

        holder.itemView.setOnClickListener(event -> {
            new Thread(() -> {
                try {
                    String downloadedBookFilePath = booksService.getBookContent(book.getBookFileName());
                    Intent book_content_intent = new Intent(context, PdfViewerActivity.class);
                    book_content_intent.putExtra("filePath", downloadedBookFilePath);
                    context.startActivity(book_content_intent);
                    Log.i("Successfully loaded book", downloadedBookFilePath);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(context, "No PDF viewer found!", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        });
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    public static class BookViewHolder extends RecyclerView.ViewHolder {
        TextView name, author;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.book_name);
            author = itemView.findViewById(R.id.book_author);
        }
    }
}

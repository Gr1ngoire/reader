package com.example.reader;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.reader.entities.Book;

import java.util.List;
import java.util.Objects;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
    private final List<Book> books;
    private int rectangleWidth;
    private int rectangleHeight;

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
        Book book = books.get(position);
        holder.name.setText(book.getName());
        holder.author.setText(book.getAuthor());

        holder.itemView.setOnClickListener(event -> {
            Intent intent = new Intent(context, PdfViewerActivity.class);
            intent.putExtra("PDF_FILE", book.getBookFileName());

            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.println(Log.ERROR, "INFO", Objects.requireNonNull(e.getMessage()));
                Toast.makeText(context, "No PDF viewer found!", Toast.LENGTH_SHORT).show();
            }
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

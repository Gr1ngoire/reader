package com.example.reader.adapters;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.reader.R;
import com.example.reader.activities.ReadingActivity;
import com.example.reader.entities.Book;
import com.example.reader.services.BooksService;
import com.example.reader.services.ReadProgressService;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {
    private final List<Book> books;
    private int rectangleWidth;
    private int rectangleHeight;

    public BookAdapter(List<Book> books) {
        this.books = books;
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
        BooksService booksService = new BooksService(context);
        ReadProgressService readProgressService = new ReadProgressService(context);
        Book book = books.get(position);
        holder.name.setText(book.getName());
        holder.author.setText(book.getAuthor());

        List<Book> downloadedBooks = booksService.getDownloadedBooks();
        boolean isBookDownloaded = downloadedBooks.stream().anyMatch(bookItem -> book.getFileName().equals(bookItem.getFileName()));
        if (isBookDownloaded) {
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> {
                booksService.deleteBookFromLocalStorage(book.getFileName());
                books.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, books.size());
                holder.deleteButton.setVisibility(View.GONE);
            });
        } else {
            holder.deleteButton.setVisibility(View.GONE);
        }

        File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File bookFile = new File(downloadsDir, book.getFileName());
        int bookAllPagesCount = readProgressService.getAllPagesCount(bookFile.getPath());
        int lastReadPageIndex = readProgressService.getLastReadPage(bookFile.getPath());
        int readProgress = lastReadPageIndex == 0 || bookAllPagesCount == 0 ? 0 : (int) ((lastReadPageIndex / (float) bookAllPagesCount) * 100);
        TextView readProgressLabel = holder.itemView.findViewById(R.id.read_progress);
        if (isBookDownloaded) {
            readProgressLabel.setText(String.format("%d%% read", readProgress));
        }

        holder.itemView.setOnClickListener(event -> new Thread(() -> {
            try {
                String downloadedBookFilePath = booksService.getBookContent(book.getFileName());
                Intent book_content_intent = new Intent(context, ReadingActivity.class);
                book_content_intent.putExtra("filePath", downloadedBookFilePath);
                context.startActivity(book_content_intent);

                Log.i("Successfully loaded book", downloadedBookFilePath);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(context, "No PDF viewer found!", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start());
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    // Method to set dynamic dimensions
    public void setRectangleDimensions(int width, int height) {
        this.rectangleWidth = width;
        this.rectangleHeight = height;
        notifyDataSetChanged();
    }

    public static class BookViewHolder extends RecyclerView.ViewHolder {
        TextView name, author;
        ImageButton deleteButton;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.book_name);
            author = itemView.findViewById(R.id.book_author);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}

package com.example.reader.entities;

public class Book {
    private final String name;
    private final String author;
    private final String bookFileName;
    private final int readingProgress;
    public Book(String name, String author, String bookFileName, int readingProgress) {
        this.name = name;
        this.author = author;
        this.bookFileName = bookFileName;
        this.readingProgress = readingProgress;
    }
    public String getAuthor() {
        return author;
    }
    public String getName() {
        return name;
    }
    public String getFileName() {
        return bookFileName;
    }
    public int getReadingProgress() {
        return this.readingProgress;
    }
}

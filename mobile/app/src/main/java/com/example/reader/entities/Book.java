package com.example.reader.entities;

import java.util.Objects;

public class Book {
    private final String name;
    private final String author;
    private final String bookFileName;
    public Book(String name, String author, String bookFileName) {
        this.name = name;
        this.author = author;
        this.bookFileName = bookFileName;
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

    @Override
    public int hashCode() {
        return bookFileName != null ? bookFileName.hashCode() : 0;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        Book book = (Book) object;

        return Objects.equals(bookFileName, book.bookFileName);
    }
}

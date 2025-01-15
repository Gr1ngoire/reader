package com.example.reader.entities;

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
    public String getBookFileName () {
        return bookFileName;
    }
}

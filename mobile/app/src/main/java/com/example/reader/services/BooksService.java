package com.example.reader.services;

import com.example.reader.entities.Book;

import java.io.File;
import java.util.List;

public abstract interface BooksService {
    public abstract File getBookContent(String bookName);
}

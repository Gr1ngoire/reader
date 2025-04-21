package com.example.reader.services;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;

public class ReadProgressService {
    private final String LAST_PAGE_PREFERENCE_NAME = "LAST_PAGE";
    private final String ALL_PAGES_COUNT_PREFERENCE_NAME = "ALL_PAGES_COUNT";
    private final Context context;
    public ReadProgressService(Context context) {
        this.context = context;
    }

    public void saveLastReadPage(String filePath, int page) {
        this.context.getSharedPreferences(this.LAST_PAGE_PREFERENCE_NAME, MODE_PRIVATE)
                .edit()
                .putInt(this.getBookFileKeyByFilePath(filePath), page)
                .apply();
    }

    public void saveAllPagesCount(String filePath, int pages) {
        this.context.getSharedPreferences(this.ALL_PAGES_COUNT_PREFERENCE_NAME, MODE_PRIVATE)
                .edit()
                .putInt(this.getBookFileKeyByFilePath(filePath), pages)
                .apply();
    }

    public int getLastReadPage(String filePath) {
        return this.context.getSharedPreferences(this.LAST_PAGE_PREFERENCE_NAME, MODE_PRIVATE)
                .getInt(this.getBookFileKeyByFilePath(filePath), 0);
    }

    public int getAllPagesCount(String filePath) {
        return this.context.getSharedPreferences(this.ALL_PAGES_COUNT_PREFERENCE_NAME, MODE_PRIVATE)
                .getInt(this.getBookFileKeyByFilePath(filePath), 0);
    }

    public void removeReadProgressInfo(String filePath) {
        this.context.getSharedPreferences(this.LAST_PAGE_PREFERENCE_NAME, MODE_PRIVATE)
                .edit()
                .remove(this.getBookFileKeyByFilePath(filePath))
                .apply();
    }

    public void removeAllPagesCount(String filePath) {
        this.context.getSharedPreferences(this.ALL_PAGES_COUNT_PREFERENCE_NAME, MODE_PRIVATE)
                .edit()
                .remove(this.getBookFileKeyByFilePath(filePath))
                .apply();
    }

    private String getBookFileKeyByFilePath(String filePath) {
        return "pdf_" + filePath.hashCode();
    }
}

package com.example.reader.services;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;

public class ReadProgressService {
    private final String READ_PROGRESS_PREFERENCE_NAME = "READ_PROGRESS";
    private final Context context;
    public ReadProgressService(Context context) {
        this.context = context;
    }

    public void saveLastReadPage(String filePath, int page) {
        this.context.getSharedPreferences(this.READ_PROGRESS_PREFERENCE_NAME, MODE_PRIVATE)
                .edit()
                .putInt(this.getBookFileKeyByFilePath(filePath), page)
                .apply();
    }

    public int getLastReadPage(String filePath) {
        return this.context.getSharedPreferences(this.READ_PROGRESS_PREFERENCE_NAME, MODE_PRIVATE)
                .getInt(this.getBookFileKeyByFilePath(filePath), 0);
    }

    public void removeReadProgressInfo(String filePath) {
        this.context.getSharedPreferences(this.READ_PROGRESS_PREFERENCE_NAME, MODE_PRIVATE)
                .edit()
                .remove(this.getBookFileKeyByFilePath(filePath))
                .apply();
    }

    private String getBookFileKeyByFilePath(String filePath) {
        return "pdf_" + filePath.hashCode();
    }
}

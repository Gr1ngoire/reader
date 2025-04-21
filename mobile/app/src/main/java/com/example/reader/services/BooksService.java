package com.example.reader.services;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.example.reader.BuildConfig;
import com.example.reader.entities.Book;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BooksService {
    private static final String TAG = "MainActivity";
    private final String PDF_EXTENSION_PORTION = ".pdf";
    private final Context context;
    private final AmazonS3 s3Client;

    public BooksService(Context context) {
        this.context = context;

        // Load AWS credentials from local.properties
        String accessKey = BuildConfig.AWS_ACCESS_KEY;
        String secretKey = BuildConfig.AWS_SECRET_KEY;
        String region = BuildConfig.AWS_REGION;

//        if (accessKey.equals("") || secretKey.equals("") || region.equals("") || bucketName.equals("")) {
//            throw new IOException("Missing AWS credentials in .env file.");
//        }

        // Initialize S3 client
        AWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
        this.s3Client = new AmazonS3Client(awsCreds, Region.getRegion(region));
    }

    public String getBookContent(String bookFileName) throws IOException {
        try {

            File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null) {
                throw new IOException("Unable to access downloads directory.");
            }

            File pdfFile = new File(downloadsDir, bookFileName);
            String bucketName = BuildConfig.AWS_BOOKS_BUCKET_NAME;
            S3Object s3Object = this.s3Client.getObject(new GetObjectRequest(bucketName, bookFileName));
            InputStream inputStreamS3 = s3Object.getObjectContent();

            try (FileOutputStream outputStream = new FileOutputStream(pdfFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStreamS3.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            Log.i(TAG, "Downloaded book from S3: " + pdfFile.getAbsolutePath());
            return pdfFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching book from S3", e);
            throw e;
        }
    }

    public List<Book> getAllBooks() {
        String bucketName = BuildConfig.AWS_BOOKS_BUCKET_NAME;
        ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(bucketName);

        ListObjectsV2Result result = this.s3Client.listObjectsV2(request);
        List<S3ObjectSummary> objects = result.getObjectSummaries();
        List<Book> books = new ArrayList<>();

        for (S3ObjectSummary summary : objects) {
            String objectName = summary.getKey();
            if (objectName.endsWith(this.PDF_EXTENSION_PORTION)) {
                books.add(this.getBookFromFileName(objectName));
            }
        }

        return books;
    }

    /** Books file names are represented in format <name>__<author>.
     * If book name or author include 2+ words they are separated with _
     **/
    public Book getBookFromFileName(String rawFileName) {
        String NAME_AUTHOR_SEPARATOR = "__";
        char RAW_NAME_FRAGMENT_SEPARATOR = '_';
        char NEW_NAME_FRAGMENT_SEPARATOR = ' ';
        int nameIndex = 0;
        int authorIndex = 1;

        String fileNameWithoutExtension = rawFileName.replace(this.PDF_EXTENSION_PORTION, "");
        String[] rawNameAndAuthor = fileNameWithoutExtension.split(NAME_AUTHOR_SEPARATOR);
        String rawName = rawNameAndAuthor[nameIndex];
        String rawAuthor = rawNameAndAuthor[authorIndex];

        String name = rawName.replace(RAW_NAME_FRAGMENT_SEPARATOR, NEW_NAME_FRAGMENT_SEPARATOR);
        String author = rawAuthor.replace(RAW_NAME_FRAGMENT_SEPARATOR, NEW_NAME_FRAGMENT_SEPARATOR);

        return new Book(name, author, rawFileName);
    }

    public List<Book> getDownloadedBooks() {
        File downloadsDir = this.context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null) {
            throw new IllegalArgumentException("Downloads dir is absent");
        }

        File[] downloadedBooksFiles = downloadsDir.listFiles();
        if (downloadedBooksFiles == null) {
            throw new IllegalArgumentException("Downloaded books are absent");
        }

        for (File file: downloadedBooksFiles) {
            Log.d("DOWNLOADED BOOK", file.getName());
        }

        return Arrays.stream(downloadedBooksFiles).map(file -> this.getBookFromFileName(file.getName())).collect(Collectors.toList());
    }

    public void deleteBookFromLocalStorage(String bookFileName) {
        File downloadsDir = this.context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null) {
            Log.e("DeleteBook", "Downloads directory is not accessible.");
            return;
        }

        File bookFile = new File(downloadsDir, bookFileName);
        if (bookFile.exists()) {
            boolean deleted = bookFile.delete();
            Log.d("DeleteBook", "Deleted " + bookFileName + ": " + deleted);
        } else {
            Log.w("DeleteBook", "Book file not found: " + bookFileName);
        }
    }
}
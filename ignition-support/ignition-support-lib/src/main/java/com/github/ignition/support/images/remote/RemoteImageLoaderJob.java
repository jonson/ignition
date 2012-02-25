package com.github.ignition.support.images.remote;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.util.ByteArrayBuffer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.github.ignition.support.cache.ImageCache;
import com.google.common.primitives.Bytes;

public class RemoteImageLoaderJob implements Runnable {

    private static final String LOG_TAG = "Ignition/ImageLoader";

    private static final int DEFAULT_RETRY_HANDLER_SLEEP_TIME = 1000;

    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    private static final int DEFATUL_SOCKET_TIMEOUT = 5000;
    
    private String imageUrl;
    private RemoteImageLoaderHandler handler;
    private ImageCache imageCache;
    private int numRetries, defaultBufferSize;

    public RemoteImageLoaderJob(String imageUrl, RemoteImageLoaderHandler handler, ImageCache imageCache,
            int numRetries, int defaultBufferSize) {
        this.imageUrl = imageUrl;
        this.handler = handler;
        this.imageCache = imageCache;
        this.numRetries = numRetries;
        this.defaultBufferSize = defaultBufferSize;
    }

    /**
     * The job method run on a worker thread. It will first query the image cache, and on a miss,
     * download the image from the Web.
     */
    @Override
    public void run() {
        Bitmap bitmap = null;

        if (imageCache != null) {
            // at this point we know the image is not in memory, but it could be cached to SD card
            bitmap = imageCache.getBitmap(imageUrl);
        }

        if (bitmap == null) {
            bitmap = downloadImage();
        }

        notifyImageLoaded(imageUrl, bitmap);
    }

    // TODO: we could probably improve performance by re-using connections instead of closing them
    // after each and every download
    protected Bitmap downloadImage() {
        int timesTried = 1;

        while (timesTried <= numRetries) {
            try {
                byte[] imageData = retrieveImageData();

                if (imageData == null) {
                    break;
                }

                // first try to decode the image before before caching it
                Bitmap bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length); 

                // at this point, it was decoded properly, cache it if possible
                if (imageCache != null) {
                    imageCache.put(imageUrl, imageData);
                }

                return bmp;

            } catch (Throwable e) {
                Log.w(LOG_TAG, "download for " + imageUrl + " failed (attempt " + timesTried + ")");
                e.printStackTrace();
                SystemClock.sleep(DEFAULT_RETRY_HANDLER_SLEEP_TIME);
                timesTried++;
            }
        }

        return null;
    }

    protected byte[] retrieveImageData() throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT);
        connection.setReadTimeout(DEFATUL_SOCKET_TIMEOUT);
        
        // determine the image size and allocate a buffer
        int fileSize = connection.getContentLength();
        if (fileSize <= 0) {
            fileSize = defaultBufferSize;
            Log.w(LOG_TAG,
                    "Server did not set a Content-Length header, will default to buffer size of "
                            + defaultBufferSize + " bytes");
        }

        byte[] imageData = new byte[fileSize];
        ByteArrayBuffer buffer = new ByteArrayBuffer(fileSize);

        // download the file
        Log.d(LOG_TAG, "fetching image " + imageUrl + " (" + fileSize + ")");
        BufferedInputStream istream = new BufferedInputStream(connection.getInputStream());
        int bytesRead = 0;
        int offset = 0;
        while (bytesRead != -1) {
            bytesRead = istream.read(imageData, 0, fileSize);
            if (bytesRead > 0) {
	            buffer.append(imageData, 0, bytesRead);
	            offset += bytesRead;
            }
        }

        // clean up
        istream.close();
        connection.disconnect();
        
        imageData = buffer.toByteArray();
        return imageData;
    }

    protected void notifyImageLoaded(String url, Bitmap bitmap) {
        Message message = new Message();
        message.what = RemoteImageLoaderHandler.HANDLER_MESSAGE_ID;
        Bundle data = new Bundle();
        data.putString(RemoteImageLoaderHandler.IMAGE_URL_EXTRA, url);
        Bitmap image = bitmap;
        data.putParcelable(RemoteImageLoaderHandler.BITMAP_EXTRA, image);
        message.setData(data);

        handler.sendMessage(message);
    }
}

package com.github.ignition.support.images.remote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

public final class BitmapHelper {

	private static final String LOG_TAG = "BitmapHelper";
	
	private BitmapHelper() {}
	
	public static Bitmap decodeAndResize(byte[] imageData, int maxWidth, int maxHeight) throws OutOfMemoryError {
		
		BitmapFactory.Options o = null;
		int scale = 1;
		
		if (maxWidth > 0 & maxHeight > 0) {
			o = new BitmapFactory.Options();
			// get the original size
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(imageData, 0, imageData.length, o);

			// Find the correct scale value. It should be the power of
			// 2.
			int widthTmp = o.outWidth, heightTmp = o.outHeight;
			while (true) {
				if ((widthTmp / 2) < maxWidth || (heightTmp / 2) < maxHeight) {
					break;
				}
				widthTmp /= 2;
				heightTmp /= 2;
				scale *= 2;
			}

			Log.d(LOG_TAG, "Scale factor=" + scale + " width=" + widthTmp
					+ " height=" + heightTmp);
		}
		else
		{
			return null;
		}
		
		o = new BitmapFactory.Options();
		o.inSampleSize = scale;

		Bitmap bmp = decodeBitmapWithRetry(imageData, o);

		if (bmp == null) {
			Log
					.w(LOG_TAG,
							"Null decoded image.. might be because of scale factor??  trying again without downsizing");
			// get the raw version
			bmp = decodeBitmapWithRetry(imageData, null);
		}

		if (bmp == null) {
			// should this throw an exception?
			return null;
		} else if (maxWidth > 0 & maxHeight > 0) {
			// scale it to the exact size (it's likely a bit bigger)
			Log.d(LOG_TAG, "Scaling bitmap");
			Bitmap img = scaleBitmap(bmp, maxWidth, maxHeight);
			bmp.recycle();
			bmp = null;
			return img;
		} else {
			// return unscaled bitmap
			Log.d(LOG_TAG, "Returning unscaled bitmap");
			return bmp;
		}
	}
	
	private static Bitmap decodeBitmapWithRetry(byte[] imageData, Options opts) {
    	try {
    		return BitmapFactory.decodeByteArray(imageData, 0, imageData.length, opts);
    	} catch (OutOfMemoryError e) {
    		// very unlikely this will help, but try a 2nd time after attempting to kickstart the gc
    		Log.w(LOG_TAG, "Out of memory error while decoding bitmap, trying to trigger a GC");
    		System.gc();
    		System.gc();
    		
    		try {
    			return BitmapFactory.decodeByteArray(imageData, 0, imageData.length, opts);
    		} catch (OutOfMemoryError e2) {
    			// we tried a few times, nothing worked, caller needs to handle OOM issues in this case
    			Log.w(LOG_TAG, "Tried 2x, still out of memory, can the caller free any memory?");
    			throw e2;
    		}
    	}
    }
	
	public static Bitmap scaleBitmap(Bitmap bmp, int maxWidth, int maxHeight) {
    	int width = bmp.getWidth();
        int height = bmp.getHeight();

        float ratio = (float)width/(float)height;

        float scaleWidth = maxWidth;
        float scaleHeight = maxHeight;

        if((float)maxWidth/(float)maxHeight > ratio) {
            scaleWidth = (float)maxHeight * ratio;
        } else {
            scaleHeight = (float)maxWidth / ratio;
        }
        
        Log.d(LOG_TAG, String.format("original=%dx%d  resized=%fx%f", width,height, scaleWidth, scaleHeight));
        
        Bitmap image = createScaledBitmapWithRetry(bmp, (int)scaleWidth, (int)scaleHeight);
        return image;
    }
	
	private static Bitmap createScaledBitmapWithRetry(Bitmap bmp, int scaleWidth, int scaleHeight) {
    	
    	try {
    		return Bitmap.createScaledBitmap(bmp, (int)scaleWidth, (int)scaleHeight, true);
    	} catch (OutOfMemoryError e) {
    		Log.w(LOG_TAG, "Out of memory error while scaling bitmap, trying to clear cache and trigger a GC");
    		System.gc();
    		System.gc();
    		
    		try {
    			return Bitmap.createScaledBitmap(bmp, (int)scaleWidth, (int)scaleHeight, true);
    		} catch (OutOfMemoryError e2) {
    			Log.e(LOG_TAG, "Tried to scale bitmap 2x, still out of memory, returning null");
    		}
    	}
    	
    	return null;
    }
}

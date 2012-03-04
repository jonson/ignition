package com.github.ignition.support.http.multipart;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.util.Log;

/**
 * Hack to put a timeout on HTTP writes, they *should* always timeout in the http layer, but 
 * on Android 2.2, 2.3, we're seeing some cases where we are getting infinite timeouts in 
 * the native SSL sockets when writing bytes.  These zombie threads will hang around until
 * the write() call eventually returns, but at least our app will not be totally hung. 
 * 
 * @author jonson
 *
 */
public class StrictWriteTimeoutFilePart extends FilePart {
	
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final int writeTimeoutMillis;
			
	public StrictWriteTimeoutFilePart(String name, File file, String filename,
			String contentType, int writeTimeoutMillis) {
		super(name, file, filename, contentType);
		this.writeTimeoutMillis = writeTimeoutMillis;
	}
	
	@Override
	protected void doWrite(final OutputStream out, final byte[] data, final int length)
			throws IOException {
		
		Future<Boolean> future = executor.submit(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				Log.d("FileUpload", "attempting to write data");
				out.write(data, 0, length);
				Log.d("FileUpload", "wrote data");
				return true;
			}
		});
		
		Boolean result;
		try {
			Log.d("FileUpload", "waiting " + writeTimeoutMillis + " seconds");
			result = future.get(writeTimeoutMillis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new IOException("InterruptedException while uploading file" , e);
		} catch (ExecutionException e) {
			throw new IOException("ExecutionException while uploading file" , e);
		} catch (TimeoutException e) {
			throw new IOException("TimeoutException while uploading file" , e);
		}
		
		if (result == null || !result) {
			throw new IOException("Write was not successful or did not complete within the timeout, cancelling");
		}
		
		Log.d("FileUpload", "Successfully wrote " + length + " bytes");
	}
	
	@Override
	protected void doneWriting() {
		executor.shutdownNow();
	}

}

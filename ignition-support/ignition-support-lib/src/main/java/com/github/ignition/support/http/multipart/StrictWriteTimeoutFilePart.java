package com.github.ignition.support.http.multipart;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;

import android.util.Log;

/**
 * Hack to put a timeout on HTTP writes, they *should* always timeout in the http layer, but 
 * on Android 2.2, 2.3, we're seeing some cases where we are getting infinite timeouts in 
 * the native SSL sockets when writing bytes.  These zombie threads will hang around until
 * the write() call eventually returns, but at least our app will not be totally hung. 
 * 
 * Using a smaller write buffer seems to limit these errors.
 * 
 * @author jonson
 *
 */
public class StrictWriteTimeoutFilePart extends FilePart implements SupportsTimeoutPart {
	
	private ExecutorService executor;
	private final int writeTimeoutMillis;
	private int amountWritten;
			
	public StrictWriteTimeoutFilePart(String name, File file, String filename,
			String contentType, int writeTimeoutMillis) {
		super(name, file, filename, contentType);
		this.writeTimeoutMillis = writeTimeoutMillis;
	}
	
	@Override
	protected void doWrite(final OutputStream out, final byte[] data, final int length)
			throws IOException {
		Log.d("FileUpload", "Attempting to write " + length + " bytes.  Total so far=" + amountWritten);
		amountWritten+= length;
		if (executor == null) {
			super.doWrite(out, data, length);
		} else {
			MultipartEntity.doWriteWithTimeout(executor, out, data, length, writeTimeoutMillis);
		}
	}

	@Override
	public void setExecutorService(ExecutorService executor) {
		this.executor = executor;
	}

}

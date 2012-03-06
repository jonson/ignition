package com.github.ignition.support.http.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.protocol.HTTP;

import android.util.Log;

/**
 * @author <a href="mailto:vit at cleverua.com">Vitaliy Khudenko</a>
 */
public class MultipartEntity extends AbstractHttpEntity implements Cloneable {
    
    /* package */ static final String CRLF = "\r\n";    //$NON-NLS-1$
    private static final int WRITE_TIMEOUT_MILLIS = 15000;
    private List<Part> parts = new ArrayList<Part>();
    private Boundary boundary;
    private ExecutorService executor = null;
    
    public MultipartEntity(String boundaryStr) {
        super();
        boundary = new Boundary(boundaryStr);
        setContentType("multipart/form-data; boundary=\"" + boundary.getBoundary() + '"');  //$NON-NLS-1$
    }
    
    public MultipartEntity() {
        this(null);
    }
    
    public void addPart(Part part) {
        parts.add(part);
    }
    
    public boolean isRepeatable() {
        return true;
    }

    public long getContentLength() {
        long result = 0;
        for (Part part : parts) {
            result += part.getContentLength(boundary);
        }
        result += boundary.getClosingBoundary().length;
        return result;
    }
    
    /**
     * Returns <code>null</code> since it's not designed to be used for server responses.
     */
    public InputStream getContent() throws IOException {
        return null;
    }
    
    public void writeTo(final OutputStream out) throws IOException {
    	try {
	        if (out == null) {
	            throw new IllegalArgumentException("Output stream may not be null");    //$NON-NLS-1$
	        }
	        for (Part part : parts) {
	        	
	        	// hack to get a shared thread
	        	if (executor != null && part instanceof SupportsTimeoutPart) {
	        		((SupportsTimeoutPart) part).setExecutorService(executor);
	        	}
	        	
	            part.writeTo(out, boundary);
	        }
	        
	        // this may timeout as well!
	        Log.d("FileUpload", "Writing closing file boundry");
	        byte[] closing = boundary.getClosingBoundary();
	        doWrite(out, closing, closing.length);
	        
	        Log.d("FileUpload", "Flushing output stream");
	        out.flush();
	        
	        Log.d("FileUpload", "writeTo() successful");
    	} finally {
    		executor.shutdown();
    	}
    }
    
    protected void doWrite(final OutputStream out, final byte[] data, final int length) throws IOException {
    	if (executor == null) {
    		out.write(data, 0, length);
    	} else {
    		doWriteWithTimeout(executor, out, data, length, WRITE_TIMEOUT_MILLIS);
    	}
    }
    
    /**
     * Helper method to execute a write with a timeout. 
     * 
     * @param executor
     * @param out
     * @param data
     * @param length
     * @param writeTimeoutMillis
     * @throws IOException
     */
    static void doWriteWithTimeout(final ExecutorService executor, final OutputStream out, final byte[] data, final int length, final int writeTimeoutMillis) throws IOException {
    	Future<Boolean> future = executor.submit(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				Log.d("FileUpload", "[thread] attempting to write data, len=" + length);
				out.write(data, 0, length);
				out.flush();
				Log.d("FileUpload", "[thread] wrote data, len=" + length);
				return true;
			}
		});
		
		Boolean result;
		try {
			long before = System.currentTimeMillis();
			Log.d("FileUpload", "waiting max " + writeTimeoutMillis + "ms for write to complete, len=" + length);
			result = future.get(writeTimeoutMillis, TimeUnit.MILLISECONDS);
			long duration = System.currentTimeMillis() - before;
			Log.d("FileUpload", "write successful, wrote " + length + " bytes in " + duration + "ms");
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

    /**
     * Tells that this entity is not streaming.
     *
     * @return <code>false</code>
     */
    public boolean isStreaming() {
        return false;
    }

    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("MultipartEntity does not support cloning"); //$NON-NLS-1$ // TODO
    }
    
    public static String encode(final String content, final String encoding) {
        try {
            return URLEncoder.encode(
                content, 
                encoding != null ? encoding : HTTP.DEFAULT_CONTENT_CHARSET
            );
        } catch (UnsupportedEncodingException problem) {
            throw new IllegalArgumentException(problem);
        }
    }
    
    public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}
}
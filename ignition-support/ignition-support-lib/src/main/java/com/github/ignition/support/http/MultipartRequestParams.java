package com.github.ignition.support.http;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;

import com.github.ignition.support.http.multipart.FilePart;
import com.github.ignition.support.http.multipart.MultipartEntity;
import com.github.ignition.support.http.multipart.StrictWriteTimeoutFilePart;
import com.github.ignition.support.http.multipart.StringPart;

/**
 * Alternative version of RequestParams that returns a MultipartEntity.
 * 
 * @author jon
 *
 */
public class MultipartRequestParams extends RequestParams {

	private final int writeTimeoutMillis;
	
	public MultipartRequestParams(int writeTimeoutMillis) {
		this.writeTimeoutMillis = writeTimeoutMillis;
	}
	
	@Override
	HttpEntity getEntity() {
        HttpEntity entity = null;

        if(!fileParams.isEmpty()) {
            MultipartEntity multipartEntity = new MultipartEntity();

            // Add string params
            for(ConcurrentHashMap.Entry<String, String> entry : urlParams.entrySet()) {
                multipartEntity.addPart(new StringPart(entry.getKey(), entry.getValue()));
            }

            for(ConcurrentHashMap.Entry<String, FileWrapper> entry : fileParams.entrySet()) {
                FileWrapper wrapper = entry.getValue();
                multipartEntity.addPart(new StrictWriteTimeoutFilePart(entry.getKey(), wrapper.file, wrapper.getFileName(), wrapper.contentType, writeTimeoutMillis));
            }

            entity = multipartEntity;
        } else {
            try {
                entity = new UrlEncodedFormEntity(getParamsList(), ENCODING);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return entity;
    }
}

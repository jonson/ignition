package com.github.ignition.support.http.multipart;

import java.util.concurrent.ExecutorService;

public interface SupportsTimeoutPart extends Part {

	void setExecutorService(ExecutorService executor);
	
}

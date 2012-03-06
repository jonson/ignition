/* Copyright (c) 2009-2011 Matthias Kaeppler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.ignition.support.http;

import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Proxy;
import android.text.TextUtils;
import android.util.Log;

import com.github.ignition.support.IgnitedDiagnostics;
import com.github.ignition.support.cache.AbstractCache;
import com.github.ignition.support.http.cache.CachedHttpRequest;
import com.github.ignition.support.http.cache.HttpResponseCache;
import com.github.ignition.support.http.ssl.EasySSLSocketFactory;

public class IgnitedHttp {

    static final String LOG_TAG = IgnitedHttp.class.getSimpleName();

    public static final int DEFAULT_MAX_CONNECTIONS = 4;
    public static final int DEFAULT_SOCKET_TIMEOUT = 30 * 1000;
    public static final int DEFAULT_WAIT_FOR_CONNECTION_TIMEOUT = 30 * 1000;
    public static final String DEFAULT_HTTP_USER_AGENT = "Android/Ignition";

    private HashMap<String, String> defaultHeaders = new HashMap<String, String>();
    private AbstractHttpClient httpClient;
    private Context appContext;

    private HttpResponseCache responseCache;

    public static class IgnitedConfig {
    	private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    	private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    	private int waitForConnectionTimeout = DEFAULT_WAIT_FOR_CONNECTION_TIMEOUT;
    	private String userAgent = DEFAULT_HTTP_USER_AGENT;
    	
    	public IgnitedConfig maxConnections(int maxConnections) {
    		this.maxConnections = maxConnections;
    		return this;
    	}
    	
    	public IgnitedConfig socketTimeout(int socketTimeoutMillis) {
    		this.socketTimeout = socketTimeoutMillis;
    		return this;
    	}
    	
    	public IgnitedConfig waitForConnectionTimeout(int waitForConnectionTimeoutMillis) {
    		this.waitForConnectionTimeout = waitForConnectionTimeoutMillis;
    		return this;
    	}
    	
    	public IgnitedConfig userAgent(String userAgent) {
    		this.userAgent = userAgent;
    		return this;
    	}
    }
    
    
    public IgnitedHttp(Context context) {
        this(context, new IgnitedConfig());
    }
    
    public IgnitedHttp(Context context, IgnitedConfig config) {
    	appContext = context.getApplicationContext();
        setupHttpClient(config);
        appContext.registerReceiver(new ConnectionChangedBroadcastReceiver(this), new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION));
    }

    protected void setupHttpClient(IgnitedConfig config) {
        BasicHttpParams httpParams = new BasicHttpParams();

        ConnManagerParams.setTimeout(httpParams, config.waitForConnectionTimeout);
        ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(
                config.maxConnections));
        ConnManagerParams.setMaxTotalConnections(httpParams, config.maxConnections);
        
        // this doesn't seem to be working w/o this in here
        HttpConnectionParams.setConnectionTimeout(httpParams, config.waitForConnectionTimeout);
        HttpConnectionParams.setSoTimeout(httpParams, config.socketTimeout);
        HttpConnectionParams.setTcpNoDelay(httpParams, false);
        HttpConnectionParams.setSocketBufferSize(httpParams, 16 * 1024);
        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUserAgent(httpParams, config.userAgent);
        

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        if (IgnitedDiagnostics.ANDROID_API_LEVEL >= 7) {
            schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        } else {
            // used to work around a bug in Android 1.6:
            // http://code.google.com/p/android/issues/detail?id=1946
            // TODO: is there a less rigorous workaround for this?
            schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
        }

        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(httpParams, schemeRegistry);
        httpClient = new DefaultHttpClient(cm, httpParams);
    }

    /**
     * Enables caching of HTTP responses. This will only enable the in-memory
     * cache. If you also want to enable the disk cache, see
     * {@link #enableResponseCache(Context, int, long, int, int)} .
     * 
     * @param initialCapacity
     *            the initial element size of the cache
     * @param expirationInMinutes
     *            time in minutes after which elements will be purged from the
     *            cache
     * @param maxConcurrentThreads
     *            how many threads you think may at once access the cache; this
     *            need not be an exact number, but it helps in fragmenting the
     *            cache properly
     * @see HttpResponseCache
     */
    public void enableResponseCache(int initialCapacity, long expirationInMinutes,
            int maxConcurrentThreads) {
        responseCache = new HttpResponseCache(initialCapacity, expirationInMinutes,
                maxConcurrentThreads);
    }

    /**
     * Enables caching of HTTP responses. This will also enable the disk cache.
     * 
     * @param context
     *            the current context
     * @param initialCapacity
     *            the initial element size of the cache
     * @param expirationInMinutes
     *            time in minutes after which elements will be purged from the
     *            cache (NOTE: this only affects the memory cache, the disk
     *            cache does currently NOT handle element TTLs!)
     * @param maxConcurrentThreads
     *            how many threads you think may at once access the cache; this
     *            need not be an exact number, but it helps in fragmenting the
     *            cache properly
     * @param diskCacheStorageDevice
     *            where files should be cached persistently (
     *            {@link AbstractCache#DISK_CACHE_INTERNAL},
     *            {@link AbstractCache#DISK_CACHE_SDCARD} )
     * @see HttpResponseCache
     */
    public void enableResponseCache(Context context, int initialCapacity, long expirationInMinutes,
            int maxConcurrentThreads, int diskCacheStorageDevice) {
        enableResponseCache(initialCapacity, expirationInMinutes, maxConcurrentThreads);
        responseCache.enableDiskCache(context, diskCacheStorageDevice);
    }

    /**
     * Disables caching of HTTP responses. You may also choose to wipe any files that may have been
     * written to disk.
     */
    public void disableResponseCache(boolean wipe) {
        if (responseCache != null && wipe) {
            responseCache.clear();
        }
        responseCache = null;
    }

    /**
     * @return the response cache, if enabled, otherwise null
     */
    public synchronized HttpResponseCache getResponseCache() {
        return responseCache;
    }

    public void setHttpClient(AbstractHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public AbstractHttpClient getHttpClient() {
        return httpClient;
    }

    public void updateProxySettings() {
        if (appContext == null) {
            return;
        }
        HttpParams httpParams = httpClient.getParams();
        ConnectivityManager connectivity = (ConnectivityManager) appContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nwInfo = connectivity.getActiveNetworkInfo();
        if (nwInfo == null) {
            return;
        }
        Log.i(LOG_TAG, nwInfo.toString());
        if (nwInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
            String proxyHost = Proxy.getHost(appContext);
            if (proxyHost == null) {
                proxyHost = Proxy.getDefaultHost();
            }
            int proxyPort = Proxy.getPort(appContext);
            if (proxyPort == -1) {
                proxyPort = Proxy.getDefaultPort();
            }
            if (proxyHost != null && proxyPort > -1) {
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            } else {
                httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, null);
            }
        } else {
            httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, null);
        }
    }

    public IgnitedHttpRequest get(String url) {
        return get(url, null, false);
    }

    public IgnitedHttpRequest get(String url, boolean cached) {
    	return get(url, null, cached);
    }
    
    public IgnitedHttpRequest get(String url, RequestParams params, boolean cached) {
    	// caching w/ query string is likely not working, we need to sort the params before
    	// building the string to guarantee the same order
    	url = getUrlWithQueryString(url, params);
        if (cached && responseCache != null && responseCache.containsKey(url)) {
            return new CachedHttpRequest(responseCache, url);
        }
        return new HttpGet(this, url, defaultHeaders);
    }

    public IgnitedHttpRequest post(String url) {
        return new HttpPost(this, url, defaultHeaders);
    }

    public IgnitedHttpRequest post(String url, HttpEntity payload) {
        return new HttpPost(this, url, payload, defaultHeaders);
    }
    
    public IgnitedHttpRequest post(String url, RequestParams params) {
    	return new HttpPost(this, url, params.getEntity(), defaultHeaders);
    }

    public IgnitedHttpRequest put(String url) {
        return new HttpPut(this, url, defaultHeaders);
    }

    public IgnitedHttpRequest put(String url, HttpEntity payload) {
        return new HttpPut(this, url, payload, defaultHeaders);
    }
    
    public IgnitedHttpRequest put(String url, RequestParams params) {
    	// need to set the content type here too?
    	return new HttpPut(this, url, params.getEntity(), defaultHeaders);
    }

    public IgnitedHttpRequest delete(String url) {
        return new HttpDelete(this, url, defaultHeaders);
    }

    public void setMaximumConnections(int maxConnections) {
        ConnManagerParams.setMaxTotalConnections(httpClient.getParams(), maxConnections);
    }

    /**
     * Adjust the connection timeout, i.e. the amount of time that may pass in
     * order to establish a connection with the server. Time unit is
     * milliseconds.
     * 
     * @param connectionTimeout
     *            the timeout in milliseconds
     * @see CoreConnectionPNames#CONNECTION_TIMEOUT
     */
    public void setConnectionTimeout(int connectionTimeout) {
        ConnManagerParams.setTimeout(httpClient.getParams(), connectionTimeout);
    }

    /**
     * Adjust the socket timeout, i.e. the amount of time that may pass when
     * waiting for data coming in from the server. Time unit is milliseconds.
     * 
     * @param socketTimeout
     *            the timeout in milliseconds
     * @see CoreConnectionPNames#SO_TIMEOUT
     */
    public void setSocketTimeout(int socketTimeout) {
        HttpConnectionParams.setSoTimeout(httpClient.getParams(), socketTimeout);
    }

    public void setDefaultHeader(String header, String value) {
        defaultHeaders.put(header, value);
    }

    public HashMap<String, String> getDefaultHeaders() {
        return defaultHeaders;
    }

    public void setPortForScheme(String scheme, int port) {
        Scheme _scheme = new Scheme(scheme, PlainSocketFactory.getSocketFactory(), port);
        httpClient.getConnectionManager().getSchemeRegistry().register(_scheme);
    }
    
    private String getUrlWithQueryString(String url, RequestParams params) {
        if(params != null) {
            String paramString = params.getParamString();
            url += "?" + paramString;
        }

        return url;
    }
    
    /**
     * Returns the specified cookie if it exists, or null otherwise.  Very simple usage, does not check
     * domain. 
     * 
     * @param name
     * 			The cookie name
     * @return
     * 		The first cookie that exists with this name.
     */
    public Cookie getCookie(String name) {
    	CookieStore cs = httpClient.getCookieStore();
    	if (cs == null) {
    		return null;
    	}
    	
    	List<Cookie> cookies = cs.getCookies();
    	if (cookies == null) {
    		return null;
    	}
    	
    	for (Cookie cookie : cookies) {
    		if (TextUtils.equals(cookie.getName(), name)) {
    			return cookie;
    		}
    	}
    	return null;
    }

}

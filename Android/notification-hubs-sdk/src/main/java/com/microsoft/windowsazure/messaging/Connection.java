/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Apache 2.0 License
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
     http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */

package com.microsoft.windowsazure.messaging;

import android.os.Build;
import android.util.Base64;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpMethod;

import static com.microsoft.windowsazure.messaging.Utils.isNullOrWhiteSpace;

/**
 * The connection with a Notification Hub server
 */
class Connection {
	/**
	 * Shared access key name
	 */
	private static final String SHARED_ACCESS_KEY_NAME = "SharedAccessKeyName";

	/**
	 * Shared access key
	 */
	private static final String SHARED_ACCESS_KEY = "SharedAccessKey";

	/**
	 * Authorization header
	 */
	private static final String AUTHORIZATION_HEADER = "Authorization";

	/**
	 * UTC timezone
	 */
	private static final String UTC_TIME_ZONE = "UTC";

	/**
	 * UTF-8 encoding
	 */
	private static final String UTF8_ENCODING = "UTF-8";

	/**
	 * Endpoint key
	 */
	private static final String ENDPOINT_KEY = "Endpoint";

	/**
	 * Authentication token expiration minutes
	 */
	private static final int EXPIRE_MINUTES = 5;

	/**
	 * SDK Version
	 */
	private static final String SDK_VERSION = "2014-09";

	/**
	 * API version query string parameter
	 */
	private static final String API_VERSION_KEY = "api-version";

	/**
	 * Api version
	 */
	private static final String API_VERSION = "2014-09";

	/**
	 * Connection data retrieved from connection string
	 */
	private Map<String, String> mConnectionData;

	private final OkHttpClient client;

	/**
	 * Creates a new connection object
	 *
	 * @param connectionString
	 * 		The connection string
	 */
	Connection(String connectionString) {
		mConnectionData = ConnectionStringParser.parse(connectionString);

		OkHttpClient.Builder builder = new OkHttpClient.Builder();
		builder.connectTimeout(30, TimeUnit.SECONDS);
		builder.readTimeout(60, TimeUnit.SECONDS);
		builder.writeTimeout(30, TimeUnit.SECONDS);

		this.client = builder.build();
	}

	/**
	 * Executes a request to the Notification Hub server
	 *
	 * @param resource
	 * 		The resource to access
	 * @param content
	 * 		The request content body
	 * @param contentType
	 * 		The request content type
	 * @param method
	 * 		The request method
	 *
	 * @return The response content body
	 *
	 * @throws Exception
	 */
	String executeRequest(String resource, String content, String contentType, String method) throws Exception {
		return executeRequest(resource, content, contentType, method, null, null);
	}

	/**
	 * Executes a request to the Notification Hub server
	 *
	 * @param resource
	 * 		The resource to access
	 * @param content
	 * 		The request content body
	 * @param contentType
	 * 		The request content type
	 * @param method
	 * 		The request method
	 * @param extraHeaders
	 * 		Extra headers to include in the request
	 *
	 * @return The response content body
	 *
	 * @throws Exception
	 */
	String executeRequest(String resource, String content, String contentType, String method, Map<String, String> extraHeaders) throws Exception {
		return executeRequest(resource, content, contentType, method, null, extraHeaders);
	}

	/**
	 * Executes a request to the Notification Hub server
	 *
	 * @param resource
	 * 		The resource to access
	 * @param content
	 * 		The request content body
	 * @param contentType
	 * 		The request content type
	 * @param method
	 * 		The request method
	 * @param targetHeaderName
	 * 		The header name when we need to get value from it in instead of content
	 *
	 * @return The response content body
	 *
	 * @throws Exception
	 */
	String executeRequest(String resource, String content, String contentType, String method, String targetHeaderName) throws Exception {
		return executeRequest(resource, content, contentType, method, targetHeaderName, null);
	}

	/**
	 * Executes a request to the Notification Hub server
	 *
	 * @param resource
	 * 		The resource to access
	 * @param content
	 * 		The request content body
	 * @param contentType
	 * 		The request content type
	 * @param method
	 * 		The request method
	 * @param targetHeaderName
	 * 		The header name when we need to get value from it in instead of content
	 * @param extraHeaders
	 * 		Extra headers to include in the request
	 *
	 * @return The response content body
	 *
	 * @throws Exception
	 */
	private String executeRequest(String resource, String content, String contentType, String method, String targetHeaderName, Map<String, String> extraHeaders) throws Exception {
		URI endpointURI = URI.create(mConnectionData.get(ENDPOINT_KEY));
		String scheme = endpointURI.getScheme();

		// Replace the scheme with "https"
		String url = "https" + endpointURI.toString().substring(scheme.length());
		if (!url.endsWith("/")) {
			url += "/";
		}

		url += resource;
		url = AddApiVersionToUrl(url);

		Request.Builder builder = new Request.Builder();
		builder.url(url);

		if (HttpMethod.requiresRequestBody(method) && content == null) {
			builder.method(method, RequestBody.create(null, ""));
		} else {
			builder.method(method, content != null ? RequestBody.create(MediaType.parse(contentType), content) : null);
		}

		Headers.Builder headers = new Headers.Builder();
		if (extraHeaders != null) {
			headers = Headers.of(extraHeaders).newBuilder();
		}
		headers.add("User-Agent", getUserAgent());
		headers.add(AUTHORIZATION_HEADER, generateAuthToken(url));

		builder.headers(headers.build());

		return executeRequest(builder.build(), targetHeaderName);
	}

	/**
	 * Adds the API Version querystring parameter to a URL
	 *
	 * @param url
	 * 		The URL to modify
	 *
	 * @return The modified URL
	 */
	private String AddApiVersionToUrl(String url) {
		URI uri = URI.create(url);

		if (uri.getQuery() == null) {
			url = url + "?";
		} else {
			url = url + "&";
		}

		url = url + API_VERSION_KEY + "=" + API_VERSION;

		return url;
	}

	/**
	 * Executes a web request
	 *
	 * @param request
	 * 		The request to execute
	 * @param targetHeaderName
	 * 		The header name when we need to get value from it in instead of content
	 *
	 * @return The content string or header value
	 *
	 * @throws Exception
	 */
	private String executeRequest(Request request, String targetHeaderName) throws Exception {
		int status;
		String content;
		String headerValue = null;
		boolean noHeaderButExpected = false;

		Response response = client.newCall(request).execute();
		status = response.code();
		content = getResponseContent(response);

		if (targetHeaderName != null) {
			List<String> targetHeaderValues = response.headers(targetHeaderName);
			if (targetHeaderValues == null || targetHeaderValues.isEmpty()) {
				noHeaderButExpected = true;
			} else {
				headerValue = targetHeaderValues.get(0);
			}
		}

		if (status >= 200 && status < 300) {
			if (noHeaderButExpected) {
				throw new NotificationHubException("The '" + targetHeaderName + "' header does not present in collection", status);
			}
			return targetHeaderName == null ? content : headerValue;
		} else if (status == 404) {
			throw new NotificationHubResourceNotFoundException();
		} else if (status == 401) {
			throw new NotificationHubUnauthorizedException();
		} else if (status == 410) {
			throw new RegistrationGoneException();
		} else {
			throw new NotificationHubException(content, status);
		}
	}

	/**
	 * Reads the content from a response to a string
	 *
	 * @param response
	 * 		The response to read
	 *
	 * @return The content string
	 *
	 * @throws java.io.IOException
	 */
	private String getResponseContent(Response response) throws IOException {
		ResponseBody responseBody = response.body();
		if (responseBody != null) {
			return responseBody.string();
		} else {
			return null;
		}
	}

	/**
	 * Generates an AuthToken
	 *
	 * @param url
	 * 		The target URL
	 *
	 * @return An AuthToken
	 *
	 * @throws java.security.InvalidKeyException
	 */
	private String generateAuthToken(String url) throws InvalidKeyException {
		String keyName = mConnectionData.get(SHARED_ACCESS_KEY_NAME);
		if (isNullOrWhiteSpace(keyName)) {
			throw new AssertionError("SharedAccessKeyName");
		}

		String key = mConnectionData.get(SHARED_ACCESS_KEY);
		if (isNullOrWhiteSpace(key)) {
			throw new AssertionError("SharedAccessKey");
		}

		try {
			url = URLEncoder.encode(url, UTF8_ENCODING).toLowerCase(Locale.ENGLISH);
		} catch (UnsupportedEncodingException e) {
			// this shouldn't happen because of the fixed encoding
		}

		// Set expiration in seconds
		Calendar expireDate = Calendar.getInstance(TimeZone.getTimeZone(UTC_TIME_ZONE));
		expireDate.add(Calendar.MINUTE, EXPIRE_MINUTES);

		long expires = expireDate.getTimeInMillis() / 1000;

		String toSign = url + '\n' + expires;

		// sign

		byte[] bytesToSign = toSign.getBytes();
		Mac mac = null;
		try {
			mac = Mac.getInstance("HmacSHA256");
		} catch (NoSuchAlgorithmException e) {
			// This shouldn't happen because of the fixed algorithm
		}

		SecretKeySpec secret = new SecretKeySpec(key.getBytes(), mac.getAlgorithm());
		mac.init(secret);
		byte[] signedHash = mac.doFinal(bytesToSign);
		String base64Signature = Base64.encodeToString(signedHash, Base64.DEFAULT);
		base64Signature = base64Signature.trim();
		try {
			base64Signature = URLEncoder.encode(base64Signature, UTF8_ENCODING);
		} catch (UnsupportedEncodingException e) {
			// this shouldn't happen because of the fixed encoding
		}

		// construct authorization string
		return "SharedAccessSignature sr=" + url + "&sig=" + base64Signature + "&se=" + expires + "&skn=" + keyName;
	}

	/**
	 * Generates the User-Agent
	 */
	private String getUserAgent() {
		return String.format("NOTIFICATIONHUBS/%s (api-origin=%s; os=%s; os_version=%s;)",
				SDK_VERSION, PnsSpecificRegistrationFactory.getInstance().getAPIOrigin(), "Android", Build.VERSION.RELEASE);
	}
}

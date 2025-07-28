package com.k_int.folio;

import com.k_int.folio.responses.LoginUsersResponse;
import com.k_int.folio.responses.TokenExpirationResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Low-level HTTP client for interacting with FOLIO APIs.
 * Supports both synchronous and asynchronous request execution.
 * This client is designed to be lightweight, stateless, and used within
 * a request scope. It is not responsible for session or token management
 * but has the ability to perform a login should the calling code need that.
 */
@Slf4j
public class FolioClient {
  /**
   * The {@link HttpClient} client used for making requests to the FOLIO APIs.
   */
  private final HttpClient httpClient;
  /**
   * The base URL of the FOLIO Okapi service.
   * This should be the root URL of the Okapi instance (e.g., <a href="http://localhost:9130">http://localhost:9130</a>).
   */
  private final String baseUrl;
  /**
   * The FOLIO tenant ID for which this client is configured.
   * This is used to set the "X-Okapi-Tenant" header in requests.
   */
  private final String tenant;


  /**
   * The ID of the patron associated with this client's operations, if applicable.
   * This can be used for operations that require a specific patron context.
   * @return The ID of the patron, or null if not applicable.
   */
  @SuppressWarnings("javadoc")
  @Getter
  private final String patronId;

  /**
   * The username for FOLIO authentication, if applicable.
   * This can be null if the client does not require authentication.
   */
  private final String userLogin;
  /**
   * The password for FOLIO authentication, if applicable.
   * This can be null if the client does not require authentication.
   */
  private final String userPassword;

  /**
   * The path to the authentication endpoint for login operations.
   * This is used to perform user login and retrieve authentication tokens.
   */
  private static final String AUTHN_PATH = "/authn";
  /**
   * The path to the user management endpoint for login operations.
   * This is used to perform user login and retrieve user-specific information.
   */
  private static final String USERS_BL_PATH = "/bl-users";

  /**
   * The path used by the authn module for login operations, which returns a {@link TokenExpirationResponse}.
   * This is typically used to authenticate users and manage session tokens.
   */
  private static final String LOGIN_PATH = AUTHN_PATH + "/login-with-expiry"; // This is the path used by the authn module for login, returns a TokenExpirationResponse
  /**
   * The path used by the bl-users module for login operations, which returns a {@link LoginUsersResponse}.
   * This is typically used to authenticate users and manage user-specific sessions.
   */
  private static final String USERS_LOGIN_PATH = USERS_BL_PATH + "/login-with-expiry"; // This is the path used by the bl-users module for login, returns a LoginUsersResponse


  /**
   * Constructs a new FolioClient instance with specified configuration.
   * The {@code ObjectMapper} is initialized internally with {@code JavaTimeModule}.
   *
   * @param baseUrl The base URL of the FOLIO Okapi service (e.g., <a href="http://localhost:9130">http://localhost:9130</a>).
   * @param tenant The FOLIO tenant ID (e.g., "diku").
   * @param patronId The ID of the patron associated with this client's operations, if applicable.
   * @param userLogin The username for FOLIO authentication. Can be null.
   * @param userPassword The password for FOLIO authentication. Can be null.
   */
  public FolioClient(
    String baseUrl,
    String tenant,
    String patronId,
    String userLogin,
    String userPassword
  ) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.tenant = tenant;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    this.patronId = patronId;

    this.userLogin = userLogin;
    this.userPassword = userPassword;
  }

  /**
   * Constructs a new FolioClient instance using a configuration object.
   * This provides an alternative constructor for easier configuration management.
   *
   * @param config The {@link FolioClientConfig} object containing all necessary client configuration.
   */
  public FolioClient(
    FolioClientConfig config
  ) {
    this(config.baseOkapiUri, config.tenantName, config.patronId, config.userLogin, config.userPassword);
  }

  // ------------------- Utility Methods ---------------

  // There's almost definitely a better way to build this URI... this will do for now
  /**
   * Builds a complete URI for a FOLIO API request by combining the base URL,
   * a relative path, and URL-encoded query parameters.
   *
   * @param path The relative path of the resource (e.g., "/users", "/circulation/loans").
   * @param queryParams A {@link Map} of query parameter names and their values.
   * Keys and values will be URL-encoded. Can be {@code null} or empty if no query parameters are needed.
   * @return A {@link URI} object representing the full request URL.
   */
  private URI buildUri(String path, Map<String, String> queryParams) {
    StringBuilder url = new StringBuilder(baseUrl).append(path);

    if (queryParams != null && !queryParams.isEmpty()) {
      StringJoiner joiner = new StringJoiner("&");
      for (Map.Entry<String, String> entry : queryParams.entrySet()) {
        String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
        String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
        joiner.add(key + "=" + value);
      }
      url.append("?").append(joiner);
    }

    return URI.create(url.toString());
  }

  /**
   * Retrieves an array of fundamental HTTP headers required for interacting with FOLIO APIs.
   * This includes the "X-Okapi-Tenant" header configured for this client,
   * along with other base headers defined in {@link FolioHeaders}.
   *
   * @return A String array where each pair of elements represents a header name and its corresponding value
   * (e.g., {@code {"Header-Name", "Header-Value", "Another-Header", "Another-Value"}}).
   */
  public String[] getBaseHeaders() {
    List<String> headers = new ArrayList<>();

    // Make sure we hard coded the tenant header
    headers.add("X-Okapi-Tenant");
    headers.add(tenant);

    for (List<String> pair : FolioHeaders.BASE_HEADERS) {
        headers.add(pair.get(0));
        headers.add(pair.get(1));
    }
    return headers.toArray(new String[0]);
  }

  /**
   * Combines two flat string arrays of HTTP headers into a single new array.
   * Each input array is expected to be in the format {@code {"Name1", "Value1", "Name2", "Value2", ...}}.
   *
   * @param headers1 The first array of header name-value pairs.
   * @param headers2 The second array of header name-value pairs.
   * @return A new String array containing all header name-value pairs from both input arrays.
   */
  public static String[] combineHeaders(String[] headers1, String[] headers2) {
    return Stream.concat(Stream.of(headers1), Stream.of(headers2))
        .toArray(String[]::new);
  }

  /**
   * Merges two maps of query parameters into a single, new map.
   * If a key exists in both maps, the value from {@code map2} will overwrite the value from {@code map1}.
   *
   * @param map1 The first {@link Map} of query parameters.
   * @param map2 The second {@link Map} of query parameters.
   * @return A new {@link Map} containing all key-value pairs from both input maps.
   */
  public static Map<String, String> combineQueryParams(Map<String,String> map1, Map<String, String> map2) {
    return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream()).collect(
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Filters a flat array of HTTP headers, returning only those that are recognized as FOLIO-specific headers.
   * This is determined by consulting {@link FolioHeaders#isFolioHeader(String, String)}.
   *
   * @param flatHeaders A flat array of header name-value pairs (e.g., {@code {"Content-Type", "application/json", "X-Okapi-Token", "token-value"}}).
   * @return A new flat String array containing only the identified FOLIO-specific header name-value pairs.
   */
  public static String[] getFolioHeaders(String[] flatHeaders) {
    List<String> result = new ArrayList<>();

    for (int i = 0; i < flatHeaders.length - 1; i += 2) {
      String name = flatHeaders[i];
      String value = flatHeaders[i + 1];
      if (FolioHeaders.isFolioHeader(name.toLowerCase(), value)) {
        result.add(name);
        result.add(value);
      }
    }

    return result.toArray(new String[0]);
  }

  // --------------- HTTP Methods ---------------

  // --------------- GET Methods ---------------

  /**
   * Asynchronously executes a GET request to the specified path with given headers and query parameters.
   * The response body is automatically deserialized into the specified Java class using {@link FolioClientBodyHandler}.
   * This method returns the full {@link HttpResponse} object, allowing access to headers and status code.
   *
   * @param path The relative path of the resource to fetch (e.g., "/users/{id}").
   * @param headers An array of header name-value pairs to include in the request. Can be empty or {@code null}.
   * @param queryParams A {@link Map} of query parameters. Can be {@code null} or empty.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the {@link HttpResponse} containing
   * the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If there's an error during request construction.
   */
  public <T> CompletableFuture<HttpResponse<T>> getAsyncWithResponse(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) {
    URI uri = buildUri(path, queryParams);
    String[] finalHeaders = combineHeaders(getBaseHeaders(), headers);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(uri)
      .GET()
      .headers(finalHeaders)
      .build();

    return httpClient.sendAsync(request, new FolioClientBodyHandler<>(responseType))
      .exceptionally((ex) -> {
        throw new CompletionException("Something went wrong with GET call", new FolioClientException("GET request failed: " + ex.getMessage(), FolioClientException.GENERIC_ERROR, ex));
      });
  }

  /**
   * Synchronously executes a GET request and deserializes the response body into the specified class.
   * This is a blocking convenience method that wraps {@link #getAsyncWithResponse(String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to fetch.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The {@link HttpResponse} containing the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If the request fails (e.g., network error, non-2xx status, deserialization error),
   * times out, or is interrupted.
   */
  public <T> HttpResponse<T> getWithResponse(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) {
    return asyncFolioClientExceptionHelper(() -> getAsyncWithResponse(path, headers, queryParams, responseType));
  }

  /**
   * Asynchronously executes a GET request and returns only the deserialized response body.
   * This is a convenience method that builds on {@link #getAsyncWithResponse(String, String[], Map, Class)}
   * by directly mapping the {@link HttpResponse} to its body.
   *
   * @param path The relative path of the resource to fetch.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails, as the underlying {@code getAsyncWithResponse} will propagate it.
   */
  public <T> CompletableFuture<T> getAsync(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) {
    return getAsyncWithResponse(path, headers, queryParams, responseType)
      .thenApply(HttpResponse::body);
  }

  /**
   * Synchronously executes a GET request and returns only the deserialized response body.
   * This is a blocking convenience method that wraps {@link #getAsync(String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to fetch.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails or if the response is not as expected.
   */
  public <T> T get(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsync(path, headers, queryParams, responseType));
  }

  // --------------- POST Methods ---------------
  /**
   * Asynchronously posts a string body to the specified path with given headers and query parameters.
   * The response body is automatically deserialized into the specified Java class using {@link FolioClientBodyHandler}.
   * This method returns the full {@link HttpResponse} object, allowing access to headers and status code.
   *
   * @param path The relative path of the resource to post to (e.g., "/circulation/loans").
   * @param body The string content to send as the request body (e.g., a JSON string representing a new entity).
   * @param headers An array of header name-value pairs to include in the request. Can be empty or {@code null}.
   * @param queryParams A {@link Map} of query parameters. Can be {@code null} or empty.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the {@link HttpResponse} containing
   * the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If there's an error during request construction.
   */
  public <T> CompletableFuture<HttpResponse<T>> postStringBodyAsyncWithResponse(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    URI uri = buildUri(path, queryParams);
    String[] finalHeaders = combineHeaders(getBaseHeaders(), headers);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(uri)
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .headers(finalHeaders)
      .build();

    return httpClient.sendAsync(request, new FolioClientBodyHandler<>(responseType))
      .exceptionally((ex) -> {
        throw new CompletionException("Something went wrong with POST call", new FolioClientException("POST request failed: " + ex.getMessage(), FolioClientException.GENERIC_ERROR, ex));
      });
  }

  /**
   * Synchronously posts a string body to the specified path and deserializes the response body into the specified class.
   * This is a blocking convenience method that wraps {@link #postStringBodyAsyncWithResponse(String, String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to post to.
   * @param body The string content to send as the request body.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The {@link HttpResponse} containing the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If the request fails (e.g., network error, non-2xx status, deserialization error),
   * times out, or is interrupted.
   */
  public <T> HttpResponse<T> postStringBodyWithResponse(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> postStringBodyAsyncWithResponse(path, body, headers, queryParams, responseType));
  }

  /**
   * Asynchronously posts a string body to the specified path and returns only the deserialized response body.
   * This is a convenience method that builds on {@link #postStringBodyAsyncWithResponse(String, String, String[], Map, Class)}
   * by directly mapping the {@link HttpResponse} to its body.
   *
   * @param path The relative path of the resource to post to.
   * @param body The string content to send as the request body.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails, as the underlying {@code postStringBodyAsyncWithResponse} will propagate it.
   */
  public <T> CompletableFuture<T> postStringBodyAsync(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return postStringBodyAsyncWithResponse(path, body, headers, queryParams, responseType)
      .thenApply(HttpResponse::body);
  }

  /**
   * Synchronously posts a string body to the specified path and returns only the deserialized response body.
   * This is a blocking convenience method that wraps {@link #postStringBodyAsync(String, String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to post to.
   * @param body The string content to send as the request body.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails or if the response is not as expected.
   */
  public <T> T postStringBody(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> postStringBodyAsync(path, body, headers, queryParams, responseType));
  }

  /**
   * Asynchronously puts a string body to the specified path with given headers and query parameters.
   * The response body is automatically deserialized into the specified Java class using {@link FolioClientBodyHandler}.
   * This method returns the full {@link HttpResponse} object, allowing access to headers and status code.
   *
   * @param path The relative path of the resource to put to (e.g., "/users/{id}").
   * @param body The string content to send as the request body (e.g., a JSON string representing an updated entity).
   * @param headers An array of header name-value pairs to include in the request. Can be empty or {@code null}.
   * @param queryParams A {@link Map} of query parameters. Can be {@code null} or empty.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the {@link HttpResponse} containing
   * the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If there's an error during request construction.
   */
  public <T> CompletableFuture<HttpResponse<T>> putStringBodyAsyncWithResponse(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    URI uri = buildUri(path, queryParams);
    String[] finalHeaders = combineHeaders(getBaseHeaders(), headers);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(uri)
      .PUT(HttpRequest.BodyPublishers.ofString(body))
      .headers(finalHeaders)
      .build();

    return httpClient.sendAsync(request, new FolioClientBodyHandler<>(responseType))
      .exceptionally((ex) -> {
        throw new CompletionException("Something went wrong with PUT call", new FolioClientException("PUT request failed: " + ex.getMessage(), FolioClientException.GENERIC_ERROR, ex));
      });
  }

  /**
   * Synchronously puts a string body to the specified path and deserializes the response body into the specified class.
   * This is a blocking convenience method that wraps {@link #putStringBodyAsyncWithResponse(String, String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to put to.
   * @param body The string content to send as the request body.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The {@link HttpResponse} containing the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If the request fails (e.g., network error, non-2xx status, deserialization error),
   * times out, or is interrupted.
   */
  public <T> HttpResponse<T> putStringBodyWithResponse(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> putStringBodyAsyncWithResponse(path, body, headers, queryParams, responseType));
  }

  /**
   * Asynchronously puts a string body to the specified path and returns only the deserialized response body.
   * This is a convenience method that builds on {@link #putStringBodyAsyncWithResponse(String, String, String[], Map, Class)}
   * by directly mapping the {@link HttpResponse} to its body.
   *
   * @param path The relative path of the resource to put to.
   * @param body The string content to send as the request body.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails, as the underlying {@code putStringBodyAsyncWithResponse} will propagate it.
   */
  public <T> CompletableFuture<T> putStringBodyAsync(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return putStringBodyAsyncWithResponse(path, body, headers, queryParams, responseType)
      .thenApply(HttpResponse::body);
  }

  /**
   * Synchronously puts a string body to the specified path and returns only the deserialized response body.
   * This is a blocking convenience method that wraps {@link #putStringBodyAsync(String, String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to put to.
   * @param body The string content to send as the request body.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails or if the response is not as expected.
   */
  public <T> T putStringBody(String path, String body, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> putStringBodyAsync(path, body, headers, queryParams, responseType));
  }

  /**   * Asynchronously executes a DELETE request to the specified path with given headers and query parameters.
   * The response body is automatically deserialized into the specified Java class using {@link FolioClientBodyHandler}.
   * This method returns the full {@link HttpResponse} object, allowing access to headers and status code.
   *
   * @param path The relative path of the resource to delete (e.g., "/users/{id}").
   * @param headers An array of header name-value pairs to include in the request. Can be empty or {@code null}.
   * @param queryParams A {@link Map} of query parameters. Can be {@code null} or empty.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the {@link HttpResponse} containing
   * the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If there's an error during request construction.
   */
  public <T> CompletableFuture<HttpResponse<T>> deleteAsyncWithResponse(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    URI uri = buildUri(path, queryParams);
    String[] finalHeaders = combineHeaders(getBaseHeaders(), headers);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(uri)
      .DELETE()
      .headers(finalHeaders)
      .build();

    return httpClient.sendAsync(request, new FolioClientBodyHandler<>(responseType))
      .exceptionally((ex) -> {
        throw new CompletionException("Something went wrong with DELETE call", new FolioClientException("DELETE request failed: " + ex.getMessage(), FolioClientException.GENERIC_ERROR, ex));
      });
  }

  /**
   * Synchronously executes a DELETE request and deserializes the response body into the specified class.
   * This is a blocking convenience method that wraps {@link #deleteAsyncWithResponse(String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to delete.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The {@link HttpResponse} containing the deserialized object of type {@code T} in its body.
   * @throws FolioClientException If the request fails (e.g., network error, non-2xx status, deserialization error),
   * times out, or is interrupted.
   */
  public <T> HttpResponse<T> deleteWithResponse(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> deleteAsyncWithResponse(path, headers, queryParams, responseType));
  }

  /**
   * Asynchronously executes a DELETE request and returns only the deserialized response body.
   * This is a convenience method that builds on {@link #deleteAsyncWithResponse(String, String[], Map, Class)}
   * by directly mapping the {@link HttpResponse} to its body.
   *
   * @param path The relative path of the resource to delete.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return A {@link CompletableFuture} that completes with the deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails, as the underlying {@code deleteAsyncWithResponse} will propagate it.
   */
  public <T> CompletableFuture<T> deleteAsync(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return deleteAsyncWithResponse(path, headers, queryParams, responseType)
      .thenApply(HttpResponse::body);
  }

  /**
   * Synchronously executes a DELETE request and returns only the deserialized response body.
   * This is a blocking convenience method that wraps {@link #deleteAsync(String, String[], Map, Class)}
   * and handles common asynchronous exceptions (timeout, interruption) as {@link FolioClientException}.
   *
   * @param path The relative path of the resource to delete.
   * @param headers An array of header name-value pairs to include in the request.
   * @param queryParams A {@link Map} of query parameters.
   * @param responseType The {@link Class} representing the target type for deserializing the JSON response body.
   * @param <T> The type of the expected deserialized response object.
   * @return The deserialized object of type {@code T}.
   * @throws FolioClientException If the request fails or if the response is not as expected.
   */
  public <T> T delete(String path, String[] headers, Map<String, String> queryParams, Class<T> responseType) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> deleteAsync(path, headers, queryParams, responseType));
  }
  // --------------- Login Methods ---------------

  /**
   * Asynchronously initiates a login request using the client's configured credentials
   * and the {@code /bl-users/login-with-expiry} endpoint. The full {@link HttpResponse}
   * containing a {@link LoginUsersResponse} object is returned within a {@link CompletableFuture}.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return A {@link CompletableFuture} that completes with an {@link HttpResponse} containing the deserialized
   * {@link LoginUsersResponse} object upon successful login.
   * @throws FolioClientException If there's an issue constructing the request, which results in a synchronous exception.
   */
  public CompletableFuture<HttpResponse<LoginUsersResponse>> loginWithUsersAsyncWithResponse(String[] headers) {
    String credBody = "{ \"username\": \"" + userLogin + "\",  \"password\": \"" + userPassword + "\"}";

    return postStringBodyAsyncWithResponse(
      USERS_LOGIN_PATH,
      credBody,
      headers,
      Collections.emptyMap(),
      LoginUsersResponse.class
    );
  }

  /**
   * Synchronously performs a login request using the client's configured credentials
   * and the {@code /bl-users/login-with-expiry} endpoint. This is a blocking convenience method
   * that wraps {@link #loginWithUsersAsyncWithResponse(String[])} and handles common asynchronous exceptions
   * (timeout, interruption) as {@link FolioClientException}.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return The {@link HttpResponse} containing the deserialized {@link LoginUsersResponse} object upon successful login.
   * @throws FolioClientException If the request fails (e.g., network error, non-2xx status, deserialization error),
   * times out, or is interrupted.
   */
  public HttpResponse<LoginUsersResponse> loginWithUsersWithResponse(String[] headers) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> loginWithUsersAsyncWithResponse(headers));
  }

  /**
   * Asynchronously performs a login request using the client's configured credentials
   * and the {@code /bl-users/login-with-expiry} endpoint. This method returns only the deserialized
   * response body, allowing for easier access to the {@link LoginUsersResponse} object.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return A {@link CompletableFuture} that completes with the deserialized {@link LoginUsersResponse} object upon successful login.
   * @throws FolioClientException If the request fails, as the underlying {@code loginWithUsersAsyncWithResponse} will propagate it.
   */
  public CompletableFuture<LoginUsersResponse> loginWithUsersAsync(String[] headers) {
    return loginWithUsersAsyncWithResponse(headers)
      .thenApply(HttpResponse::body);
  }

  /**
   * Synchronously performs a login request using the client's configured credentials
   * and the {@code /bl-users/login-with-expiry} endpoint. This is a blocking convenience method
   * that wraps {@link #loginWithUsersAsync(String[])} and handles common asynchronous exceptions
   * (timeout, interruption) as {@link FolioClientException}.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return The deserialized {@link LoginUsersResponse} object upon successful login.
   * @throws FolioClientException If the request fails or if the response is not as expected.
   */
  public LoginUsersResponse loginWithUsers(String[] headers) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> loginWithUsersAsync(headers));
  }

  /**
   * Asynchronously initiates a login request using the client's configured credentials
   * and the {@code /authn/login-with-expiry} endpoint. The full {@link HttpResponse}
   * containing a {@link TokenExpirationResponse} object is returned within a {@link CompletableFuture}.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return A {@link CompletableFuture} that completes with an {@link HttpResponse} containing the deserialized
   * {@link TokenExpirationResponse} object upon successful login.
   * @throws FolioClientException If there's an issue constructing the request, which results in a synchronous exception.
   */
  public CompletableFuture<HttpResponse<TokenExpirationResponse>> loginAsyncWithResponse(String[] headers) {
    String credBody = "{ \"username\": \"" + userLogin + "\",  \"password\": \"" + userPassword + "\"}";

    return postStringBodyAsyncWithResponse(
      LOGIN_PATH,
      credBody,
      headers,
      Collections.emptyMap(),
      TokenExpirationResponse.class
    );
  }

  /**
   * Synchronously performs a login request using the client's configured credentials
   * and the {@code /authn/login-with-expiry} endpoint. This is a blocking convenience method
   * that wraps {@link #loginAsyncWithResponse(String[])} and handles common asynchronous exceptions
   * (timeout, interruption) as {@link FolioClientException}.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return The {@link HttpResponse} containing the deserialized {@link TokenExpirationResponse} object upon successful login.
   * @throws FolioClientException If the request fails (e.g., network error, non-2xx status, deserialization error),
   * times out, or is interrupted.
   */
  public HttpResponse<TokenExpirationResponse> loginWithResponse(String[] headers) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> loginAsyncWithResponse(headers));
  }

  /**
   * Asynchronously performs a login request using the client's configured credentials
   * and the {@code /authn/login-with-expiry} endpoint. This method returns only the deserialized
   * response body, allowing for easier access to the {@link TokenExpirationResponse} object.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return A {@link CompletableFuture} that completes with the deserialized {@link TokenExpirationResponse} object upon successful login.
   * @throws FolioClientException If the request fails, as the underlying {@code loginAsyncWithResponse} will propagate it.
   */
  public CompletableFuture<TokenExpirationResponse> loginAsync(String[] headers) {
    return loginAsyncWithResponse(headers)
      .thenApply(HttpResponse::body);
  }

  /**
   * Synchronously performs a login request using the client's configured credentials
   * and the {@code /authn/login-with-expiry} endpoint. This is a blocking convenience method
   * that wraps {@link #loginAsync(String[])} and handles common asynchronous exceptions
   * (timeout, interruption) as {@link FolioClientException}.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return The deserialized {@link TokenExpirationResponse} object upon successful login.
   * @throws FolioClientException If the request fails or if the response is not as expected.
   */
  public TokenExpirationResponse login(String[] headers) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> loginAsync(headers));
  }

  /**
   * Asynchronously performs a login and extracts the "folioAccessToken" cookie from the response headers.
   * This method is crucial for obtaining the authentication token needed for subsequent FOLIO API calls.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return A {@link CompletableFuture} that, upon successful login and cookie extraction,
   * provides a String array in the format {@code {"Cookie", "folioAccessToken=YOUR_TOKEN_VALUE"}}.
   * @throws FolioClientException If the login fails (e.g., bad credentials, network issues),
   * or if the "folioAccessToken" cookie is not found in the successful response.
   */
  public CompletableFuture<String[]> getFolioAccessTokenCookieAsync(String[] headers) throws FolioClientException {
    return loginAsyncWithResponse(headers)
      .thenApply(resp -> {
        String folioAccessToken = "";
        for (String string : resp.headers().map().get("set-cookie")) {
          if (string.matches("folioAccessToken=.*")) {
            folioAccessToken = string;
          }
        }

        return new String[] { "Cookie", folioAccessToken };
      });
  }

  /**
   * Synchronously performs a login and retrieves the "folioAccessToken" cookie.
   * This is a blocking convenience method that wraps the asynchronous {@link #getFolioAccessTokenCookieAsync(String[])}
   * and handles common exceptions by rethrowing them as {@link FolioClientException}.
   *
   * @param headers Additional HTTP headers to include with the login request. Can be empty or {@code null}.
   * @return A String array in the format {@code {"Cookie", "folioAccessToken=YOUR_TOKEN_VALUE"}}
   * suitable for use in subsequent request headers.
   * @throws FolioClientException If the underlying asynchronous call fails, times out, or is interrupted,
   * or if the login fails or the cookie is not found.
   */
  public String[] getFolioAccessTokenCookie(String[] headers) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getFolioAccessTokenCookieAsync(headers));
  }

  // --------------- Exception Handling ---------------

  /**
   * A protected helper method to bridge asynchronous {@link CompletableFuture} operations
   * with synchronous blocking calls. It executes the provided {@link CompletableFuture}
   * and unwraps common exceptions ({@link ExecutionException}, {@link TimeoutException}, {@link InterruptedException})
   * into a more specific {@link FolioClientException}.
   *
   * @param supplier A {@link Supplier} that provides the {@link CompletableFuture} to be executed.
   * @param <T> The type of the result returned by the {@link CompletableFuture}.
   * @return The resolved result of the {@link CompletableFuture}.
   * @throws FolioClientException If the future fails (its cause is a {@link FolioClientException} or another exception),
   * if the call times out, or if the current thread is interrupted while waiting.
   */
  protected <T> T asyncFolioClientExceptionHelper(Supplier<CompletableFuture<T>> supplier) throws FolioClientException {
    try {
      return supplier.get().get(20, TimeUnit.SECONDS); // configurable timeout -- OKAPI can be sloooooow
    } catch (ExecutionException e) {
      if (e.getCause() instanceof FolioClientException) {
        log.error("Async execution failed with FolioClientException: {}", e.getCause().getMessage(), e);
        throw (FolioClientException) e.getCause(); // rethrow as-is
      }

      log.error("Unhandled async execution error", e);
      throw new FolioClientException("Unhandled async execution error", FolioClientException.GENERIC_ERROR, e.getCause());
    } catch (TimeoutException e) {
      log.error("Async call timed out", e);
      throw new FolioClientException("Async call timed out", FolioClientException.TIMEOUT_ERROR, e);
    } catch (InterruptedException e) {
      log.error("Async call interrupted", e);
      Thread.currentThread().interrupt();
      throw new FolioClientException("Async call interrupted", FolioClientException.INTERRUPTED_ERROR, e);
    } catch (Exception e) {
      log.error("Unhandled error", e);
      throw new FolioClientException("Unhandled error", FolioClientException.GENERIC_ERROR, e);
    }
  }
}

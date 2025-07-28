package com.k_int.folio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

@Slf4j
public class FolioClientBodyHandler<T> implements HttpResponse.BodyHandler<T> {
  /**
   * An {@link ObjectMapper} instance used to deserialize the response body
   */
  private final ObjectMapper objectMapper;
  /**
   * The target type to which the response body should be deserialized.
   * This can be String.class, InputStream.class, or any other class that Jackson can deserialize to.
   */
  private final Class<T> targetType;

  /**
   * Constructs a new FolioClientBodyHandler with the specified target type.
   * <p>
   * This constructor initializes an ObjectMapper instance that is used to deserialize the response body
   * into the specified target type.
   * </p>
   *
   * @param targetType The class type to which the response body should be deserialized.
   */
  public FolioClientBodyHandler(Class<T> targetType) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule()); // Allows for parsing Instant, LocalDateTime, etc.

    this.objectMapper = mapper;
    this.targetType = targetType;
  }

  /**
   * This method is used to return a BodySubscriber that reads the response body as a String.
   * It is only called when the targetType is String.class.
   *
   * @return A BodySubscriber that reads the response body as a String.
   */
  @SuppressWarnings("unchecked")
  private HttpResponse.BodySubscriber<T> getStringBodySubscriber() {
    // This cast is safe because it's only called when targetType is String.class
    return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * This method is used to return a BodySubscriber that reads the response body as an InputStream.
   * It is only called when the targetType is InputStream.class.
   *
   * @return A BodySubscriber that reads the response body as an InputStream.
   */
  @SuppressWarnings("unchecked")
  private HttpResponse.BodySubscriber<T> getInputStreamBodySubscriber() {
    // This cast is safe because it's only called when targetType is String.class
    return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.ofInputStream();
  }

  /**
   * Applies the BodyHandler to the given HttpResponse.ResponseInfo.
   * <p>
   * This method checks the status code of the response and returns a BodySubscriber that either
   * deserializes the body into the specified target type or handles error responses.
   * </p>
   *
   * @param responseInfo The response information containing the status code and headers.
   * @return A BodySubscriber that processes the response body.
   */
  @Override
  public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
    // Check for success status codes (2xx)
    if (responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 300) {

      if (targetType.equals(String.class)) {
        // If the target type is String, we can directly return a BodySubscriber that reads the body as a String
        return getInputStreamBodySubscriber();
      }

      if (targetType.equals(InputStream.class)) {
        // If the target type is String, we can directly return a BodySubscriber that reads the body as a String
        return getStringBodySubscriber();
      }

      // If successful, we want to deserialize the body
      return HttpResponse.BodySubscribers.mapping(
        HttpResponse.BodySubscribers.ofInputStream(),
        inputStream -> {
          try (InputStream is = inputStream) { // Ensure stream is closed
            if (is == null) {
              return null;
            }
            return objectMapper.readValue(is, targetType);
          } catch (IOException e) {
            String errorMessage = "Failed to deserialize response body to " + targetType.getSimpleName();
            log.error(errorMessage, e);
            throw new FolioClientException(errorMessage, FolioClientException.RESPONSE_WRONG_SHAPE, e);
          }
        }
      );
    } else {
      return HttpResponse.BodySubscribers.mapping(
        HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8),
        errorBody -> {
          log.error("Received non-success status {}. Raw body: {}", responseInfo.statusCode(), errorBody);
          throw new FolioClientException(
            "HTTP request failed with status: " + responseInfo.statusCode() + " - " + errorBody,
            FolioClientException.REQUEST_NOT_OK
          );
        }
      );
    }
  }
}
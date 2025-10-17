package com.k_int.folio;

/**
 * Exception class for handling errors in Folio client operations.
 * <p>
 * This class extends the Exception class to provide specific error codes and messages
 * for various types of errors that may occur while interacting with the Folio API.
 * </p>
 */
public class FolioClientException extends RuntimeException {
  /**
   * Generic error code, used when no more specific code is applicable.
   */
  public static final Long GENERIC_ERROR = 0L;

  /**
   * The request to the server failed to complete.
   */
  public static final Long FAILED_REQUEST = 1L;

  /**
   * The server responded, but with a non-OK status.
   */
  public static final Long REQUEST_NOT_OK = 2L;

  /**
   * The response did not match the expected format.
   */
  public static final Long RESPONSE_WRONG_SHAPE = 3L;

  /**
   * A network error occurred during the request.
   */
  public static final Long NETWORK_ERROR = 4L;

  /**
   * The request timed out.
   */
  public static final Long TIMEOUT_ERROR = 5L;

  /**
   * The request was interrupted.
   */
  public static final Long INTERRUPTED_ERROR = 6L;

  /**
   * The code representing the specific error, should be set using one of the predefined constants.
   * If no specific code is applicable, it defaults to {@link #GENERIC_ERROR}.
   */
  final Long code;
  /**
   * The underlying cause of this exception, if any.
   * This can be used to retrieve more information about the error.
   */
  final Throwable cause;

  /**
   * Constructs a new FolioClientException with the specified detail message.
   * The error code defaults to {@link #GENERIC_ERROR} and the cause is {@code null}.
   *
   * @param errorMessage The detail message (which is saved for later retrieval by the {@link Throwable#getMessage()} method).
   */
  public FolioClientException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = null;
  }

  /**
   * Constructs a new FolioClientException with the specified detail message and a specific error code.
   * The cause is {@code null}.
   *
   * @param errorMessage The detail message.
   * @param code The specific error code.
   */
  public FolioClientException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
    this.cause = null;
  }

  /**
   * Constructs a new FolioClientException with the specified detail message and cause.
   * The error code defaults to {@link #GENERIC_ERROR}.
   *
   * @param errorMessage The detail message.
   * @param cause The underlying cause of this exception, if any.
   */
  public FolioClientException(String errorMessage, Throwable cause) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
    this.cause = cause;
  }

  /**
   * Constructs a new FolioClientException with the specified detail message, error code, and cause.
   *
   * @param errorMessage The detail message.
   * @param code The specific error code.
   * @param cause The underlying cause of this exception, if any.
   */
  public FolioClientException(String errorMessage, Long code, Throwable cause) {
    super(errorMessage);
    this.code = code;
    this.cause = cause;
  }

  @Override
  public Throwable getCause() {
    return cause;
  }
}

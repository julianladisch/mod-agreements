package com.k_int.folio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


// FIXME This was conceived as an attempt to fix stream cancellations, but changing to HTTP1.1 did that
// Knowing then that this is slightly out of my depth right now, I am putting this class' use on hold
/** * FolioClientExecutor is a utility class that provides a centralized executor service
 * for managing HTTP client requests in a Folio application.
 * <p>
 * It uses a fixed thread pool to handle concurrent HTTP requests efficiently.
 * NOTE: This was conceived as an attempt to fix stream cancellations, but changing to HTTP1.1 did that.
 * Therefore, this class is currently not in use, and its implementation is on hold.
 * </p>
 */
public class FolioClientExecutor {

  /** * The size of the HTTP client thread pool.
   * <p>
   * This value determines how many concurrent HTTP requests can be processed.
   * Adjust this value based on the expected load and performance requirements.
   * </p>
   */
  private static final int HTTP_CLIENT_POOL_SIZE = 20; // Example size

  // Use a private static final to ensure it's a singleton and managed centrally
  /** * The executor service for handling HTTP client requests.
   * <p>
   * This executor uses a fixed thread pool with the size defined by {@link #HTTP_CLIENT_POOL_SIZE}.
   * It is designed to handle HTTP requests concurrently, improving performance and responsiveness.
   * </p>
   */
  private static final ExecutorService HTTP_CLIENT_EXECUTOR =
    new ThreadPoolExecutor(
      HTTP_CLIENT_POOL_SIZE, // corePoolSize
      HTTP_CLIENT_POOL_SIZE, // maximumPoolSize (fixed size pool)
      0L, TimeUnit.MILLISECONDS, // keepAliveTime
      new java.util.concurrent.LinkedBlockingQueue<>(), // unbounded queue for tasks
      Executors.defaultThreadFactory() // default thread factory
    );

  /**
   * Shuts down the HTTP client executor service.
   */
  public static void shutdownExecutor() {
    HTTP_CLIENT_EXECUTOR.shutdown(); // Initiates an orderly shutdown
    try {
      if (!HTTP_CLIENT_EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
        HTTP_CLIENT_EXECUTOR.shutdownNow(); // Force shutdown if not terminated gracefully
      }
    } catch (InterruptedException e) {
      HTTP_CLIENT_EXECUTOR.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Returns the HTTP client executor service.
   * <p>
   * This method provides access to the executor service for submitting HTTP client tasks.
   * </p>
   *
   * @return the executor service used for handling HTTP client requests
   */
  public static ExecutorService getHttpClientExecutor() {
    return HTTP_CLIENT_EXECUTOR;
  }
}
package com.k_int.folio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


// FIXME This was conceived as an attempt to fix stream cancellations, but changing to HTTP1.1 did that
// Knowing then that this is slightly out of my depth right now, I am putting this class' use on hold
public class FolioClientExecutor {

  private static final int HTTP_CLIENT_POOL_SIZE = 20; // Example size

  // Use a private static final to ensure it's a singleton and managed centrally
  private static final ExecutorService HTTP_CLIENT_EXECUTOR =
    new ThreadPoolExecutor(
      HTTP_CLIENT_POOL_SIZE, // corePoolSize
      HTTP_CLIENT_POOL_SIZE, // maximumPoolSize (fixed size pool)
      0L, TimeUnit.MILLISECONDS, // keepAliveTime
      new java.util.concurrent.LinkedBlockingQueue<>(), // unbounded queue for tasks
      Executors.defaultThreadFactory() // default thread factory
    );

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

  // You'll need to expose this executor
  public static ExecutorService getHttpClientExecutor() {
    return HTTP_CLIENT_EXECUTOR;
  }
}
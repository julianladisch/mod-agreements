package com.k_int.folio;

/**
 * Functional interface representing a call to the FOLIO client.
 * <p>
 * This interface is used to encapsulate operations that can be executed against the FOLIO client,
 * allowing for exception handling and retry logic to be applied uniformly.
 * </p>
 *
 * @param <T> the type of the result returned by the FOLIO client call
 */
@FunctionalInterface
public interface FolioCall<T> {
  /**
   * Executes the FOLIO client call.
   * <p>
   * This method is expected to perform the operation defined by the implementing class
   * and return a result of type {@code T}. It may throw an exception if the operation fails.
   * </p>
   *
   * @return the result of the FOLIO client call
   * @throws Exception if an error occurs during the execution of the call
   */
  T execute() throws Exception;
}

package com.k_int.folio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Configuration class for Folio client settings.
 * <p>
 * This class encapsulates the necessary configuration parameters for connecting to
 * a Folio instance, including the base URI, tenant name, patron ID, and optional user credentials.
 * </p>
 */
@Getter
@AllArgsConstructor
@Builder
@SuppressWarnings("javadoc")
public class FolioClientConfig {
  /**
   * The base URI of the Folio instance.
   * This is the root URL used to access the Folio APIs.
   *
   * @param baseOkapiUri the base URI of the Folio instance
   * @return the base URI of the Folio instance
   */
  final String baseOkapiUri;
  /**
   * The name of the tenant in the Folio instance.
   * This is used to identify the specific tenant for which the client is configured.
   *
   * @param tenantName the name of the tenant
   * @return the name of the tenant
   */
  final String tenantName;

  /**
   * The patron ID used for authentication.
   * This is typically the identifier of the user or system that is accessing the Folio APIs.
   *
   * @param patronId the patron ID for authentication
   * @return the patron ID for authentication
   */
  final String patronId;

  /**
   * Optional login for user authentication.
   * This can be used to specify a username for Folio API access.
   *
   * @param userLogin the login name of the user
   * @return the login name of the user
   */
  @Nullable
  final String userLogin;
  /**
   * Optional password for user authentication.
   * This can be used to specify a password for Folio API access.
   *
   * @param userPassword the password of the user
   * @return the password of the user
   */
  @Nullable
  final String userPassword;
}

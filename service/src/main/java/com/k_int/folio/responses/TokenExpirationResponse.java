package com.k_int.folio.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.time.Instant;

/** A FOLIO login response.
 * <p>
 * This class represents the structure of a login response from the authn/login-with-expiry endpoint in a FOLIO system,
 * including token expiration times.
 * </p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("javadoc")
public class TokenExpirationResponse {
  /**
   * The expiration date ({@link Instant}) for the refresh token from the FOLIO login operation
   * @return the expiration date for the refresh token
   */
  Instant refreshTokenExpiration;
  /**
   * The expiration date ({@link Instant}) for the access token from the FOLIO login operation
   * @return the expiration date for the access token
   */
  Instant accessTokenExpiration;
}
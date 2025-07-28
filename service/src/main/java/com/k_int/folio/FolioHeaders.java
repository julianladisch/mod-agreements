package com.k_int.folio;

import java.util.List;
import java.util.Set;

/**
 * Utility class for handling FOLIO-specific headers and cookies.
 * <p>
 * This class provides constants for FOLIO headers and methods to check if a header or cookie
 * is related to FOLIO.
 * </p>
 */
public class FolioHeaders {
  /**
   * FOLIO headers that are used for authentication and tenant identification.
   * <p>
   * These headers are typically included in requests to FOLIO services.
   * </p>
   */
  public static final Set<String> FOLIO_HEADERS = Set.of(
    "x-okapi-tenant",
    "x-okapi-token"
  );

  /**
   * Prefixes for FOLIO cookies that are used for authentication.
   * <p>
   * These prefixes help identify cookies related to FOLIO access and refresh tokens.
   * </p>
   */
  public static final List<String> FOLIO_COOKIE_PREFIXES = List.of(
    "folioAccessToken=",
    "folioRefreshToken="
  );

  /**
   * Base headers that are commonly used in FOLIO requests.
   * <p>
   * Each entry in this list <b>must be a pair</b> of header name and value,
   * as required by Java HTTP client APIs (e.g., {@code HttpRequest.Builder.header(String, String)}).
   * This ensures compatibility and correct construction of HTTP requests for FOLIO services.
   * </p>
   * <p>
   * This list includes headers for content type and acceptance, which are essential
   * for JSON-based APIs in FOLIO.
   * </p>
   */
  public static final List<List<String>> BASE_HEADERS = List.of(
    List.of("Content-Type", "application/json"),
    List.of("accept", "application/json")
  );

  /**
   * Checks if the given cookie value contains any FOLIO-specific prefixes.
   * <p>
   * This method is used to determine if a cookie is related to FOLIO access or refresh tokens.
   * </p>
   *
   * @param cookieValue the cookie value to check
   * @return true if the cookie value contains a FOLIO prefix, false otherwise
   */
  public static boolean isFolioCookie(String cookieValue) {
    for (String prefix : FOLIO_COOKIE_PREFIXES) {
      if (cookieValue.contains(prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the given header name and value are related to FOLIO.
   * <p>
   * This method checks if the header name is one of the known FOLIO headers or if it is a cookie
   * that contains a FOLIO-specific prefix.
   * </p>
   *
   * @param headerName  the name of the header to check
   * @param headerValue the value of the header to check
   * @return true if the header is a FOLIO header or a FOLIO cookie, false otherwise
   */
  public static boolean isFolioHeader(String headerName, String headerValue) {
    return FOLIO_HEADERS.contains(headerName.toLowerCase()) || (headerName.equalsIgnoreCase("cookie") && isFolioCookie(headerValue));
  }
}
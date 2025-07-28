package com.k_int.folio.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** A FOLIO login response.
 * <p>
 * This class represents the structure of a login response from the bl-users endpoint in a FOLIO system,
 * including user details, patron group information, permissions, service points,
 * and token expiration times.
 * </p>
 * <p>
 *   This class attempts to map the JSON response from FOLIO's login endpoint to
 *   the Getter we might be interested in using from a login response.
 * </p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("javadoc")
public class LoginUsersResponse {
  /**
   * A FOLIO login User object for the {@link LoginUsersResponse}.
   * <p>
   * This class contains the user information returned by the FOLIO login endpoint,
   * such as user ID, username, barcode, active status, etc
   * </p>
   */
  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class User {
    /**
     * A FOLIO login User Personal object for the {@link User} on the {@link LoginUsersResponse}.
     * <p>
     * This class contains personal information about the user, such as first name, last name, and email.
     * </p>
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Personal {
      /**
       * The first name of the user.
       * @return The first name of the user.
       */
      String firstName;
      /**
       * The last name of the user.
       * @return The last name of the user.
       */
      String lastName;
      /**
       * The email address of the user.
       * @return The email address of the user.
       */
      String email;
      // Also includes addresses and
      // other informamtion not valuable to us for now
    }
    /**
     * The unique identifier for the user.
     * @return The unique identifier for the user.
     */
    String id;
    /**
     * The username of the user.
     * @return The username of the user.
     */
    String username;
    /**
     * The external system identifier for the user.
     * This is often used to link the user to an external system or service.
     * @return The external system identifier for the user.
     */
    String externalSystemId;
    /**
     * The barcode associated with the user.
     * This is typically used for identification purposes, such as in library systems.
     * @return The barcode associated with the user.
     */
    String barcode;
    /**
     * Indicates whether the user is currently active.
     * @return True if the user is active, false otherwise.
     */
    boolean active;
    /**
     * The ID of the patron group to which the user belongs.
     * This is used to categorize users into different groups for permissions and access control.
     * @return The ID of the patron group.
     */
    String patronGroup;

    /**
     * The {@link Personal} information object for the user.
     * This contains personal details such as first name, last name, and email.
     * @return The personal information of the user.
     */
    Personal personal;
    /**
     * The {@link Instant} for the created date of the user.
     * @return The date and time when the user was created.
     */
    Instant createdDate;
    /**
     * The {@link Instant} for the updated date of the user.
     * @return The date and time when the user was last updated.
     */
    Instant updatedDate;

    // We also have metaGetter, custom fields, preferred email communication and departments
  }
  /**
   * The {@link User} object returned by the FOLIO login endpoint.
   * This contains detailed information about the user, including personal details,
   * active status, patron group, and more.
   * @return The user object containing user details.
   */
  User user;

  /**
   * A FOLIO login PatronGroup object on the {@link LoginUsersResponse}.
   * <p>
   * This class contains information about the patron group, such as its ID, group, and description.
   * </p>
   */
  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PatronGroup {
    /**
     * The unique identifier for the patron group.
     * @return The unique identifier for the patron group.
     */
    String id;
    /**
     * The name of the patron group.
     * This is used to categorize users into different patron groups for permissions and access control.
     * @return The name of the patron group.
     */
    String group;
    /**
     * A description of the patron group.
     * This provides additional context or information about the patron group.
     * @return The description of the patron group.
     */
    String desc;
    // We also have metaGetter
  }
  /**
   * The {@link PatronGroup} object returned by the FOLIO login endpoint.
   * This contains information about the patron group to which the user belongs,
   * including its ID, name, and description.
   * @return The patron group object containing group details.
   */
  PatronGroup patronGroup;

  /**
   * A FOLIO login Permissions object on the {@link LoginUsersResponse}.
   * <p>
   * This class contains information about the permissions granted to the user
   * </p>
   */
  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Permissions {
    /**
     * The identifier for the permissions object
     * @return The permissions object identifier
     */
    String id;
    /**
     * The unique identifier for the user
     * @return The unique identifier for the user
     */
    String userId;
    /**
     * A list of FOLIO permissions granted to the user who performed the login
     * @return The list of String permissions
     */
    List<String> permissions;
    // We also have metaGetter
  }
  /**
   * The {@link Permissions} object returned by the FOLIO login endpoint.
   * This contains information about the permissions granted to a user
   * including the user identifier and the list of FOLIO permission keys
   * @return The permissions for the user at hand
   */
  Permissions permissions;

  /**
   * A FOLIO login ServicePointsUser object on the {@link LoginUsersResponse}.
   * <p>
   * This class contains information about the service points for the user
   * </p>
   */
  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)

  public static class ServicePointsUser {
    /**
     * The unique identifier for the ServicePointsUser object
     * @return The unique identifier for the ServicePointsUser object
     */
    String id;
    /**
     * The unique identifier for the login user
     * @return The unique identifier for the login user
     */
    String userId;
    /**
     * A list of service point identifiers for the user.
     * @return the list of service points for the user, in the form of a list of identifier strings
     */
    List<String> servicePointsIds;
    // Service points -- not sure what shape these take
    /**
     * The default service point identifier from the list
     * @return the String default service point identifier for the user
     */
    String defaultServicePointId;
    // We also have metaGetter
  }
  /**
   * The {@link ServicePointsUser} object returned by the FOLIO login endpoint.
   * This contains information about the service points for a user
   * including the user identifier and the list of FOLIO service point identifiers
   * @return The service point user object for the user at hand
   */
  ServicePointsUser servicePointsUser;

  /**
   * A {@link TokenExpirationResponse} object containing information about the tokens granted to the user as part of the successful login
   * @return The token expiration metaGetter object
   */
  TokenExpirationResponse tokenExpiration;

  /**
   * The tenant for the logged in user
   * @return the tenant name for the user
   */
  String tenant;
}

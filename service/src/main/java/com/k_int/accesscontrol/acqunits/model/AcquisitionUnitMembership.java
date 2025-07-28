package com.k_int.accesscontrol.acqunits.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

/**
 * Represents a membership of a user in an acquisition unit.
 * <p>
 * This class encapsulates the relationship between a user and an acquisition unit,
 * including the IDs of both the acquisition unit and the user.
 * </p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("javadoc")
public class AcquisitionUnitMembership {
  /**
   * Unique identifier for the acquisition unit.
   * This ID is used to reference the unit in access control policies and claims.
   * @return The acquisition unit identifier
   */
  String acquisitionsUnitId;

  /**
   * Unique identifier for the membership record.
   * This ID is used to reference the membership in access control policies and claims.
   * @return The identifier for the membership record
   */
  String id;
  /**
   * Unique identifier for the user who is a member of the acquisition unit.
   * This ID is used to reference the user in access control policies and claims.
   * @return the user identifier
   */
  String userId;
}

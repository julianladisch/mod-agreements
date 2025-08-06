package com.k_int.accesscontrol.acqunits.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;

/**
 * Represents an acquisition unit in the access control system.
 * <p>
 * This class encapsulates the properties of an acquisition unit, including its ID,
 * name, description, and various protection flags that determine the permissions
 * for creating, deleting, reading, and updating the unit.
 * </p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("javadoc")
@SuperBuilder
@NoArgsConstructor
public class AcquisitionUnit {
  /**
   * Unique identifier for the acquisition unit.
   * This ID is used to reference the unit in access control policies and claims.
   *
   * @param id the unique identifier for the acquisition unit
   * @return the unique identifier
   */
  String id;
  /**
   * Indicates whether the acquisition unit has been deleted.
   * This flag is used to filter out units that are no longer active. -- WIP at the minute
   *
   * @param isDeleted the deletion status of the acquisition unit
   * @return true if the unit is deleted, false otherwise
   */
  boolean isDeleted;
  /**
   * The name of the acquisition unit.
   * This is a human-readable identifier for the unit, used in user interfaces and reports.
   *
   * @param name the name of the acquisition unit
   * @return the name of the acquisition unit
   */
  String name;

  /**
   * A description of the acquisition unit.
   * This field provides additional context or information about the unit's purpose or contents.
   *
   * @param description the description of the acquisition unit
   * @return the description of the acquisition unit
   */
  @Nullable
  String description;

  /**
   * Flag indicating whether the acquisition unit restricts the assignment of this unit to a resource.
   *
   * @param protectCreate the protection flag for assigning this unit to resources
   * @return true if assignation is protected, false otherwise
   */
  boolean protectCreate;
  /**
   * Flag indicating whether the acquisition unit restricts the deletion of a resource.
   *
   * @param protectDelete the protection flag for deleting resources
   * @return true if deletion is protected, false otherwise
   */
  boolean protectDelete;
  /**
   * Flag indicating whether the acquisition unit restricts reading of the resource.
   *
   * @param protectRead the protection flag for reading resources
   * @return true if reading is protected, false otherwise
   */
  boolean protectRead;
  /**
   * Flag indicating whether the acquisition unit restricts updating of the resource.
   *
   * @param protectUpdate the protection flag for updating resources
    * @return true if updating is protected, false otherwise
   */
  boolean protectUpdate;
}

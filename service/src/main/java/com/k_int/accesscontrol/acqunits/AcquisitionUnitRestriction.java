package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.core.PolicyRestriction;
import lombok.Getter;

// For ACQ_UNITS
// read -> restrict read for rows
// CREATE -> restrict the ability to create FOR this acquisition unit ... sort of a "claim"
// UPDATE -> restrict the ability to update a row
// DELETE -> restrict the ability to delete a row

/**
 * Enum representing the internal acquisition unit flags (protection settings) used within FOLIO.
 * Each constant in this enum corresponds to a specific protection setting in FOLIO's acquisition unit
 * configuration (e.g., `protectRead`, `protectUpdate`).
 *
 * <p>This enum also provides a utility method to convert a high-level {@link PolicyRestriction}
 * into its corresponding {@code AcquisitionUnitRestriction}, specifically handling the unique
 * mapping logic for acquisition units where 'create' might refer to 'claiming' or associating.</p>
 */
@Getter
@SuppressWarnings("javadoc")
public enum AcquisitionUnitRestriction {
  /**
   * Corresponds to the `protectRead` flag in FOLIO acquisition unit settings,
   * restricting read access to resources within this acquisition unit.
   */
  READ("protectRead"),

  /**
   * Corresponds to the `protectCreate` flag in FOLIO acquisition unit settings.
   * In the context of {@link PolicyRestriction} mappings, this flag is used to
   * restrict the *claiming* or association of resources with an acquisition unit.
   * <p>Note: {@link PolicyRestriction#CLAIM} maps to this restriction.</p>
   */
  CREATE("protectCreate"),

  /**
   * Corresponds to the `protectUpdate` flag in FOLIO acquisition unit settings,
   * restricting modification access to resources within this acquisition unit.
   */
  UPDATE("protectUpdate"),

  /**
   * Corresponds to the `protectDelete` flag in FOLIO acquisition unit settings,
   * restricting deletion access to resources within this acquisition unit.
   */
  DELETE("protectDelete"),

  /**
   * Represents a state where no specific acquisition unit restriction is applied or found.
   * This is also the mapping for {@link PolicyRestriction#CREATE}, as creation is not
   * directly restricted by acquisition units themselves.
   */
  NONE("none");

  /**
   * The JSON key or accessor string used in FOLIO responses/configurations
   * to represent this acquisition unit restriction (e.g., "protectRead").
   * @return The string accessor for this acquisition unit restriction.
   */
  private final String restrictionAccessor;

  /**
   * Private constructor for {@code AcquisitionUnitRestriction} enum constants.
   * @param restrictionAccessor The string accessor that corresponds to this restriction in FOLIO.
   */
  AcquisitionUnitRestriction(String restrictionAccessor)
  {
    this.restrictionAccessor = restrictionAccessor;
  }

  /**
   * Converts a generic {@link PolicyRestriction} into its corresponding
   * {@code AcquisitionUnitRestriction}, adhering to the specific mapping rules for
   * FOLIO acquisition unit protection flags.
   *
   * <p>Specific mappings:</p>
   * <ul>
   * <li>{@link PolicyRestriction#READ} maps to {@link #READ}.</li>
   * <li>{@link PolicyRestriction#CLAIM} maps to {@link #CREATE} (as claiming/associating is seen as a 'create' operation for the acquisition unit link).</li>
   * <li>{@link PolicyRestriction#CREATE} maps to {@link #NONE} (as acquisition units do not directly restrict the act of creating a new record, but rather its association).</li>
   * <li>{@link PolicyRestriction#UPDATE} maps to {@link #UPDATE}.</li>
   * <li>{@link PolicyRestriction#APPLY_POLICIES} maps to {@link #UPDATE} (as acquisition units are usually set in FOLIO on a field on the resource itself, and so protected by UPDATE)</li>
   * <li>{@link PolicyRestriction#DELETE} maps to {@link #DELETE}.</li>
   * </ul>
   *
   * @param pr The {@link PolicyRestriction} to convert.
   * @return The corresponding {@code AcquisitionUnitRestriction}, or {@link #NONE} if
   * the provided {@code PolicyRestriction} does not have a direct or defined mapping.
   */
  public static AcquisitionUnitRestriction getRestrictionFromPolicyRestriction(PolicyRestriction pr) {
    return switch (pr) {
      case READ -> AcquisitionUnitRestriction.READ;
      case CLAIM -> AcquisitionUnitRestriction.CREATE;
      case DELETE -> AcquisitionUnitRestriction.DELETE;
      case UPDATE, APPLY_POLICIES -> AcquisitionUnitRestriction.UPDATE;
      default -> AcquisitionUnitRestriction.NONE;
    };
  }
}

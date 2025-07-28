package com.k_int.accesscontrol.core;

/**
 * Enumeration of access policy types.
 * These represent different mechanisms of access control, such as:
 * - `ACQ_UNIT`: FOLIO acquisition units
 * - `KI_GRANT`: Reserved for custom grant-based access control (planned)
 */
public enum AccessPolicyType {
  /**
   * Represents access policies based on FOLIO acquisition units.
   * This type is used to manage access control through acquisition units in the FOLIO system.
   */
  ACQ_UNIT,
  /**
   * Reserved for future use, specifically for custom grant-based access control.
   * This type is planned to be implemented in the future to handle access control through grants.
   */
  KI_GRANT
}

package com.k_int.accesscontrol.acqunits.useracquisitionunits;

/**
 * Enum representing subsets of user acquisition units based on membership and restriction status.
 * <p>
 * This enum is used to categorize user acquisition units into different subsets based on whether the user
 * is a member of the unit and whether the unit is restrictive or non-restrictive.
 * </p>
 */
public enum UserAcquisitionsUnitSubset {
  /**
   * Represents acquisition units that restrict access and the user is a member of those units.
   * These units allow access for the user.
   */
  MEMBER_RESTRICTIVE,
  /**
   * Represents acquisition units that restrict access and the user is not a member of those units.
   * These units deny access for the user.
   */
  NON_MEMBER_RESTRICTIVE,
  /**
   * Represents acquisition units that do not restrict access.
   * These units allow access for the user regardless of membership.
   * Usually, this is enough to provide access to the user without checking membership.
   */
  NON_RESTRICTIVE, // Collection of member AND non-member -- usually this is enough
  /**
   * Represents non-restrictive acquisition units for which the user is a member.
   * These units allow access for the user regardless of membership.
   * Usually this is not needed, as non-restrictive units are already accessible to all users.
   */
  MEMBER_NON_RESTRICTIVE,
  /**
   * Represents non-restrictive acquisition units for which the user is not a member.
   * These units allow access for the user regardless of membership.
   * Usually this is not needed, as non-restrictive units are already accessible to all users.
   */
  NON_MEMBER_NON_RESTRICTIVE
}
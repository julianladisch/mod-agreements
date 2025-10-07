package com.k_int.accesscontrol.acqunits.useracquisitionunits;

import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitPolicy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents acquisition units associated with a user, grouped by restriction type.
 * <p>
 * Used to determine access control logic based on whether the user is a member of
 * restrictive acquisition units.
 * </p>
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class UserAcquisitionUnits {
  /**
   * Acquisition units that restrict access, but the user is explicitly listed as a member.
   * These units allow access for the user.
   * @param memberRestrictiveUnits Acquisition units that restrict access, but the user is explicitly listed as a member
   * @return Acquisition units that restrict access, but the user is explicitly listed as a member
   */
  List<AcquisitionUnit> memberRestrictiveUnits;

  /**
   * Acquisition units that restrict access and the user is not a member.
   * These units deny access for the user.
   * @param nonMemberRestrictiveUnits Acquisition units that restrict access and the user is not a member
   * @return Acquisition units that restrict access and the user is not a member
   */
  List<AcquisitionUnit> nonMemberRestrictiveUnits;

  /**
   * Acquisition units that do not restrict access.
   * These units allow access for the user regardless of membership.
   * @param nonRestrictiveUnits Acquisition units that do not restrict access
   * @return Acquisition units that do not restrict access
   */
  List<AcquisitionUnit> nonRestrictiveUnits;

  /**
   * Acquisition units that do not restrict access with the user explicitly listed as a member
   * These units allow access for the user regardless of membership.
   * @param memberNonRestrictiveUnits Acquisition units that do not restrict access with the user explicitly listed as a member
   * @return Acquisition units that do not restrict access with the user explicitly listed as a member
   */
  List<AcquisitionUnit> memberNonRestrictiveUnits;

  /**
   * Acquisition units that do not restrict access and the user is not a member
   * These units allow access for the user regardless of membership.
   * @param nonMemberNonRestrictiveUnits Acquisition units that do not restrict access and the user is not a member
   * @return Acquisition units that do not restrict access and the user is not a member
   */
  List<AcquisitionUnit> nonMemberNonRestrictiveUnits;

  /**
   * Metadata about the fetched acquisition units, indicating which subsets are present.
   * @param userAcquisitionUnitsMetadata Metadata about the user acquisition units
   * @return Metadata about the user acquisition units
   */
  UserAcquisitionUnitsMetadata userAcquisitionUnitsMetadata;

  /**
   * Returns a list of IDs for acquisition units that restrict access and the user is a member.
   *
   * @return List of member restrictive unit IDs
   */
  public List<String> getMemberRestrictiveUnitIds() {
    return memberRestrictiveUnits.stream()
            .map(AcquisitionUnit::getId)
            .toList();
  }

  /**
   * Returns a list of IDs for acquisition units that restrict access and the user is not a member.
   *
   * @return List of non-member restrictive unit IDs
   */
  public List<String> getNonMemberRestrictiveUnitIds() {
    return nonMemberRestrictiveUnits.stream()
            .map(AcquisitionUnit::getId)
            .toList();
  }

  /**
   * Returns a list of IDs for acquisition units that do not restrict access.
   *
   * @return List of non-restrictive unit IDs
   */
  public List<String> getNonRestrictiveUnitIds() {
    return nonRestrictiveUnits.stream()
            .map(AcquisitionUnit::getId)
            .toList();
  }


  /**
   * Returns a list of {@link AcquisitionUnitPolicy} objects for acquisition units that restrict access and the user is a member.
   *
   * @return List of restrictive units for which the user is a member
   */
  public List<AcquisitionUnitPolicy> getMemberRestrictiveUnitPolicies() {
    return memberRestrictiveUnits.stream()
      .map(mru -> AcquisitionUnitPolicy.fromAcquisitionUnit(mru, true)).toList();
  }

  /**
   * Returns a list of {@link AcquisitionUnitPolicy} objects for acquisition units that restrict access and the user is not a member.
   *
   * @return List of restrictive units for which the user is not a member
   */
  public List<AcquisitionUnitPolicy> getNonMemberRestrictiveUnitPolicies() {
    return nonMemberRestrictiveUnits.stream()
      .map(mru -> AcquisitionUnitPolicy.fromAcquisitionUnit(mru, false)).toList();
  }

  /**
   * Returns a list of {@link AcquisitionUnitPolicy} objects for acquisition units that do not restrict access.
   *
   * @return List of non-restrictive units
   */
  public List<AcquisitionUnitPolicy> getNonRestrictiveUnitPolicies() {
    return nonMemberRestrictiveUnits.stream()
      .map(mru -> AcquisitionUnitPolicy.fromAcquisitionUnit(mru, null)).toList();
  }

  /**
   * Returns a list of {@link AcquisitionUnitPolicy} objects for acquisition units that do not restrict access and the user is not a member.
   *
   * @return List of non-restrictive units for which the user is not a member
   */
  public List<AcquisitionUnitPolicy> getNonMemberNonRestrictiveUnitPolicies() {
    return nonMemberNonRestrictiveUnits.stream()
      .map(mru -> AcquisitionUnitPolicy.fromAcquisitionUnit(mru, false)).toList();
  }

  /**
   * Returns a list of {@link AcquisitionUnitPolicy} objects for acquisition units that do not restrict access and the user is a member.
   *
   * @return List of non-restrictive units for which the user is a member
   */
  public List<AcquisitionUnitPolicy> getMemberNonRestrictiveUnitPolicies() {
    return memberNonRestrictiveUnits.stream()
      .map(mru -> AcquisitionUnitPolicy.fromAcquisitionUnit(mru, true)).toList();

  }
}

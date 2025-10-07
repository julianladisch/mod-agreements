package com.k_int.accesscontrol.acqunits.useracquisitionunits;

import lombok.Data;

import java.util.Set;

/**
 * Metadata class for user acquisition unit fetching, encapsulating various subsets of user acquisition units.
 * This class provides a way to check what was fetched for a given UserAcquisitionUnit object, and therefore the
 * information one can expect to find within
 */
@Data
@SuppressWarnings("javadoc")
public class UserAcquisitionUnitsMetadata {
  /**
   * Indicates whether the user acquisition units include restrictive units for which the user is a member.
   * @param memberRestrictive true if the user acquisition units include restrictive units for which the user is a member, false otherwise
   * @return true if the user acquisition units include restrictive units for which the user is a member, false otherwise
   */
  private final boolean memberRestrictive;
  /**
   * Indicates whether the user acquisition units include restrictive units for which the user is not a member.
   * @param nonMemberRestrictive true if the user acquisition units include restrictive units for which the user is not a member, false otherwise
   * @return true if the user acquisition units include restrictive units for which the user is not a member, false otherwise
   */
  private final boolean nonMemberRestrictive;
  /**
   * Indicates whether the user acquisition units include non-restrictive units.
   * @param nonRestrictive true if the user acquisition units include non-restrictive units, false otherwise
   * @return true if the user acquisition units include non-restrictive units, false otherwise
   */
  private final boolean nonRestrictive;

  /**
   * Indicates whether the user acquisition units include non-restrictive units for which the user is a member.
   * @param memberNonRestrictive true if the user acquisition units include non-restrictive units for which the user is a member, false otherwise
   * @return true if the user acquisition units include non-restrictive units for which the user is a member, false otherwise
   */
  private final boolean memberNonRestrictive;
  /**
   * Indicates whether the user acquisition units include non-restrictive units for which the user is not a member.
   * @param nonMemberNonRestrictive true if the user acquisition units include non-restrictive units for which the user is not a member, false otherwise
   * @return true if the user acquisition units include non-restrictive units for which the user is not a member, false otherwise
   */
  private final boolean nonMemberNonRestrictive;

  /**
   * Constructs a UserAcquisitionUnitsMetadata object based on the provided set of {@link UserAcquisitionsUnitSubset}.
   * This constructor initializes the metadata fields based on the presence of specific subsets in the provided set.
   *
   * @param subsetSets a set of UserAcquisitionsUnitSubset that indicates which subsets are included
   */
  public UserAcquisitionUnitsMetadata(Set<UserAcquisitionsUnitSubset> subsetSets) {
    this.memberRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE);
    this.nonMemberRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.NON_MEMBER_RESTRICTIVE);
    this.nonRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.NON_RESTRICTIVE);
    this.memberNonRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.MEMBER_NON_RESTRICTIVE);
    this.nonMemberNonRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.NON_MEMBER_NON_RESTRICTIVE);
  }
}


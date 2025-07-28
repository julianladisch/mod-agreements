package com.k_int.accesscontrol.acqunits.useracquisitionunits;

import lombok.Data;

import java.util.Set;

@Data
public class UserAcquisitionUnitsMetadata {
  private final boolean memberRestrictive;
  private final boolean nonMemberRestrictive;
  private final boolean nonRestrictive;

  public UserAcquisitionUnitsMetadata(Set<UserAcquisitionsUnitSubset> subsetSets) {
    this.memberRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE);
    this.nonMemberRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.NON_MEMBER_RESTRICTIVE);
    this.nonRestrictive = subsetSets.contains(UserAcquisitionsUnitSubset.NON_RESTRICTIVE);
  }
}


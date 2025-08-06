package com.k_int.accesscontrol.acqunits.useracquisitionunits;

public enum UserAcquisitionsUnitSubset {
  MEMBER_RESTRICTIVE,
  NON_MEMBER_RESTRICTIVE,
  NON_RESTRICTIVE, // Collection of member AND non-member -- usually this is enough
  MEMBER_NON_RESTRICTIVE,
  NON_MEMBER_NON_RESTRICTIVE
}
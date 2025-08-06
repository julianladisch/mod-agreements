package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.core.http.responses.Policy;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * A response containing all AcquisitionUnit information, in addition to whether the user in
 * question is or is not a member for that unit
 */
@SuperBuilder
@JsonTypeName("ACQ_UNITS")
@SuppressWarnings("javadoc")
public class AcquisitionUnitPolicy extends AcquisitionUnit implements Policy {

  // Should contain all the useful information on an AcquisitionUnit AND whether the user is a member
  /**
   * A boolean representing whether the user is a member of the acquistion unit
   * @return A boolean representing whether the user is a member of the acquistion unit
   */
  @Getter
  Boolean isMember;

  /**
   * Creates an {@link AcquisitionUnitPolicy} instance from a base {@link AcquisitionUnit}
   * and a membership flag.
   *
   * <p>This method copies all relevant fields from the given {@code unitBase} and sets
   * the membership status indicating whether the user is a member of this acquisition unit.</p>
   *
   * @param unitBase the base acquisition unit to copy fields from
   * @param isMember a boolean indicating if the user is a member of this acquisition unit
   * @return a new {@link AcquisitionUnitPolicy} instance containing the data and membership info
   */
  public static AcquisitionUnitPolicy fromAcquisitionUnit(AcquisitionUnit unitBase, Boolean isMember) {
    return AcquisitionUnitPolicy
      .builder()
      .isMember(isMember)
      // Now the underlying AcqUnit fields
      .id(unitBase.getId())
      .name(unitBase.getName())
      .description(unitBase.getDescription())
      .isDeleted(unitBase.isDeleted())
      .protectCreate(unitBase.isProtectCreate())
      .protectRead(unitBase.isProtectRead())
      .protectUpdate(unitBase.isProtectUpdate())
      .protectDelete(unitBase.isProtectDelete())
      .build();
  }
}

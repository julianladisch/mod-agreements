package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.k_int.accesscontrol.acqunits.model.AcquisitionUnitMembership;
import lombok.Data;

import java.util.List;

/**
 * Represents the response containing a list of acquisition unit memberships.
 * <p>
 * This class encapsulates the response structure for acquisition unit memberships,
 * including a list of memberships and the total number of records.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("javadoc")
public class AcquisitionUnitMembershipResponse {
  /**
   * List of acquisition unit memberships.
   * Each membership links a user to an acquisition unit.
   * @param acquisitionsUnitMemberships The list of {@link AcquisitionUnitMembership} records
   * @return The list of {@link AcquisitionUnitMembership} records
   */
  List<AcquisitionUnitMembership> acquisitionsUnitMemberships;
  /**
   * Total number of acquisition unit membership records.
   * This is used for pagination and to inform the client about the total available memberships.
   *
   * @param totalRecords the total number of records in the response
   * @return the total number of records in the response
   */
  int totalRecords;
}


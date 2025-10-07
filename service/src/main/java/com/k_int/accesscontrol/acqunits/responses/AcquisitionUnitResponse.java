package com.k_int.accesscontrol.acqunits.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import lombok.Data;

import java.util.List;

/**
 * Represents the response containing a list of acquisition units.
 * <p>
 * This class encapsulates the response structure for acquisition units,
 * including a list of acquisition units and the total number of records.
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("javadoc")
public class AcquisitionUnitResponse {
  /**
   * List of acquisition units.
   * Each unit represents a distinct acquisition unit in the access control system.
   * @param acquisitionsUnits List of acquisition units
   * @return List of acquisition units
   */
  List<AcquisitionUnit> acquisitionsUnits;
  /**
   * Total number of acquisition unit records.
   * This is used for pagination and to inform the client about the total available units.
   * @param totalRecords Total number of acquisition unit records
   * @return Total number of acquisition unit records
   */
  int totalRecords;
}


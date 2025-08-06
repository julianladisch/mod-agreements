package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.AcquisitionUnitPolicyEngineConfiguration;
import lombok.Builder;
import lombok.Data;

/**
 * Configuration for the Policy Engine, which includes settings for acquisition unit policies.
 * This class encapsulates the configuration details required to initialize and run the policy engine.
 */
@Builder
@Data
@SuppressWarnings("javadoc")
public class PolicyEngineConfiguration {
  /**
   * Configuration for the acquisition unit policy engine.
   * This determines whether acquisition unit-based access restrictions are enabled and how they are evaluated.
   *
   * @param acquisitionUnitPolicyEngineConfiguration the {@link AcquisitionUnitPolicyEngineConfiguration} associated with this policy engine
   * @return the {@link AcquisitionUnitPolicyEngineConfiguration} associated with this policy engine
   */
  AcquisitionUnitPolicyEngineConfiguration acquisitionUnitPolicyEngineConfiguration;

  // Configuration for KI Grants, if applicable. This is an EXTENSION for later, not currently implemented.
  // KIGrantsPolicyEngineConfiguration kiGrantsPolicyEngineConfiguration;
}

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
public class PolicyEngineConfiguration {
  AcquisitionUnitPolicyEngineConfiguration acquisitionUnitPolicyEngineConfiguration;

  // Configuration for KI Grants, if applicable. This is an EXTENSION for later, not currently implemented.
  // KIGrantsPolicyEngineConfiguration kiGrantsPolicyEngineConfiguration;
}

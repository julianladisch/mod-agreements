package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.core.policyengine.PolicyEngineImplementorConfiguration;
import com.k_int.folio.FolioClientConfig;
import lombok.Builder;
import lombok.Data;

/**
 * Configuration for the Acquisition Unit Policy Engine.
 * This class encapsulates the settings required to initialize and run the acquisition unit policy engine,
 * including whether it is enabled, the FOLIO client configuration, and external login requirements.
 */
@Data
@Builder
public class AcquisitionUnitPolicyEngineConfiguration implements PolicyEngineImplementorConfiguration {
  /**
   * Indicates whether the acquisition unit policy engine is enabled.
   * If true, the policy engine will process acquisition unit policies.
   * If false, it will not process any acquisition unit policies.
   */
  boolean enabled;

  /**
   * Configuration for the FOLIO client used by the acquisition unit policy engine
   * Includes settings for authentication, API endpoints, and other necessary parameters
   */
  FolioClientConfig folioClientConfig; // Configuration for the FOLIO client, including authentication and API settings

  /**
   * Indicates whether an external FOLIO login is required.
   * If true, the policy engine will perform a login to the external FOLIO client before processing policies.
   * If false, it will assume that the client is already authenticated.
   */
  boolean externalFolioLogin; // When configured for an EXTERNAL folio client, ensure we perform a login first.
}

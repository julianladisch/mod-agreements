package com.k_int.accesscontrol.core;

/**
 * An interface representing some Policy coming from an external system, such as an acquisition unit object.
 * It MUST have some field representing the unique identifier for that policy, which is what this interface exposes,
 * and implementations of this interface may then extend to include other fields as needed, which can be "enriched" through
 * the {@link com.k_int.accesscontrol.main.PolicyEngine}
 */
public interface ExternalPolicy {
  /**
   * A String field that MUST be present to identify the policy.
   * For most policies (Such as acquisition units) this will be an identifier. In some cases (KIGrants)
   * this may instead be a string such as "GBV%"
   * @return The identification String for this Policy
   */
  String getId();
}

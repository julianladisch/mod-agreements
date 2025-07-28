package com.k_int.accesscontrol.core.http.bodies;

import com.k_int.accesscontrol.core.AccessPolicyType;

/**
 * Represents a policy claim in the context of access control.
 * <p>
 * This interface defines the structure for a policy claim, which includes
 * an identifier, an associated policy ID, the type of access policy, and a description.
 * Policy claims are fundamental units for asserting access rights or restrictions
 * based on defined policies.
 * </p>
 */
public interface PolicyClaim {

  /**
   * Retrieves the unique identifier of this policy claim.
   *
   * @return The unique ID of the policy claim.
   */
  String getId();

  /**
   * Sets the unique identifier for this policy claim.
   *
   * @param id The unique ID to set for the policy claim.
   */
  void setId(String id);

  /**
   * Retrieves the identifier of the policy to which this claim pertains.
   * This links the claim to a specific access policy definition.
   *
   * @return The ID of the associated policy.
   */
  String getPolicyId();

  /**
   * Sets the identifier of the policy to which this claim pertains.
   *
   * @param policyId The ID of the policy to associate with this claim.
   */
  void setPolicyId(String policyId);

  /**
   * Retrieves the type of access policy that this claim represents.
   * This type helps categorize or interpret the nature of the access control.
   *
   * @return The {@link AccessPolicyType} of this claim.
   */
  AccessPolicyType getType();

  /**
   * Sets the type of access policy that this claim represents.
   *
   * @param type The {@link AccessPolicyType} to set for this claim.
   */
  void setType(AccessPolicyType type);

  /**
   * Retrieves a human-readable description of this policy claim.
   * This can provide context or details about the claim's purpose.
   *
   * @return A descriptive string for the policy claim.
   */
  String getDescription();

  /**
   * Sets a human-readable description for this policy claim.
   *
   * @param description The description to set for the policy claim.
   */
  void setDescription(String description);
}
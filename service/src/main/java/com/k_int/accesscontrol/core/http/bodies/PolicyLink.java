package com.k_int.accesscontrol.core.http.bodies;

import com.k_int.accesscontrol.core.AccessPolicyType;
import com.k_int.accesscontrol.core.http.responses.Policy;

/**
 * Represents a policy link in the context of access control.
 * <p>
 * This interface defines the structure for a policy link, which includes
 * an identifier (representing the join to a resource), an associated policy ID, the type of access policy, and a description.
 * Policy links are fundamental units for asserting access rights or restrictions
 * based on defined policies.
 * </p>
 */
public interface PolicyLink {

 /**
   * Retrieves the unique identifier of this policy link.
   *
   * @return The unique ID of the policy link.
   */
  String getId();

  /**
   * Sets the unique identifier for this policy link.
   *
   * @param id The unique ID to set for the policy link.
   */
  void setId(String id);

  /**
   * Retrieves the policy to which this link pertains.
   * This links the PolicyLink to a specific access policy definition.
   *
   * @return The associated policy.
   */
  Policy getPolicy();

  /**
   * Sets the policy to which this link pertains.
   *
   * @param policy The policy to associate with this link.
   */
  void setPolicy(Policy policy);

  /**
   * Retrieves the type of access policy that this link represents.
   * This type helps categorize or interpret the nature of the access control.
   *
   * @return The {@link AccessPolicyType} of this link.
   */
  AccessPolicyType getType();

  /**
   * Sets the type of access policy that this link represents.
   *
   * @param type The {@link AccessPolicyType} to set for this link.
   */
  void setType(AccessPolicyType type);

  /**
   * Retrieves a human-readable description of this policy link.
   * This can provide context or details about the link's purpose.
   *
   * @return A descriptive string for the policy link.
   */
  String getDescription();

  /**
   * Sets a human-readable description for this policy link.
   *
   * @param description The description to set for the policy link.
   */
  void setDescription(String description);
}
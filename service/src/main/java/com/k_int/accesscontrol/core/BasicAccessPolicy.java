package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Basic implementation of the AccessPolicy interface.
 * <p>
 * This class provides a simple data structure to represent an access policy rule,
 * linking a specific policy ID to a target resource class and ID, and typed according to a known AccessPolicyType.
 * </p>
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class BasicAccessPolicy implements DomainAccessPolicy {
  /** The unique identifier for the access policy in the database.
   * This is typically a UUID or similar unique string.
   * @param id The unique identifier of the access policy.
   * @return The unique identifier of the access policy.
   */
  String id;

  /** The description of the access policy.
   * This provides additional context or information about the policy.
   * @param description The description of the access policy.
   * @return The description of the access policy.
   */
  String description;
  /** The date and time when the access policy was created.
   * This is represented as an {@link Instant}.
   * @param dateCreated The creation date of the access policy.
   * @return The creation date of the access policy.
   */
  Instant dateCreated;
  /** The date and time when the access policy was last updated.
   * This is represented as an {@link Instant}.
   * @param dateUpdated The last updated date of the access policy.
   * @return The last updated date of the access policy.
   */
  Instant dateUpdated;
  /** The type of access policy (e.g., ACQ_UNIT).
   * This indicates the category or classification of the policy.
   * @param type The type of access policy.
   * @return The type of access policy.
   */
  AccessPolicyType type;
  /** The specific policy ID that this access policy refers to.
   * This ID corresponds to a record in the system that defines the actual access permissions.
   * @param policyId The specific policy ID.
   * @return The specific policy ID.
   */
  String policyId;
  /** The class of the resource that this access policy applies to.
   * @param resourceClass The class of the resource.
   * @return The class of the resource.
   */
  String resourceClass;
  /** The identifier of the specific resource that this access policy applies to.
   * This can be a UUID or similar unique string.
   * @param resourceId The identifier of the specific resource.
   * @return The identifier of the specific resource.
   */
  String resourceId;
}

package com.k_int.accesscontrol.core;

import java.time.Instant;

/**
 * Interface representing an access policy rule in the implementing system's database.
 * An access policy links a specific policy ID to a target resource class and ID,
 * and is typed according to a known `AccessPolicyType` (e.g., ACQ_UNIT).
 * Each implementation (Grails, micronaut, etc) will need to provide its own "entity" class implementing this interface.
 */
public interface DomainAccessPolicy {
  /**
   * Gets the unique identifier for the access policy in the database.
   * This is typically a UUID or similar unique string.
   *
   * @return the unique identifier of the access policy
   */
  String getId();
  /**
   * Sets the unique identifier for the access policy in the database.
   * This is typically a UUID or similar unique string.
   *
   * @param id the unique identifier to set
   */
  void setId(String id);

  // ------- Access Policy Metadata -------

  /**
   * Gets the description of the access policy.
   * This provides additional context or information about the policy.
   *
   * @return the description of the access policy
   */
  String getDescription();
  /**
   * Sets the description of the access policy.
   * This provides additional context or information about the policy.
   *
   * @param description the description to set
   */
  void setDescription(String description);

  /**
   * Gets the date and time when the access policy was created.
   * This is typically represented as an Instant.
   *
   * @return the creation date of the access policy
   */
  Instant getDateCreated();
  /**
   * Sets the date and time when the access policy was created.
   * This is typically represented as an Instant.
   *
   * @param dateCreated the creation date to set
   */
  void setDateCreated(Instant dateCreated);


  /**
   * Gets the type of the access policy
   * This is typically an enum value representing the category of the policy (e.g., ACQ_UNIT).
   *
   * @return the type of the access policy
   */
  AccessPolicyType getType();
  /**
   * Sets the type of the access policy.
   * This is typically an enum value representing the category of the policy (e.g., ACQ_UNIT).
   *
   * @param type the type of the access policy to set
   */
  void setType(AccessPolicyType type);

  /**
   * Gets the unique identifier for the policy held by an external system.
   * This is typically a UUID or similar unique string that identifies the policy.
   *
   * @return the policy ID
   */
  String getPolicyId();
  /**
   * Sets the unique identifier for the policy held by an external system.
   * This is typically a UUID or similar unique string that identifies the policy.
   *
   * @param policyId the policy ID to set
   */
  void setPolicyId(String policyId);

  // ------- Resource Metadata -------
  /**
   * Gets the class of the resource that this policy applies to.
   * This is typically a string representing the type of resource (e.g., "org.olf.erm.SubscriptionAgreement").
   *
   * @return the resource class
   */
  String getResourceClass();
  /**
   * Sets the class of the resource that this policy applies to.
   * This is typically a string representing the type of resource (e.g., "org.olf.erm.SubscriptionAgreement").
   *
   * @param resourceClass the resource class to set
   */
  void setResourceClass(String resourceClass);

  /**
   * Gets the unique identifier for the resource that this policy applies to.
   * This is typically a UUID or similar unique string that identifies the resource.
   *
   * @return the resource ID
   */
  String getResourceId();
  /**
   * Sets the unique identifier for the resource that this policy applies to.
   * This is typically a UUID or similar unique string that identifies the resource.
   *
   * @param resourceId the resource ID to set
   */
  void setResourceId(String resourceId);
}

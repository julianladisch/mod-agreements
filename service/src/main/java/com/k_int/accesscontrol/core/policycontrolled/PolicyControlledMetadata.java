package com.k_int.accesscontrol.core.policycontrolled;

import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Represents the metadata extracted from a class annotated with {@link PolicyControlled},
 * detailing how a resource is controlled by policies, including its own identifiers and
 * its relationship to an owner in an ownership hierarchy.
 * This class is used internally by the access control system to build and navigate
 * ownership chains for policy evaluation.
 * This object is immutable and all fields are set upon construction
 */
@Getter
@Builder
@SuppressWarnings("javadoc")
public class PolicyControlledMetadata {
  /**
   * The fully qualified class name of the resource entity (e.g., "org.olf.erm.SubscriptionAgreement").
   * @param resourceClassName The resource class name
   * @return The resource class name
   */
  final String resourceClassName;
  /**
   * The name of the database column that stores the unique identifier for this resource.
   * This is typically the primary key column name.
   * @param resourceIdColumn the name of the database column for the resource identifier field
   * @return the name of the database column for the resource identifier field
   */
  final String resourceIdColumn;
  /**
   * The name of the field (property) in the resource class that represents its unique identifier.
   * (e.g., "id"). This can be used in HQL/Criteria queries.
   * @param resourceIdField the resource identifier field name
   * @return the resource identifier field name
   */
  final String resourceIdField;

  // Ownership metadata
  /**
   * The name of the database column that stores the ID of this resource's owner.
   * This column exists in the current resource's table.
   * @param ownerColumn the name of the database column for the owner field
   * @return the name of the database column for the owner field
   */
  final String ownerColumn;
  /**
   * The name of the field (property) in the resource class that represents the association
   * to its owner entity (e.g., "owner" or "subscriptionOwner").
   * This can be used for HQL/Criteria joins.
   * @param ownerField the owner field name
   * @return the owner field name
   */
  final String ownerField;
  /**
   * The {@link Class} object of the owner entity.
   * @param ownerClass the class name of the owner
   * @return the class name of the owner
   */
  final Class<?> ownerClass;
  /**
   * Tracks how "far up" the hierarchy this {@code PolicyControlledMetadata} instance is. <br/>
   * -1: Represents the "base class" or leaf resource. <br/>
   * 0: Represents the direct owner of the base class. <br/>
   * 1: Represents the owner of the owner of the base class, and so on.
   * @param ownerLevel the level in the heirarchy for this policy controlled object
   * @return the level in the heirarchy for this policy controlled object
   */
  @Builder.Default
  final int ownerLevel = -1;

  // Owner alias fields
  /**
   * An alias name generated for this owner level when building dynamic queries
   * involving joins (e.g., "owner_alias_0"). This is {@code null} for the leaf class.
   * @param aliasName the sql alias name
   * @return the sql alias name
   */
  @Nullable
  final String aliasName;
  /**
   * The full path in terms of SQL column names (including previous aliases) to reference
   * this owner from the preceding level in the ownership chain (e.g., "owner_alias_0.owner_id").
   * This is used for constructing join conditions with specific column names.
   * This is {@code null} for the leaf class.
   * @param aliasOwnerColumn the sql alias owner column
   * @return the sql alias owner column
   */
  @Nullable
  final String aliasOwnerColumn;
  /**
   * The full path in terms of entity field names (including previous aliases) to reference
   * this owner from the preceding level in the ownership chain (e.g., "owner_alias_0.owner").
   * This is used for constructing entity-level joins in HQL/Criteria.
   * This is {@code null} for the leaf class.
   * @param aliasOwnerField the sql alias owner field
   * @return the sql alias owner field
   */
  @Nullable
  final String aliasOwnerField;
}

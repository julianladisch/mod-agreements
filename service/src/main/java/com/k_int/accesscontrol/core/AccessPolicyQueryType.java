package com.k_int.accesscontrol.core;

/**
 * Enum representing the types of queries that can be performed on access policies.
 * This is used to differentiate between queries that list multiple records
 * and those that check a single record against policy restrictions.
 */
public enum AccessPolicyQueryType {
  /**
   * Represents a query that retrieves a list of records filtered by a specific policy restriction.
   * This corresponds to the `PolicySubquery` used to filter all records by `PolicyRestriction`.
   */
  LIST,
  /**
   * Represents a query that checks if a specific policy restriction applies to a single record.
   * This corresponds to the `PolicySubquery` used to determine if `PolicyRestriction` applies to a SINGLE record.
   */
  SINGLE
}

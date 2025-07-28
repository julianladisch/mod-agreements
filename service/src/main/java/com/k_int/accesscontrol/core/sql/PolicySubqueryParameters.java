package com.k_int.accesscontrol.core.sql;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/**
 * Parameters for generating SQL subqueries based on access policy restrictions.
 * <p>
 * Encapsulates the necessary parameters to create a subquery that filters
 * records according to access policies, including details about the policy table,
 * resource class, and specific resource IDs.
 * </p>
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class PolicySubqueryParameters {

  /**
   * Name of the access policy table.
   * @param accessPolicyTableName the table name containing access policies
   * @return the table name containing access policies
   */
  String accessPolicyTableName;

  /**
   * Column name for the policy type.
   * @param accessPolicyTypeColumnName the column name indicating the type of policy
   * @return the column name indicating the type of policy
   */
  String accessPolicyTypeColumnName;

  /**
   * Column name for the policy ID.
   * @param accessPolicyIdColumnName the column name containing the policy's unique identifier
   * @return the column name containing the policy's unique identifier
   */
  String accessPolicyIdColumnName;

  /**
   * Column name for the resource ID in the policy table.
   * @param accessPolicyResourceIdColumnName the column name referencing the resource ID in the policy table
   * @return the column name referencing the resource ID in the policy table
   */
  String accessPolicyResourceIdColumnName;

  /**
   * Column name for the resource class in the policy table.
   * @param accessPolicyResourceClassColumnName the column name referencing the resource class/type in the policy table
   * @return the column name referencing the resource class/type in the policy table
   */
  String accessPolicyResourceClassColumnName;

  /**
   * The class/type of the resource being filtered.
   * @param resourceClass the resource class value to filter on
   * @return the resource class value to filter on
   */
  String resourceClass;

  /**
   * SQL alias for the resource table.
   * @param resourceAlias the alias used for the resource table in SQL queries
   * @return the alias used for the resource table in SQL queries
   */
  String resourceAlias;

  /**
   * Column name for the resource ID in the resource table.
   * @param resourceIdColumnName the column name containing the resource's unique identifier
   * @return the column name containing the resource's unique identifier
   */
  String resourceIdColumnName;

  /**
   * Optional specific resource ID to match.
   * @param resourceId the resource ID to filter on, or {@code null} if not specified
   * @return the resource ID to filter on, or {@code null} if not specified
   */
  @Nullable
  String resourceId;
}
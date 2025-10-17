package com.k_int.accesscontrol.core.policyengine;

import com.k_int.accesscontrol.core.AccessPolicyQueryType;
import com.k_int.accesscontrol.core.GroupedExternalPolicies;
import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.sql.PolicySubquery;

import java.util.List;

/**
 * Interface for implementing policy engine logic.
 * <p>
 * This interface defines methods for generating policy subqueries based on
 * access policy restrictions, allowing for flexible integration with different
 * policy engines.
 * </p>
 */
public interface PolicyEngineImplementor {
  /**
   * Generates policy subqueries for the given policy restriction and query type.
   *
   * @param headers   the request context headers, used for FOLIO/internal service authentication
   * @param pr        the policy restriction to filter by
   * @param queryType the type of query to generate (SINGLE or LIST)
   * @return a list of {@link PolicySubquery} objects for the given restriction and query type
   * @throws PolicyEngineException if an error occurs while generating policy subqueries
   */
  List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType);

  /**
   * Retrieves a list of access policy IDs grouped by their type for the given policy restriction.
   *
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @param pr      the policy restriction to filter by
   * @return a list of {@link GroupedExternalPolicies} containing policy IDs grouped by type
   */
  List<GroupedExternalPolicies> getRestrictionPolicies(String[] headers, PolicyRestriction pr);

  /**
   * Validates the policy IDs against the provided headers and policy restriction.
   *
   * @param headers   the request context headers, used for FOLIO/internal service authentication
   * @param pr        the policy restriction to filter by
   * @param policies the list of policy IDs to validate
   * @return true if all policy IDs are valid, false otherwise
   */
  boolean arePoliciesValid(String[] headers, PolicyRestriction pr, List<GroupedExternalPolicies> policies);

  /**
   * Enriches the policy information from the `id` provided
   * (Likely incoming is a {@link com.k_int.accesscontrol.core.http.responses.BasicPolicy} implementation)
   *
   * @param policies a list of AccessPolicy objects to enrich, it will use the "type" and the "policy.id" fields to enrich
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @return A list of AccessPolicy objects with all policy information provided
   */
  List<GroupedExternalPolicies> enrichPolicies(String[] headers, List<GroupedExternalPolicies> policies);
}

package com.k_int.accesscontrol.core.http.filters;

import com.k_int.accesscontrol.core.GroupedExternalPolicies;
import com.k_int.accesscontrol.core.AccessPolicyType;
import com.k_int.accesscontrol.core.DomainAccessPolicy;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;

import java.util.*;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class representing a filter containing a list of access policies.
 * <p>
 * This class is used to encapsulate a collection of {@link GroupedExternalPolicies},
 * which group access policies by their type. This group will be ORed together in a filter SQL operation performed
 * by the {@link com.k_int.accesscontrol.main.PolicyEngine}. A list of PoliciesFilter objects can be used to represent multiple
 * such groups, which will be ANDed together in the same filter operation.
 * </p>
 */
@Slf4j
@Builder
@SuppressWarnings("javadoc")
public class PoliciesFilter {
  /**
   * A list of {@link GroupedExternalPolicies} to be used as filters.
   * Each entry in the list represents a group of access policies
   * that will be ORed together in a filter operation.
   * @param filters A list of {@link GroupedExternalPolicies} to be used as filters.
   * @return A list of {@link GroupedExternalPolicies} to be used as filters.
   */
  @Getter
  List<GroupedExternalPolicies> filters;

  /** Constructor for PoliciesFilter.
   * @param filters A list of {@link GroupedExternalPolicies} to be used as filters.
   */
  public PoliciesFilter(List<GroupedExternalPolicies> filters) {
    this.filters = filters;
  }

  /** Create a PoliciesFilter from a string representation of policies.
   * The string should be formatted as `{@link AccessPolicyType}:{@link DomainAccessPolicy}.id`.
   * @param policyString The string representation of policies.
   * @return A PoliciesFilter object.
   * @throws PolicyEngineException If the policy string is invalid.
   */
  public static PoliciesFilter fromString(String policyString) {
    return new PoliciesFilter(
      GroupedExternalPolicies.fromString(policyString)
    );
  }

  /** Create a list of PoliciesFilter from a collection of string representations of policies.
   * These top level filters will be ANDed together in a filter operation, with the internal list of {@link GroupedExternalPolicies} being
   * ORed together. Each string should be formatted as
   * `{@link AccessPolicyType}:{@link DomainAccessPolicy}.id`.
   * @param policyStrings The collection of string representations of policies.
   * @return A list of PoliciesFilter objects.
   */
  public static List<PoliciesFilter> fromStringCollection(Collection<String> policyStrings) {
    return policyStrings
      .stream()
      .map(PoliciesFilter::fromString)
      .toList();
  }
}

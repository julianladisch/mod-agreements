package com.k_int.accesscontrol.core.http.bodies;

import com.k_int.accesscontrol.core.AccessPolicies;
import com.k_int.accesscontrol.core.http.responses.BasicPolicy;
import com.k_int.accesscontrol.core.http.responses.Policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a body containing a list of policy claims.
 * <p>
 * This interface is used to encapsulate the collection of policy claims
 * that are associated with a specific access control operation.
 * </p>
 * NOTE: This interface is designed to ease the implementation of a specific API.
 * Should some implementation wish to provide an alternative API then it is free to do so.
 */
public interface ClaimBody {
  /**
   * Returns the list of {@link PolicyLink} objects representing the individual policy claims.
   * <p>
   * Each {@link PolicyLink} associates a specific access policy with its type,
   * allowing further processing (e.g., grouping by type or policy evaluation).
   * </p>
   *
   * @return a list of {@link PolicyLink} instances associated with this claim body
   */
  List<PolicyLink> getClaims();

  /**
   * Returns a list of access policy IDs grouped by their type.
   * <p>
   * This method processes the claims to extract and group policy IDs
   * based on their associated access policy type.
   * </p>
   *
   * @return a list of {@link AccessPolicies} containing policy IDs grouped by type
   */
  default List<AccessPolicies> convertToAccessPolicies() {
    return getClaims().stream().reduce(
      new ArrayList<>(),
      (acc, curr) -> {
        AccessPolicies relevantTypeIds = acc.stream()
          .filter(apti -> apti.getType() == curr.getType())
          .findFirst()
          .orElse(null);

        if (relevantTypeIds != null) {

          // Update existing type with new policy ID
          ArrayList<Policy> updatedPolicyIds = new ArrayList<>(relevantTypeIds.getPolicies());
          updatedPolicyIds.add(BasicPolicy.builder().id(curr.getPolicy().getId()).build());
          relevantTypeIds.setPolicies(updatedPolicyIds);
        } else {
          acc.add(
            AccessPolicies.builder()
              .type(curr.getType())
              .policies(Collections.singletonList(BasicPolicy.builder().id(curr.getPolicy().getId()).build()))
              .name("POLICY_IDS_FOR_" + curr.getType().toString())
              .build()
          );
        }

        return acc;
      },
      (policies1, policies2) -> {
        policies1.addAll(policies2);
        return policies1;
      }
    );
  }
}

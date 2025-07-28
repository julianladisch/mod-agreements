package com.k_int.accesscontrol.core.http.bodies;

import com.k_int.accesscontrol.core.AccessPolicyTypeIds;

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
  List<PolicyClaim> getClaims();


  /**
   * Returns a list of access policy IDs grouped by their type.
   * <p>
   * This method processes the claims to extract and group policy IDs
   * based on their associated access policy type.
   * </p>
   *
   * @return a list of {@link AccessPolicyTypeIds} containing policy IDs grouped by type
   */
  default List<AccessPolicyTypeIds> convertToAccessPolicyTypeIds() {
    return getClaims().stream().reduce(
      new ArrayList<>(),
      (acc, curr) -> {
        AccessPolicyTypeIds relevantTypeIds = acc.stream()
          .filter(apti -> apti.getType() == curr.getType())
          .findFirst()
          .orElse(null);

        if (relevantTypeIds != null) {

          // Update existing type with new policy ID
          ArrayList<String> updatedPolicyIds = new ArrayList<>(relevantTypeIds.getPolicyIds());
          updatedPolicyIds.add(curr.getPolicyId());
          relevantTypeIds.setPolicyIds(updatedPolicyIds);
        } else {
          acc.add(
            AccessPolicyTypeIds.builder()
              .type(curr.getType())
              .policyIds(Collections.singletonList(curr.getPolicyId()))
              .name("POLICY_IDS_FOR_" + curr.getType().toString())
              .build()
          );
        }

        return acc;
      },
      (claim1, claim2) -> {
        claim1.addAll(claim2);
        return claim1;
      }
    );
  }
}

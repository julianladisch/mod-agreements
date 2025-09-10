package com.k_int.accesscontrol.core;

import com.k_int.accesscontrol.core.http.bodies.PolicyLink;
import com.k_int.accesscontrol.core.http.responses.BasicPolicy;
import com.k_int.accesscontrol.core.http.responses.BasicPolicyLink;
import com.k_int.accesscontrol.core.http.responses.Policy;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents a collection of access policy IDs grouped by their type.
 * <p>
 * This class is used to encapsulate the relationship between an access policy type
 * and the list of policy IDs associated with that type.
 * It is typically returned by methods that retrieve valid access policy IDs for {@link PolicyRestriction#CLAIM}.
 * </p>
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class AccessPolicies {
  /**
   * The type of access policy (e.g., ACQ_UNIT).
   * This indicates the category or classification of the policies.
   * @param type The type of access policy.
   * @return The type of access policy.
   */
  AccessPolicyType type;
  /**
   * A list of policies associated with the specified access policy type.
   * These represent the specific policies that are valid for the given type.
   * @param policies A list of policies associated with the specified access policy type.
   * @return A list of policies associated with the specified access policy type.
   */
  List<? extends Policy> policies;

  /**
   * An optional name for the access policy type.
   * This can provide additional context as to what this
   * list of policy IDs represents within the given type.
   * @param name An optional name for the access policy type.
   * @return An optional name for the access policy type.
   */
  @Nullable
  String name;


  /**
   * Converts a flat list of {@link AccessPolicy} entities into a grouped list of {@link AccessPolicies},
   * each containing a set of {@link BasicPolicy} records grouped by their {@link AccessPolicyType}.
   *
   * @param policyList the raw list of {@link AccessPolicy} entities, typically retrieved from a database
   * @return a grouped list of {@link AccessPolicies}, where each entry holds policies of a single type
   */
  public static List<AccessPolicies> fromAccessPolicyList(Collection<AccessPolicy> policyList) {
    return policyList.stream().reduce(
      new ArrayList<>(),
      ( acc, curr) -> {
        AccessPolicies relevantPoliciesEntry = acc.stream()
          .filter(policiesEntry -> policiesEntry.getType() == curr.getType())
          .findFirst()
          .orElse(null);

        if (relevantPoliciesEntry != null) {
          // Update existing type with new policy
          ArrayList<Policy> updatedPolicyIds = new ArrayList<>(relevantPoliciesEntry.getPolicies());
          updatedPolicyIds.add(BasicPolicy.builder().id(curr.getPolicyId()).build());
          relevantPoliciesEntry.setPolicies(updatedPolicyIds);
        } else {
          acc.add(
            AccessPolicies.builder()
              .type(curr.getType())
              .policies(Collections.singletonList(BasicPolicy.builder().id(curr.getPolicyId()).build()))
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

  /**
   * Converts a list of {@link AccessPolicies} into a flat list of {@link PolicyLink} instances.
   * <p>
   * Each {@link PolicyLink} includes the original {@link Policy} as well as a description and type.
   * The description is derived from the {@code name} of the access policy group and the policy ID.
   * </p>
   *
   * @param accessPolicies the grouped access policies to convert
   * @return a flat list of {@link PolicyLink} instances suitable for output in a response body
   */
  public static List<PolicyLink> convertListToPolicyLinkList(Collection<AccessPolicies> accessPolicies) {
    return accessPolicies.stream().reduce(
      new ArrayList<>(),
      (acc, curr) -> {
        // Construct a PolicyLink for EACH accessPolicies.policy entry

        // We don't currently keep any of the description or id information about AccessPolicy objects in the DB against
        // the AccessPolicies List<? extends Policy> policies, so we cannot guess at a description or id
        // We might wish to do so in future, but for now we ignore and we trust upstream callers to populate these fields if needed
        List<BasicPolicyLink> innerPolicyLinks = curr.getPolicies().stream().map(pol -> BasicPolicyLink.builder()
          .policy(pol)
          .type(curr.getType())
          .build()
        ).toList();

        acc.addAll(innerPolicyLinks);
        return acc;
      },
      (arr1, arr2) -> {
        arr1.addAll(arr2);
        return arr1;
      }
    );
  }
}

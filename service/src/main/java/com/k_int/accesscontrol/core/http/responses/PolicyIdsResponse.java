package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.AccessPolicies;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.List;

/** Helper class to represent the response of access control policy IDs. An implementation can choose to ignore this and return their own API should they wish */
@Data // It is IMPORTANT to use @Data and non final fields here
@Builder
@SuppressWarnings("javadoc")
public class PolicyIdsResponse {
  /**
   * List of access policy IDs for reading operations.
   * This field is optional and may be null if no read policies are applicable.
   *
   * @param readPolicyIds a list of {@link AccessPolicies} for read operations
   * @return a list of {@link AccessPolicies} for read operations
   */
  @Nullable
  List<AccessPolicies> readPolicyIds;
  /**
   * List of access policy IDs for updating operations.
   * This field is optional and may be null if no update policies are applicable.
   *
   * @param updatePolicyIds a list of {@link AccessPolicies} for update operations
   * @return a list of {@link AccessPolicies} for update operations
   */
  @Nullable
  List<AccessPolicies> updatePolicyIds;
  /**
   * List of access policy IDs for creating operations.
   * This field is optional and may be null if no create policies are applicable.
   *
   * @param createPolicyIds a list of {@link AccessPolicies} for create operations
   * @return a list of {@link AccessPolicies} for create operations
   */
  @Nullable
  List<AccessPolicies> createPolicyIds;
  /**
   * List of access policy IDs for deleting operations.
   * This field is optional and may be null if no delete policies are applicable.
   *
   * @param deletePolicyIds a list of {@link AccessPolicies} for delete operations
   * @return a list of {@link AccessPolicies} for delete operations
   */
  @Nullable
  List<AccessPolicies> deletePolicyIds;
  /**
   * List of access policy IDs for claim operations.
   * This field is optional and may be null if no claim policies are applicable.
   *
   * @param claimPolicyIds a list of {@link AccessPolicies} for claim operations
   * @return a list of {@link AccessPolicies} for claim operations
   */
  @Nullable
  List<AccessPolicies> claimPolicyIds;
  /**
   * List of access policy IDs for applying policies.
   * This field is optional and may be null if no apply policies are applicable.
   *
   * @param applyPolicyIds a list of {@link AccessPolicies} for apply policy operations
   * @return a list of {@link AccessPolicies} for apply policy operations
   */
  @Nullable
  List<AccessPolicies> applyPolicyIds;
}

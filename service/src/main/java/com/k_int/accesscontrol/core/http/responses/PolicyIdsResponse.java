package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.AccessPolicyTypeIds;
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
   * @return a list of {@link AccessPolicyTypeIds} for read operations
   */
  @Nullable
  List<AccessPolicyTypeIds> readPolicyIds;
  /**
   * List of access policy IDs for updating operations.
   * This field is optional and may be null if no update policies are applicable.
   *
   * @return a list of {@link AccessPolicyTypeIds} for update operations
   */
  @Nullable
  List<AccessPolicyTypeIds> updatePolicyIds;
  /**
   * List of access policy IDs for creating operations.
   * This field is optional and may be null if no create policies are applicable.
   *
   * @return a list of {@link AccessPolicyTypeIds} for create operations
   */
  @Nullable
  List<AccessPolicyTypeIds> createPolicyIds;
  /**
   * List of access policy IDs for deleting operations.
   * This field is optional and may be null if no delete policies are applicable.
   *
   * @return a list of {@link AccessPolicyTypeIds} for delete operations
   */
  @Nullable
  List<AccessPolicyTypeIds> deletePolicyIds;
  /**
   * List of access policy IDs for claim operations.
   * This field is optional and may be null if no claim policies are applicable.
   *
   * @return a list of {@link AccessPolicyTypeIds} for claim operations
   */
  @Nullable
  List<AccessPolicyTypeIds> claimPolicyIds;
  /**
   * List of access policy IDs for applying policies.
   * This field is optional and may be null if no apply policies are applicable.
   *
   * @return a list of {@link AccessPolicyTypeIds} for apply operations
   */
  @Nullable
  List<AccessPolicyTypeIds> applyPolicyIds;
}

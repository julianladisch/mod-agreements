package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.AccessPolicies;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.List;

/** Helper class to represent the response of access control policies. An implementation can choose to ignore this and return their own API should they wish */
@Data // It is IMPORTANT to use @Data and non final fields here
@Builder
@SuppressWarnings("javadoc")
public class PoliciesResponse {
  /**
   * List of access policies for reading operations.
   * This field is optional and may be null if no read policies are applicable.
   *
   * @param readPolicies a list of {@link AccessPolicies} for read operations
   * @return a list of {@link AccessPolicies} for read operations
   */
  @Nullable
  List<AccessPolicies> readPolicies;
  /**
   * List of access policies for updating operations.
   * This field is optional and may be null if no update policies are applicable.
   *
   * @param updatePolicies a list of {@link AccessPolicies} for update operations
   * @return a list of {@link AccessPolicies} for update operations
   */
  @Nullable
  List<AccessPolicies> updatePolicies;
  /**
   * List of access policies for creating operations.
   * This field is optional and may be null if no create policies are applicable.
   *
   * @param createPolicies a list of {@link AccessPolicies} for create operations
   * @return a list of {@link AccessPolicies} for create operations
   */
  @Nullable
  List<AccessPolicies> createPolicies;
  /**
   * List of access policies for deleting operations.
   * This field is optional and may be null if no delete policies are applicable.
   *
   * @param deletePolicies a list of {@link AccessPolicies} for delete operations
   * @return a list of {@link AccessPolicies} for delete operations
   */
  @Nullable
  List<AccessPolicies> deletePolicies;
  /**
   * List of access policies for claim operations.
   * This field is optional and may be null if no claim policies are applicable.
   *
   * @param claimPolicies a list of {@link AccessPolicies} for claim operations
   * @return a list of {@link AccessPolicies} for claim operations
   */
  @Nullable
  List<AccessPolicies> claimPolicies;
  /**
   * List of access policies for applying policies.
   * This field is optional and may be null if no apply policies are applicable.
   *
   * @param applyPolicies a list of {@link AccessPolicies} for apply policy operations
   * @return a list of {@link AccessPolicies} for apply policy operations
   */
  @Nullable
  List<AccessPolicies> applyPolicies;
}

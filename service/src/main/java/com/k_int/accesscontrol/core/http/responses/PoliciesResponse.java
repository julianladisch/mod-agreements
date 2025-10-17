package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.GroupedExternalPolicies;
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
   * @param readPolicies a list of {@link GroupedExternalPolicies} for read operations
   * @return a list of {@link GroupedExternalPolicies} for read operations
   */
  @Nullable
  List<GroupedExternalPolicies> readPolicies;
  /**
   * List of access policies for updating operations.
   * This field is optional and may be null if no update policies are applicable.
   *
   * @param updatePolicies a list of {@link GroupedExternalPolicies} for update operations
   * @return a list of {@link GroupedExternalPolicies} for update operations
   */
  @Nullable
  List<GroupedExternalPolicies> updatePolicies;
  /**
   * List of access policies for creating operations.
   * This field is optional and may be null if no create policies are applicable.
   *
   * @param createPolicies a list of {@link GroupedExternalPolicies} for create operations
   * @return a list of {@link GroupedExternalPolicies} for create operations
   */
  @Nullable
  List<GroupedExternalPolicies> createPolicies;
  /**
   * List of access policies for deleting operations.
   * This field is optional and may be null if no delete policies are applicable.
   *
   * @param deletePolicies a list of {@link GroupedExternalPolicies} for delete operations
   * @return a list of {@link GroupedExternalPolicies} for delete operations
   */
  @Nullable
  List<GroupedExternalPolicies> deletePolicies;
  /**
   * List of access policies for claim operations.
   * This field is optional and may be null if no claim policies are applicable.
   *
   * @param claimPolicies a list of {@link GroupedExternalPolicies} for claim operations
   * @return a list of {@link GroupedExternalPolicies} for claim operations
   */
  @Nullable
  List<GroupedExternalPolicies> claimPolicies;
  /**
   * List of access policies for applying policies.
   * This field is optional and may be null if no apply policies are applicable.
   *
   * @param applyPolicies a list of {@link GroupedExternalPolicies} for apply policy operations
   * @return a list of {@link GroupedExternalPolicies} for apply policy operations
   */
  @Nullable
  List<GroupedExternalPolicies> applyPolicies;
}

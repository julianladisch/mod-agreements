package com.k_int.accesscontrol.core.http.responses;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

/**
 * Helper class to represent the response of access control checks. An implementation can choose to ignore this and return their own API should they wish
 */
@Data // It is IMPORTANT to use @Data and non-final fields
@Builder
@SuppressWarnings("javadoc")
public class CanAccessResponse {
  /**
   * Indicates whether the user can read the resource.
   * This field is optional and may be null if the read permission is not applicable.
   *
   * @param canRead true if the user can read, false otherwise
   * @return true if the user can read, false otherwise
   */
  @Nullable
  Boolean canRead;
  /**
   * Indicates whether the user can update the resource.
   * This field is optional and may be null if the update permission is not applicable.
   *
   * @param canUpdate true if the user can update, false otherwise
   * @return true if the user can update, false otherwise
   */
  @Nullable
  Boolean canUpdate;
  /**
   * Indicates whether the user can delete the resource.
   * This field is optional and may be null if the delete permission is not applicable.
   *
   * @param canDelete true if the user can delete, false otherwise
   * @return true if the user can delete, false otherwise
   */
  @Nullable
  Boolean canDelete;
  /**
   * Indicates whether the user can create the resource.
   * This field is optional and may be null if the create permission is not applicable.
   *
   * @param canCreate true if the user can create, false otherwise
   * @return true if the user can create, false otherwise
   */
  @Nullable
  Boolean canCreate;
  /**
   * Indicates whether the user can claim the resource.
   * This field is optional and may be null if the claim permission is not applicable.
   *
   * @param canClaim true if the user can claim, false otherwise
   * @return true if the user can claim, false otherwise
   */
  @Nullable
  Boolean canClaim;
  /**
   * Indicates whether the user can apply policies to the resource.
   * This field is optional and may be null if the apply permission is not applicable.
   *
   * @param canApplyPolicies true if the user can apply policies, false otherwise
   * @return true if the user can apply policies, false otherwise
   */
  @Nullable
  Boolean canApplyPolicies;
}

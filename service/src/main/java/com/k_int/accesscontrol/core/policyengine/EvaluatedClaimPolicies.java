package com.k_int.accesscontrol.core.policyengine;

import com.k_int.accesscontrol.core.AccessPolicies;
import com.k_int.accesscontrol.core.AccessPolicy;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Stream;

/**
 * Represents the result of evaluating access policies for claim operation
 * <p>
 * This class encapsulates the lists of access policies that need to be added, removed, or updated
 * based on the evaluation of claims against existing policies.
 * </p>
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class EvaluatedClaimPolicies {
  /**
   * List of access policies to be added
   * @param policiesToAdd the list of policies to add
   * @return the list of policies to add
   */
  List<AccessPolicy> policiesToAdd;
  /**
   * List of access policies to be removed
   * @param policiesToRemove the list of policies to remove
   * @return the list of policies to remove
   */
  List<AccessPolicy> policiesToRemove;
  /**
   * List of access policies to be updated
   * @param policiesToUpdate the list of policies to update
   * @return the list of policies to update
   */
  List<AccessPolicy> policiesToUpdate;

  /**
   * Returns a consolidated list of all access policies that have changed
   * (i.e., those that are to be added, removed, or updated) grouped by their type.
   * <p>
   * This method combines the lists of policies to add, remove, and update
   * into a single list for easier processing or reporting.
   * </p>
   *
   * @return a list of all changed access policies
   */
  public List<AccessPolicies> changedPolicies() {
    return AccessPolicies.fromAccessPolicyList(
      Stream.concat(
        getPoliciesToAdd().stream(),
        Stream.concat(
          getPoliciesToUpdate().stream(),
          getPoliciesToRemove().stream()
        )
      ).toList()
    );
  }
}

package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
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
public class AccessPolicyTypeIds {
  /**
   * The type of access policy (e.g., ACQ_UNIT).
   * This indicates the category or classification of the policies.
   * @param type The type of access policy.
   * @return The type of access policy.
   */
  AccessPolicyType type;
  /**
   * A list of policy IDs associated with the specified access policy type.
   * These IDs represent the specific policies that are valid for the given type.
   * @param policyIds A list of policy IDs associated with the specified access policy type.
   * @return A list of policy IDs associated with the specified access policy type.
   */
  List<String> policyIds;

  /**
   * An optional name for the access policy type.
   * This can provide additional context as to what this
   * list of policy IDs represents within the given type.
   * @param name An optional name for the access policy type.
   * @return An optional name for the access policy type.
   */
  @Nullable
  String name;
}

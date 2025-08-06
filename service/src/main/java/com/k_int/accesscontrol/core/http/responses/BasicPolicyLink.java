package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.AccessPolicyType;
import com.k_int.accesscontrol.core.http.bodies.PolicyLink;
import lombok.Builder;
import lombok.Data;

/**
 * A basic concrete implementation of the {@link PolicyLink} interface.
 * <p>
 * This implementation is designed to serve as a simple DTO (Data Transfer Object) for
 * transferring access policy link data over HTTP or between components.
 * It provides the minimal required structure to represent a link between a resource and
 * an associated {@link Policy}, including its identifier, type, and human-readable description.
 * </p>
 *
 * <p>
 * This class is typically used in API responses and other cases where a lightweight
 * representation of a policy link is sufficient.
 * </p>
 *
 * @see PolicyLink
 * @see Policy
 */
@Data
@Builder
@SuppressWarnings("javadoc")
public class BasicPolicyLink implements PolicyLink {

  /**
   * The unique identifier for this policy link instance.
   * This identifier typically represents the join between a resource and the associated policy.
   * @param id The unique identifier
   * @return The unique identifier
   */
  String id;

  /**
   * The {@link Policy} object associated with this link.
   * This links the resource to a specific access policy.
   * @param policy The policy
   * @return The policy
   */
  Policy policy;

  /**
   * The {@link AccessPolicyType} indicating the type of policy this link represents.
   * This can help interpret the enforcement or application of the policy.
   * @param type The type
   * @return The type
   */
  AccessPolicyType type;

  /**
   * A human-readable description of the policy link.
   * This can be used to provide context or documentation for how the link is used or applied.
   * @param description The description
   * @return The description
   */
  String description;
}

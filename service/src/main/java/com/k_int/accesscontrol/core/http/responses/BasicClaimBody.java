package com.k_int.accesscontrol.core.http.responses;

import com.k_int.accesscontrol.core.http.bodies.ClaimBody;
import com.k_int.accesscontrol.core.http.bodies.PolicyLink;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * A simple implementation of the {@link ClaimBody} interface.
 * <p>
 * This class represents a collection of access policy claims as a list of {@link PolicyLink} instances.
 * It is intended to be used as a lightweight DTO for transmitting policy claims
 * in HTTP request or response bodies.
 * </p>
 *
 * @see ClaimBody
 * @see PolicyLink
 */
@Data
@Builder
public class BasicClaimBody implements ClaimBody {

  /**
   * The list of {@link PolicyLink} instances that represent access policy claims.
   * Each entry in this list associates a resource with a specific policy.
   */
  List<PolicyLink> claims;
}

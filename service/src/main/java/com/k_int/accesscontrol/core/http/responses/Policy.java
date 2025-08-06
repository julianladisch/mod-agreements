package com.k_int.accesscontrol.core.http.responses;

/**
 * An interface to ensure that we can return an enriched Policy rather than just a list of strings
 */
public interface Policy {
  /**
   * A String field that MUST be present to identify the policy.
   * For most policies (Such as acquisition units) this will be an identifier. In some cases (KIGrants)
   * this may instead be a string such as "GBV%"
   * @return The identification String for this Policy
   */
  String getId();
}

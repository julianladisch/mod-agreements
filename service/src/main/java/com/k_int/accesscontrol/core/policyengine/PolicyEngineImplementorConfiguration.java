package com.k_int.accesscontrol.core.policyengine;

/**
 * Configuration interface for the Policy Engine Implementor.
 * <p>
 * This interface defines a method to check if the policy engine implementor is enabled.
 * </p>
 */
public interface PolicyEngineImplementorConfiguration {
  /**
   * Checks if the policy engine implementor is enabled.
   *
   * @return true if the policy engine implementor is enabled, false otherwise
   */
  boolean isEnabled();
}

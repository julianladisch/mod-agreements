package com.k_int.accesscontrol.main;

import com.k_int.accesscontrol.acqunits.*;
import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.core.http.bodies.PolicyLink;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineImplementor;
import com.k_int.accesscontrol.core.sql.PolicySubquery;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core entry point for evaluating policy restrictions within the access control system.
 * The engine fetches necessary access policy data (e.g., from acquisition units)
 * and composes SQL fragments that can be used to filter Hibernate queries.
 * Configuration is driven by `PolicyEngineConfiguration`.
 */
@Slf4j
public class PolicyEngine implements PolicyEngineImplementor {
  /**
   * Configuration for the policy engine, including whether to use acquisition units.
   * This is set during construction and used to determine which policy types to query.
   */
  @Getter
  private final PolicyEngineConfiguration config;

  /**
   * The acquisition unit policy engine implementor, which handles policy subquery generation
   * for acquisition units. This is initialized based on the configuration.
   */
  @Getter
  private final AcquisitionUnitPolicyEngine acquisitionUnitPolicyEngine;

  /**
   * Constructs a new PolicyEngine with the provided configuration.
   * Initializes the acquisition unit policy engine if acquisition units are enabled in the configuration.
   *
   * @param config the configuration for the policy engine, including acquisition unit settings
   */
  public PolicyEngine(PolicyEngineConfiguration config) {
    this.config = config;

    // Initialize the acquisition unit policy engine if acquisition units are configured
    AcquisitionUnitPolicyEngineConfiguration acquisitionUnitConfig = config.getAcquisitionUnitPolicyEngineConfiguration();

    if (acquisitionUnitConfig.isEnabled()) {
      this.acquisitionUnitPolicyEngine = new AcquisitionUnitPolicyEngine(config);
    } else {
      this.acquisitionUnitPolicyEngine = null;
    }
    // In future we may add more policy engines here, such as KI Grants
  }

  /**
   * There are two types of AccessPolicy query that we might want to handle, LIST: "Show me all records for which I can do RESTRICTION" and SINGLE: "Can I do RESTRICTION for resource X?"
   *
   * @param headers The request context headers -- used mainly to connect to FOLIO (or other "internal" services)
   * @param pr The policy restriction which we want to filter by
   * @param queryType Whether to return boolean queries for single use or filter queries for all records
   * @return A list of PolicySubqueries, either for boolean restriction or for filtering.
   * @throws PolicyEngineException -- the understanding is that within a request context this should be caught and return a 500
   */
  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType) throws PolicyEngineException {
    List<PolicySubquery> policySubqueries = new ArrayList<>();

    if (pr.equals(PolicyRestriction.CLAIM)) {
      throw new PolicyEngineException("getPolicySubqueries is not valid for PolicyRestriction.CLAIM", PolicyEngineException.INVALID_RESTRICTION);
    }

    if (acquisitionUnitPolicyEngine != null) {
      policySubqueries.addAll(acquisitionUnitPolicyEngine.getPolicySubqueries(headers, pr, queryType));
    }

    return policySubqueries;
  }


  /**
   * Retrieves a list of access policy IDs grouped by their type for the given policy restriction.
   * This method is used to fetch valid policy IDs that can be used for operations like claims.
   *
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @param pr      the policy restriction to filter by
   * @return a list of {@link AccessPolicies} containing policy IDs grouped by type
   * @throws PolicyEngineException if an error occurs while fetching policy IDs
   */
  public List<AccessPolicies> getPolicyIds(String[] headers, PolicyRestriction pr) throws PolicyEngineException {
    List<AccessPolicies> policyIds = new ArrayList<>();

    if (acquisitionUnitPolicyEngine != null) {
      policyIds.addAll(acquisitionUnitPolicyEngine.getPolicyIds(headers, pr));
    }

    return policyIds;
  }

  /**
   * Validates the provided policy IDs against the access control system.
   * This method checks if the policy IDs are valid for the given policy restriction.
   *
   * @param headers  the request context headers, used for FOLIO/internal service authentication
   * @param pr       the policy restriction to filter by
   * @param policyIds the list of policy IDs to validate
   * @return true if all policy IDs are valid, false otherwise
   * @throws PolicyEngineException if an error occurs during validation
   */
  public boolean arePolicyIdsValid(String[] headers, PolicyRestriction pr, List<AccessPolicies> policyIds) throws PolicyEngineException {
    boolean isValid = true;

    // Check if isValid is true for each sub policyEngine, so that we can short-circuit if any engine returns false.
    // No need to check for the first engine, as it will always start true
    if (acquisitionUnitPolicyEngine != null) {
      isValid = acquisitionUnitPolicyEngine.arePolicyIdsValid(headers, pr, policyIds.stream().filter(pid -> pid.getType() == AccessPolicyType.ACQ_UNIT).toList());
    }

    return isValid;
  }

  /**
   * A helper method which returns the engines enabled in the config, as sorted by AccessPolicyType
   * @return A map of each AccessPolicyType and whether it is enabled or not
   */
  public Map<AccessPolicyType, Boolean> getEnabledEngines() {
    return Map.of(
      AccessPolicyType.ACQ_UNIT, getConfig().getAcquisitionUnitPolicyEngineConfiguration().isEnabled()
    );
  }

  /**
   * A helper method which returns the engines enabled in the config as a Set
   * @return A set of each enabled AccessPolicyType
   */
  public Set<AccessPolicyType> getEnabledEngineSet() {
    Map<AccessPolicyType, Boolean> enabledEngines = getEnabledEngines();
    return enabledEngines.keySet().stream().filter(key -> enabledEngines.get(key) == true).collect(Collectors.toSet());
  }

  /**
   * A function which takes in a list of {@link AccessPolicy} objects, likely with a
   * {@link com.k_int.accesscontrol.core.http.responses.Policy} implementation of
   * {@link com.k_int.accesscontrol.core.http.responses.BasicPolicy}
   * It should then return
   * @param policies a list of AccessPolicy objects to enrich, it will use the "type" and the "policy.id" fields to enrich
   * with Policy implementations from the individual engine plugins
   * @return A list of AccessPolicy objects with all information provided by the policyEngineImplementors
   */
  public List<AccessPolicies> enrichPolicies(String[] headers, List<AccessPolicies> policies) {
    List<AccessPolicies> enrichedPolicies = new ArrayList<>();

    if (acquisitionUnitPolicyEngine != null) {
      enrichedPolicies.addAll(acquisitionUnitPolicyEngine.enrichPolicies(headers, policies));
    }

    return enrichedPolicies;
  }

  /**
   * Converts a list of {@link AccessPolicy} entities into a list of {@link PolicyLink} DTOs,
   * preserving enriched policy metadata and per-resource assignment details.
   * <p>
   * This is used primarily when serializing claims or presenting assigned policies
   * for a specific domain object.
   * </p>
   *
   * @param headers        the request headers
   * @param policyEntities the access policy entities assigned to a resource
   * @return a list of enriched {@link PolicyLink} instances
   */
  public List<PolicyLink> getPolicyLinksFromAccessPolicyList(String[] headers, Collection<AccessPolicy> policyEntities) {
    // We want to turn this into the shape List<PolicyLink> (We want the enriched Policy information)
    // enrichPolicies needs ids and types, so send as AccessPolicies object
    List<AccessPolicies> accessPoliciesList = AccessPolicies.fromAccessPolicyList(policyEntities);

    // use "enrich" method from policyEngine to get List<AccessPolicies>
    List<AccessPolicies> enrichedAccessPolicies = enrichPolicies(headers, accessPoliciesList);

    // Then convert into List<PolicyLink> so it's in roughly the same shape we'd send down in a ClaimBody
    List<PolicyLink> policyLinkList = AccessPolicies.convertListToPolicyLinkList(enrichedAccessPolicies);

    // Finally, we have to add back the descriptions and ids that were set on the AccessPolicyEntities for resource
    // This isn't strictly necessary, but if not done then description will be reset and the policies will churn on each set
    return policyLinkList.stream()
      .map(pll -> {

        Optional<AccessPolicy> relevantAPEOpt = policyEntities.stream()
          .filter(ape -> Objects.equals(ape.getPolicyId(), pll.getPolicy().getId()))
          .findFirst();

        if (relevantAPEOpt.isEmpty()) {
          return pll; // Return the PolicyLink as is if we don't have a relevant AccessPolicy from the DB
        }
        AccessPolicy relevantAPE = relevantAPEOpt.get();


        pll.setId(relevantAPE.getId());
        if (relevantAPE.getDescription() != null && relevantAPE.getDescription() != pll.getDescription()) {
          pll.setDescription(relevantAPE.getDescription());
        }

        return pll;
      })
      .toList();
  }
}

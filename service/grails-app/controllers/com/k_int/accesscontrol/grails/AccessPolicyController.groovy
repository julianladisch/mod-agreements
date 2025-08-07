package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicies
import com.k_int.accesscontrol.core.AccessPolicyType
import com.k_int.accesscontrol.core.PolicyRestriction

import com.k_int.accesscontrol.core.http.responses.PoliciesResponse
import com.k_int.utils.Json
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 *  Controller for managing access policies.
 * This controller extends the PolicyEngineController to provide specific functionality for access policies.
 * It includes methods to retrieve policy IDs based on different restrictions.
 */
@Slf4j
@CurrentTenant
class AccessPolicyController extends PolicyEngineController<AccessPolicyEntity> {
  AccessPolicyController() {
    super(AccessPolicyEntity)
  }

  /**
   * Retrieves the PolicyEngine instance configured for the current request.
   * This method builds the FolioClientConfig based on environment variables or Grails application configuration.
   * It also resolves the tenant and patron information.
   *
   * @return A PolicyEngine instance configured for the current request.
   */
  private List<AccessPolicies> getPolicies(PolicyRestriction restriction) {
    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    return policyEngine.getRestrictionPolicies(grailsHeaders, restriction)
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#READ} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of read policy IDs.
   */
  @Transactional
  def getReadPolicies() {
    log.trace("AccessPolicyController::getReadPolicies")
    render text: Json.toJson(PoliciesResponse.builder().readPolicies(getPolicies(PolicyRestriction.READ)).build()), contentType: 'application/json'
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#UPDATE} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of update policy IDs.
   */
  @Transactional
  def getUpdatePolicies() {
    log.trace("AccessPolicyController::getUpdatePolicies")
    render text: Json.toJson(PoliciesResponse.builder().updatePolicies(getPolicies(PolicyRestriction.UPDATE)).build()), contentType: 'application/json'
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#CREATE} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of create policy IDs.
   */
  @Transactional
  def getCreatePolicies() {
    log.trace("AccessPolicyController::getCreatePolicies")
    render text: Json.toJson(PoliciesResponse.builder().createPolicies(getPolicies(PolicyRestriction.CREATE)).build()), contentType: 'application/json'
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#DELETE} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of delete policy IDs.
   */
  @Transactional
  def getDeletePolicies() {
    log.trace("AccessPolicyController::getDeletePolicies")
    render text: Json.toJson(PoliciesResponse.builder().deletePolicies(getPolicies(PolicyRestriction.DELETE)).build()), contentType: 'application/json'
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#CLAIM} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of claim policy IDs.
   */
  @Transactional
  def getClaimPolicies() {
    log.trace("AccessPolicyController::getClaimPolicies")
    render text: Json.toJson(PoliciesResponse.builder().claimPolicies(getPolicies(PolicyRestriction.CLAIM)).build()), contentType: 'application/json'
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#APPLY_POLICIES} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of apply policy IDs.
   */
  @Transactional
  def getApplyPolicies() {
    log.trace("AccessPolicyController::getApplyPolicies")
    render text: Json.toJson(PoliciesResponse.builder().applyPolicies(getPolicies(PolicyRestriction.APPLY_POLICIES)).build()), contentType: 'application/json'
  }

  /**
   * Override save method to prevent direct creation of AccessPolicies.
   * Instead, resources must be claimed via accessControl endpoints.
   */
  @Transactional
  def save() {
    respond ([ message: "AccessPolicies cannot be created directly, instead a resource must be claimed via accessControl endpoints" ], status: 403 )
  }

  /**
   * Override update method to prevent direct updates of AccessPolicies.
   * Instead, resources must be claimed via accessControl endpoints.
   */
  @Transactional
  def update() {
    respond ([ message: "AccessPolicies cannot be updated directly, instead a resource must be claimed via accessControl endpoints" ], status: 403 )
  }

  /**
   * Override delete method to prevent direct deletion of AccessPolicies.
   * Instead, resources must be claimed via accessControl endpoints.
   */
  @Transactional
  def delete() {
    respond ([ message: "AccessPolicies cannot be deleted directly, instead a resource must be claimed via accessControl endpoints" ], status: 403 )
  }

  /**
   * Fetch the enabled engines for the PolicyEngine sorted by AccessPolicyType (Converted to String)
   */
  def getEnabledEngines() {
    // Map <AccessPolicyType, Boolean> won't render as is, convert to Map<String, Boolean>
    Map<AccessPolicyType, Boolean> enabledEngines = policyEngine.getEnabledEngines()

    Map<String, Boolean> enabledEnginesResponse = new HashMap<>()
    enabledEngines.keySet().each {
      enabledEnginesResponse.put(it.toString(), enabledEngines.get(it))
    }

    respond enabledEnginesResponse
  }
}

package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyTypeIds
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.http.responses.PolicyIdsResponse
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
  private List<AccessPolicyTypeIds> getPolicyIds(PolicyRestriction restriction) {
    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    return policyEngine.getPolicyIds(grailsHeaders, restriction)
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#READ} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of read policy IDs.
   */
  @Transactional
  def getReadPolicyIds() {
    log.trace("AccessPolicyController::getReadPolicyIds")
    respond PolicyIdsResponse.builder().readPolicyIds(getPolicyIds(PolicyRestriction.READ)).build()
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#UPDATE} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of update policy IDs.
   */
  @Transactional
  def getUpdatePolicyIds() {
    log.trace("AccessPolicyController::getUpdatePolicyIds")
    respond PolicyIdsResponse.builder().updatePolicyIds(getPolicyIds(PolicyRestriction.UPDATE)).build()
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#CREATE} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of create policy IDs.
   */
  @Transactional
  def getCreatePolicyIds() {
    log.trace("AccessPolicyController::getCreatePolicyIds")
    respond PolicyIdsResponse.builder().createPolicyIds(getPolicyIds(PolicyRestriction.CREATE)).build()
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#DELETE} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of delete policy IDs.
   */
  @Transactional
  def getDeletePolicyIds() {
    log.trace("AccessPolicyController::getDeletePolicyIds")
    respond PolicyIdsResponse.builder().deletePolicyIds(getPolicyIds(PolicyRestriction.DELETE)).build()
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#CLAIM} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of claim policy IDs.
   */
  @Transactional
  def getClaimPolicyIds() {
    log.trace("AccessPolicyController::getClaimPolicyIds")
    respond PolicyIdsResponse.builder().claimPolicyIds(getPolicyIds(PolicyRestriction.CLAIM)).build()
  }

  /**
   * Retrieves the policy IDs for the {@link PolicyRestriction#APPLY_POLICIES} restriction.
   * This method is transactional and responds with a list of policy IDs.
   *
   * @return A response containing the list of apply policy IDs.
   */
  @Transactional
  def getApplyPolicyIds() {
    log.trace("AccessPolicyController::getApplyPolicyIds")
    respond PolicyIdsResponse.builder().applyPolicyIds(getPolicyIds(PolicyRestriction.APPLY_POLICIES)).build()
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
}

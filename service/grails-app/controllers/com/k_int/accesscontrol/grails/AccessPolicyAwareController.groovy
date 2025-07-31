package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyTypeIds
import com.k_int.accesscontrol.core.http.responses.CanAccessResponse
import com.k_int.accesscontrol.core.sql.AccessControlSql
import com.k_int.accesscontrol.core.AccessPolicyQueryType
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledManager
import com.k_int.accesscontrol.core.policycontrolled.PolicyControlledMetadata
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException
import com.k_int.accesscontrol.core.PolicyRestriction
import com.k_int.accesscontrol.core.sql.AccessControlSqlType
import com.k_int.accesscontrol.core.sql.PolicySubquery
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters
import com.k_int.accesscontrol.grails.criteria.AccessControlHibernateTypeMapper
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.grails.criteria.MultipleAliasSQLCriterion
import com.k_int.okapi.OkapiClient
import grails.gorm.transactions.Transactional
import org.hibernate.Criteria
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.query.NativeQuery
import org.hibernate.type.Type
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.PostConstruct
import java.time.Duration

/**
 * Extends com.k_int.okapi.OkapiTenantAwareController to incorporate access policy enforcement for resources.
 * This controller provides methods to check read, update, and delete permissions based on defined
 * access policies, including handling complex ownership chains and dynamic policy evaluation.
 *
 * @param <T> The type of the resource entity managed by this controller.
 */
class AccessPolicyAwareController<T> extends PolicyEngineController<T> {
  /**
   * The Class object representing the resource entity type managed by this controller.
   */
  final Class<T> resourceClass

  /**
   * Manages policy-controlled metadata and ownership chains for the resource.
   */
  final PolicyControlledManager policyControlledManager

  // We need to inject the hibernate session factory to map between AccessControl types and Hibernate types while obeying dialect etc etc.
  @Autowired
  SessionFactory hibernateSessionFactory

  /**
   * A mapper for converting AccessControl SQL types to Hibernate types.
   */
  AccessControlHibernateTypeMapper typeMapper // Initialised in PostConstruct


  /**
   * Constructs an {@code AccessPolicyAwareController} for a given resource class.
   * @param resource The Class object of the resource entity.
   */
  AccessPolicyAwareController(Class<T> resource) {
    super(resource)
    this.resourceClass = resource
    this.policyControlledManager = new PolicyControlledManager(resource)
  }

  /**
   * Constructs an {@code AccessPolicyAwareController} for a given resource class,
   * with an option to mark it as read-only.
   * @param resource The Class object of the resource entity.
   * @param readOnly A boolean indicating if the controller should operate in read-only mode.
   */
  AccessPolicyAwareController(Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
    this.resourceClass = resource
    this.policyControlledManager = new PolicyControlledManager(resource)
  }

  /**
   * Initializes the AccessControlHibernateTypeMapper once dependencies are injected.
   */
  @PostConstruct
  void initTypeMapper() {
    // hibernateSessionFactory is guaranteed to be injected by now
    this.typeMapper = new AccessControlHibernateTypeMapper(hibernateSessionFactory as SessionFactoryImplementor)
  }

  /**
   * Resolves the ID of the ultimate owner (root) in an ownership chain, starting from a leaf resource ID.
   * If no ownership chain is configured, the leaf resource itself is considered the root.
   * This method dynamically constructs an HQL query to traverse the chain and fetch the root ID.
   *
   * @param leafResourceId The ID of the leaf resource for which to find the root owner.
   * @return The ID of the root owner, or the {@code leafResourceId} if no owners are configured or resolution fails.
   */
  protected String resolveRootOwnerId(String leafResourceId) {
    if (!policyControlledManager.hasOwners()) {
      // If there are no configured owners, the leaf resource itself is the "root" for policy purposes
      return leafResourceId
    }
    List<PolicyControlledMetadata> ownershipChain = policyControlledManager.getOwnershipChain()
    PolicyControlledMetadata leafMetadata = policyControlledManager.getLeafPolicyControlledMetadata()
    PolicyControlledMetadata rootMetadata = policyControlledManager.getRootPolicyControlledMetadata()

    // Dynamically build an HQL query to traverse the ownership chain
    StringBuilder hql = new StringBuilder("SELECT t${ownershipChain.size() - 1}.${rootMetadata.resourceIdField}")
    hql.append(" FROM ${leafMetadata.resourceClassName} t0") // Start from the leaf entity

    // Build the JOINs up the chain
    for (int i = 0; i < ownershipChain.size() - 1; i++) {
      PolicyControlledMetadata currentMetadata = ownershipChain.get(i)
      // Ensure currentMetadata.ownerField is not null/empty before appending join
      if (currentMetadata.ownerField) {
        hql.append(" JOIN t${i}.${currentMetadata.ownerField} t${i+1}")
      }
    }

    hql.append(" WHERE t0.id = :leafResourceId") // Filter by the requested leaf resource ID

    // Execute the HQL query and return the id at hand
    String resolvedRootId = AccessPolicyEntity.executeQuery(hql.toString(), ["leafResourceId": leafResourceId])[0]

    // Return the resolved ID, or fallback to the leaf ID if resolution fails (e.g., entity not found)
    return resolvedRootId ?: leafResourceId
  }

  protected String resolveRootOwnerClass() {
    PolicyControlledMetadata leafMetadata = policyControlledManager.getLeafPolicyControlledMetadata()

    if (!policyControlledManager.hasOwners()) {
      // If there are no configured owners, the leaf resource itself is the "root" for policy purposes

      return leafMetadata?.resourceClassName
    }
    PolicyControlledMetadata rootMetadata = policyControlledManager.getRootPolicyControlledMetadata()
    return rootMetadata.resourceClassName
  }

  /**
   * Generates a list of SQL fragments (policy subqueries) based on a given policy restriction,
   * query type, and the resource ID to which the policy applies.
   * This method communicates with the {@link PolicyEngine} to retrieve the policy definitions
   * and formats them into SQL suitable for database queries.
   *
   * @param restriction The type of policy restriction (e.g., READ, UPDATE, DELETE).
   * @param queryType The type of query (e.g., SINGLE for individual resource checks, LIST for collection checks).
   * @param resourceId The ID of the resource to apply the policy to. Can be {@code null} for LIST queries.
   * @return A list of SQL string fragments representing the access policies.
   */
  protected List<AccessControlSql> getPolicySql(PolicyRestriction restriction, AccessPolicyQueryType queryType, String resourceId) {
    // FIXME THIS IS HERE FOR LOGGING PURPOSES AND WE SHOULD REMOVE LATER
    log.info("ACCESS POLICY AWARE CONTROLLER GETPOLICYSQL")
    def p = getPatron()
    log.info("WHAT IS PATRON: ${p}")
    if (p.hasProperty("id")) {
      log.info("WHAT IS PATRON ID: ${p.id}")
    }
    /* ------------------------------- ACTUALLY DO THE WORK FOR EACH POLICY RESTRICTION ------------------------------- */

    // This should pass down all headers to the policyEngine. We can then choose to ignore those should we wish (Such as when logging into an external FOLIO)
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)

    List<PolicySubquery> policySubqueries = policyEngine.getPolicySubqueries(grailsHeaders, restriction, queryType)

    String resourceAlias = '{alias}'
    PolicyControlledMetadata rootPolicyControlledMetadata = policyControlledManager.getRootPolicyControlledMetadata()
    if (policyControlledManager.hasOwners()) {
      resourceAlias = rootPolicyControlledMetadata.getAliasName()
    } // If there are no "owners" then rootPolicyControlledMetadata should equal leafPolicyControlledMetadata ie, resourceClass

    // We build a parameter block to use on the policy subqueries. Some of these we can probably set up ahead of time...
    PolicySubqueryParameters params = PolicySubqueryParameters
      .builder()
      .accessPolicyTableName(AccessPolicyEntity.TABLE_NAME)
      .accessPolicyTypeColumnName(AccessPolicyEntity.TYPE_COLUMN)
      .accessPolicyIdColumnName(AccessPolicyEntity.POLICY_ID_COLUMN)
      .accessPolicyResourceIdColumnName(AccessPolicyEntity.RESOURCE_ID_COLUMN)
      .accessPolicyResourceClassColumnName(AccessPolicyEntity.RESOURCE_CLASS_COLUMN)
      .resourceAlias(resourceAlias) // This alias can be deeply nested from owner or '{alias}' for hibernate top level queries
      .resourceIdColumnName(rootPolicyControlledMetadata.resourceIdColumn)
      .resourceId(resourceId) // This might be null (For LIST type queries)
      .resourceClass(rootPolicyControlledMetadata.resourceClassName)
      .build()

    log.trace("PolicySubqueryParameters configured: ${params}")

    return policySubqueries.collect { psq -> psq.getSql(params)}
  }


  // CLAIM is not supported with canAccess, and is instead handled by the AccessPolicyController
  /* --------------------- DYNAMICALLY ASSIGNED ACCESSCONTROL METHODS --------------------- */

  /**
   * Returns a set of {@link PolicyRestriction} enums that are considered valid
   * for single resource access checks (e.g., in {@code canAccess} method).
   *
   * <p>Individual controllers can override this method to customize which
   * policy restrictions are supported for direct access checks.</p>
   *
   * <p>The {@code @SuppressWarnings('GrMethodMayBeStatic')} annotation is used
   * to suppress IDE warnings, as this method might be overridden in subclasses
   * where it could potentially depend on instance state.</p>
   *
   * @return An {@link EnumSet} containing the valid policy restrictions for access checks.
   */
  @SuppressWarnings('GrMethodMayBeStatic') // Intellij won't shut up about making this static
  protected Set<PolicyRestriction> getCanAccessValidPolicyRestrictions() {
    return EnumSet.of(
      PolicyRestriction.CREATE,
      PolicyRestriction.DELETE,
      PolicyRestriction.UPDATE,
      PolicyRestriction.READ,
      PolicyRestriction.APPLY_POLICIES,
    )
  }

  /**
   * Determines if a single resource can be accessed for a given {@link PolicyRestriction}.
   * This method resolves the root owner ID (if applicable), retrieves policy SQL fragments,
   * and executes a native SQL query combining these fragments to check access.
   *
   * @param pr The {@link PolicyRestriction} to check. The validity of this restriction
   * is determined by {@link #getCanAccessValidPolicyRestrictions()}.
   * @return {@code true} if access is allowed according to the policies, {@code false} otherwise.
   *
   * @throws PolicyEngineException if the provided restriction type is not supported as per {@link #getCanAccessValidPolicyRestrictions()}.
   */
  protected boolean canAccess(PolicyRestriction pr) {
    // FIXME THIS IS HERE FOR LOGGING PURPOSES AND WE SHOULD REMOVE LATER
    log.info("ACCESS POLICY AWARE CONTROLLER CANACCESS")
    def p = getPatron()
    log.info("WHAT IS PATRON: ${p}")
    if (p.hasProperty("id")) {
      log.info("WHAT IS PATRON ID: ${p.id}")
    }
    AccessPolicyEntity.withNewSession { Session sess ->
      // Handle OWNER logic

      // If there are NO owners, we can use the queryResourceId from the request itself
      String queryResourceId = resolveRootOwnerId(params.id)

      if (!getCanAccessValidPolicyRestrictions().contains(pr)) {
        throw new PolicyEngineException("Restriction: ${pr.toString()} is not accessible here", PolicyEngineException.INVALID_RESTRICTION)
      }

      // We have a valid restriction, lets get the policySql
      List<AccessControlSql> policySqlFragments = getPolicySql(pr, AccessPolicyQueryType.SINGLE, queryResourceId)

      // If we have no SQL here, just shortcut to true
      if (policySqlFragments.size() == 0) {
        return true
      }

      log.trace("AccessControl generated PolicySql: ${policySqlFragments.collect{ it.sqlString }.join(', ')}")
      log.trace("AccessControl generated PolicySql parameters: ${policySqlFragments.collect{ it.parameters.collect { it.toString() } }.join(', ')}")
      log.trace("AccessControl generated PolicySql types: ${policySqlFragments.collect{ it.types.collect { it.toString() } }.join(', ')}")

      // We're going to do this with hibernate criteria builder to match doTheLookup logic
      String bigSql = policySqlFragments.collect {"(${it.getSqlString()})" }.join(" AND ") // JOIN all sql subqueries together here.
      NativeQuery accessAllowedQuery = sess.createNativeQuery("SELECT ${bigSql} AS access_allowed".toString())

      // Now bind all parameters for all sql fragments. We ASSUME they're all using ? for bind params.
      // Track where we're up to with hibernateParamIndex -- hibernate is 1-indexed
      int hibernateParamIndex = 1
      policySqlFragments.each { AccessControlSql entry ->
        entry.getParameters().eachWithIndex { Object param, int paramIndex  ->
          accessAllowedQuery.setParameter(hibernateParamIndex, param, (Type) typeMapper.getHibernateType((AccessControlSqlType) entry.getTypes()[paramIndex]))
          hibernateParamIndex++ // Iterate the outer index
        }
      }

      boolean result = accessAllowedQuery.list()[0]

      return result
    }
  }

  boolean canUserRead() {
    // FIXME THIS IS HERE FOR LOGGING PURPOSES AND WE SHOULD REMOVE LATER
    log.info("ACCESS POLICY AWARE CONTROLLER CANUSERREAD")
    def p = getPatron()
    log.info("WHAT IS PATRON: ${p}")
    if (p.hasProperty("id")) {
      log.info("WHAT IS PATRON ID: ${p.id}")
    }
    return canAccess(PolicyRestriction.READ)
  }

  boolean canUserUpdate() {
    return canAccess(PolicyRestriction.UPDATE)
  }

  boolean canUserDelete() {
    return canAccess(PolicyRestriction.DELETE)
  }

  boolean canUserCreate() {
    return canAccess(PolicyRestriction.CREATE)
  }

  boolean canUserApplyPolicies() {
    return canAccess(PolicyRestriction.APPLY_POLICIES)
  }

  /**
   * Checks if the currently authenticated user has read access to the resource identified by {@code params.id}.
   * The result is returned in the response map.
   */
  @Transactional
  def canRead() {
    log.trace("AccessPolicyAwareController::canRead")
    respond CanAccessResponse.builder().canRead(canUserRead()).build()
  }

  /**
 * Checks if the currently authenticated user has update access to the resource identified by {@code params.id}.
 * The result is returned in the response map.
 */
  @Transactional
  def canUpdate() {
    log.trace("AccessPolicyAwareController::canUpdate")
    respond CanAccessResponse.builder().canUpdate(canUserUpdate()).build()
  }

  /**
   * Checks if the currently authenticated user has delete access to the resource identified by {@code params.id}.
   * The result is returned in the response map.
   */
  @Transactional
  def canDelete() {
    log.trace("AccessPolicyAwareController::canDelete")
    respond CanAccessResponse.builder().canDelete(canUserDelete()).build()
  }

  /**
   * Checks if the currently authenticated user has permission to create new resources of the type managed by this controller.
   * The result is returned in the response map.
   */
  @Transactional
  def canCreate() {
    log.trace("AccessPolicyAwareController::canCreate")
    respond CanAccessResponse.builder().canCreate(canUserCreate()).build()
  }

  /**
   * Checks if the currently authenticated user can apply policies to the resource identified by {@code params.id}.
   * This method is used to determine if the user has permission to apply access policies to the resource.
   * The result is returned in the response map.
   */
  @Transactional
  def canApplyPolicies() {
    log.trace("AccessPolicyAwareController::canApplyPolicies")
    respond CanAccessResponse.builder().canApplyPolicies(canUserApplyPolicies()).build()
  }

  /**
   * Checks if a given list of policy IDs are valid for a specific policy restriction.
   * This method consults the {@link PolicyEngine} to validate the policy IDs against
   * the current user's permissions and the specified restriction.
   *
   * @param pr The {@link PolicyRestriction} to check against.
   * @param policyIds A list of {@link AccessPolicyTypeIds} representing the policy IDs to validate.
   * @return {@code true} if all provided policy IDs are valid for the given restriction, {@code false} otherwise.
   */
  protected boolean arePolicyIdsValid(PolicyRestriction pr, List<AccessPolicyTypeIds> policyIds) {
    String[] grailsHeaders = convertGrailsHeadersToStringArray(request)
    return policyEngine.arePolicyIdsValid(grailsHeaders, pr, policyIds)
  }

/**
 * Checks if a given list of policy IDs are valid for the {@code CREATE} policy restriction.
 *
 * @param policyIds A list of {@link AccessPolicyTypeIds} representing the policy IDs to validate.
 * @return {@code true} if all provided policy IDs are valid for CREATE, {@code false} otherwise.
 */
  protected boolean areCreateIdsValid(List<AccessPolicyTypeIds> policyIds) {
    return arePolicyIdsValid(PolicyRestriction.CREATE, policyIds)
  }

  /**
   * Checks if a given list of policy IDs are valid for the {@code READ} policy restriction.
   *
   * @param policyIds A list of {@link AccessPolicyTypeIds} representing the policy IDs to validate.
   * @return {@code true} if all provided policy IDs are valid for READ, {@code false} otherwise.
   */
  protected boolean areReadIdsValid(List<AccessPolicyTypeIds> policyIds) {
    return arePolicyIdsValid(PolicyRestriction.READ, policyIds)
  }

  /**
   * Checks if a given list of policy IDs are valid for the {@code UPDATE} policy restriction.
   *
   * @param policyIds A list of {@link AccessPolicyTypeIds} representing the policy IDs to validate.
   * @return {@code true} if all provided policy IDs are valid for UPDATE, {@code false} otherwise.
   */
  protected boolean areUpdateIdsValid(List<AccessPolicyTypeIds> policyIds) {
    return arePolicyIdsValid(PolicyRestriction.UPDATE, policyIds)
  }

  /**
   * Checks if a given list of policy IDs are valid for the {@code DELETE} policy restriction.
   *
   * @param policyIds A list of {@link AccessPolicyTypeIds} representing the policy IDs to validate.
   * @return {@code true} if all provided policy IDs are valid for DELETE, {@code false} otherwise.
   */
  protected boolean areDeleteIdsValid(List<AccessPolicyTypeIds> policyIds) {
    return arePolicyIdsValid(PolicyRestriction.DELETE, policyIds)
  }

  /**
   * Checks if a given list of policy IDs are valid for the {@code CLAIM} policy restriction.
   *
   * @param policyIds A list of {@link AccessPolicyTypeIds} representing the policy IDs to validate.
   * @return {@code true} if all provided policy IDs are valid for CLAIM, {@code false} otherwise.
   */
  protected boolean areClaimIdsValid(List<AccessPolicyTypeIds> policyIds) {
    return arePolicyIdsValid(PolicyRestriction.CLAIM, policyIds)
  }

  /**
   * Checks if a given list of policy IDs are valid for the {@code APPLY_POLICIES} policy restriction.
   *
   * @param policyIds A list of {@link AccessPolicyTypeIds} representing the policy IDs to validate.
   * @return {@code true} if all provided policy IDs are valid for APPLY_POLICIES, {@code false} otherwise.
   */
  protected boolean areApplyPoliciesIdsValid(List<AccessPolicyTypeIds> policyIds) {
    return arePolicyIdsValid(PolicyRestriction.APPLY_POLICIES, policyIds)
  }



  // Comment out to allow quick revert to default index behaviour for development
  /**
   * Overrides the default index method to apply access control policies when listing resources.
   * This method uses the {@link AccessPolicyEntity} to enforce {@link PolicyRestriction#READ} restrictions based on the user's access policies.
   * It constructs SQL criteria dynamically based on the configured ownership chain and policy restrictions.
   */
  @Transactional
  def index(Integer max) {
    // Protect the index method with access control -- replace the built in "index" method
    AccessPolicyEntity.withNewSession {
      List<AccessControlSql> policySql = getPolicySql(PolicyRestriction.READ, AccessPolicyQueryType.LIST, null)
      log.trace("AccessControl generated PolicySql: ${policySql.collect{ it.sqlString }.join(', ')}")
      log.trace("AccessControl generated PolicySql parameters: ${policySql.collect{ it.parameters.collect { it.toString() } }.join(', ')}")
      log.trace("AccessControl generated PolicySql types: ${policySql.collect{ it.types.collect { it.toString() } }.join(', ')}")

      long beforeLookup = System.nanoTime()
      respond doTheLookup(resourceClass) {

        // To handle nested levels of ownership, we have pre-parsed the owner tree
        MultipleAliasSQLCriterion.SubCriteriaAliasContainer[] subCriteria = policyControlledManager.getNonLeafOwnershipChain().collect { pcm ->
          Criteria aliasCriteria = criteria.createCriteria(pcm.getAliasOwnerField(), pcm.getAliasName())
          return new MultipleAliasSQLCriterion.SubCriteriaAliasContainer(pcm.getAliasName(), aliasCriteria)
        }

        policySql.each {psql ->
          String sqlString = psql.getSqlString()
          Object[] parameters = psql.getParameters()
          Type[] types = psql.getTypes().collect { acst -> typeMapper.getHibernateType(acst) } as Type[]

          criteria.add(new MultipleAliasSQLCriterion(sqlString, parameters, types, subCriteria))
        }
        // Ensure we return criteria at the bottom?
        return criteria
      }

      long afterLookup = System.nanoTime()
      log.trace("AccessPolicyAwareController::testReadRestrictedList query time: {}", Duration.ofNanos(afterLookup - beforeLookup))
    }
  }

  /**
   * Overrides the show method to apply access control policies when retrieving a single resource.
   * This method checks if the user has read access to the resource identified by {@code params.id}
   * and responds accordingly.
   *
   * @return A response containing the resource if access is granted, or an error message if access is denied.
   */
  @Transactional
  def show() {
    // FIXME THIS IS HERE FOR LOGGING PURPOSES AND WE SHOULD REMOVE LATER
    log.info("ACCESS POLICY AWARE CONTROLLER SHOW BEFORE CANUSERREAD")
    def p = getPatron()
    log.info("WHAT IS PATRON: ${p}")
    if (p.hasProperty("id")) {
      log.info("WHAT IS PATRON ID: ${p.id}")
    }
    if (canUserRead()) {
      // FIXME THIS IS HERE FOR LOGGING PURPOSES AND WE SHOULD REMOVE LATER
      log.info("ACCESS POLICY AWARE CONTROLLER SHOW AFTER CANUSERREAD")
      def p2 = getPatron()
      log.info("WHAT IS PATRON: ${p2}")
      if (p2.hasProperty("id")) {
        log.info("WHAT IS PATRON ID: ${p2.id}")
      }
      super.show()
      return
    }

    respond ([ message: "PolicyRestriction.READ check failed in access control" ], status: 403 )
  }

  /**
   * Overrides the save method to apply access control policies when creating a new resource.
   * This method checks if the user has create access and responds accordingly.
   *
   * @return A response containing the created resource if access is granted, or an error message if access is denied.
   */
  @Transactional
  def save() {
    if (canUserCreate()) {
      super.save()
      return
    }

    respond ([ message: "PolicyRestriction.CREATE check failed in access control" ], status: 403 )
  }

  /**
   * Overrides the update method to apply access control policies when updating an existing resource.
   * This method checks if the user has update access to the resource identified by {@code params.id}
   * and responds accordingly.
   *
   * @return A response containing the updated resource if access is granted, or an error message if access is denied.
   */
  @Transactional
  def update() {
    if (canUserUpdate()) {
      super.update()
      return
    }

    respond ([ message: "PolicyRestriction.UPDATE check failed in access control" ], status: 403 )
  }

  /**
   * Overrides the delete method to apply access control policies when deleting a resource.
   * This method checks if the user has delete access to the resource identified by {@code params.id}
   * and responds accordingly.
   *
   * @return A response indicating successful deletion if access is granted, or an error message if access is denied.
   */
  @Transactional
  def delete() {
    if (canUserDelete()) {
      super.delete()
      return
    }

    respond ([ message: "PolicyRestriction.DELETE check failed in access control" ], status: 403 )
  }

  /**
   * Handles the claiming of a resource by checking if the user has permission to apply policies.
   * If the user has the {@link PolicyRestriction#APPLY_POLICIES} permission, it processes the claim.
   * Otherwise, it responds with a 403 Forbidden status.
   *
   * The POST body should contain the policies to be applied to the resource,
   * and these are expected to be valid for the {@link PolicyRestriction#CLAIM} restriction.
   * DOES NOT follow the _delete pattern, as it is not a GORM entity. Instead a POST without some AccessPolicyEntity id will remove said policy
   *
   * @return A response indicating the result of the claim operation.
   */
  def claim(GrailsClaimBody claimBody) {
    if (claimBody.hasErrors()) {
      // If there are errors, respond with a 400 Bad Request and the errors object
      respond claimBody.errors, status: 400
      return
    }

    if (policyControlledManager.hasOwners()) {
      // If there are owners, then don't allow claiming (for now)
      respond ([ message: 'Claiming is not supported on resources PolicyControlled via an owner' ], status: 400 )
      return
    }

    // Strategy - Check whether APPLY_POLICIES is allowed on the resource, and if so, then check whether all policies in the request body are valid for CLAIM.
    if (!canUserApplyPolicies()) {
      respond ([ message: "PolicyRestriction.APPLY_POLICIES check failed in access control" ], status: 403 )
      return
    }

    // We need to transform the claimBody into a list of AccessPolicyTypeIds objects to use policyEngine.arePolicyIdsValid
    List<AccessPolicyTypeIds> policyIds = claimBody.convertToAccessPolicyTypeIds()

    if (!areClaimIdsValid(policyIds)) {
      respond ([ message: "PolicyRestriction.CLAIM not valid for one or more policyIds in claims" ], status: 403 )
      return
    }

    // At this point, we know that the user has permission to apply policies and that the policies in the claimBody are valid for CLAIM.

    // Might not need to do this now, since we're cancelling early if there are owners
    String resourceId = resolveRootOwnerId(params.id)
    String resourceClass = resolveRootOwnerClass()
    boolean success = true
    String failureMessage = ''
    int failureCode = 400
    AccessPolicyEntity.withNewSession { sess ->
      AccessPolicyEntity.withTransaction { transactionStatus ->

        // Firstly delete any AccessPolicyEntities for this resource NOT in the claimBody
        // We will then add/update policies from the claimBody
        // We do the delete first so that if we are accidentally replacing a policy like-for-like without an id,
        // we don't fail to add it thanks to duplicate check below, and then remove it, leaving resource unprotected
        List<AccessPolicyEntity> accessPoliciesForResource = AccessPolicyEntity.findAllByResourceIdAndResourceClass(resourceId, resourceClass)
        for (AccessPolicyEntity policy : accessPoliciesForResource) {
          if (!claimBody.claims.any { claim -> claim.id == policy.id || claim.id == null }) {
            // If the policy is not in the claimBody, we delete it
            policy.delete(failOnError: true)
          }
        }

        // Fetch again after we complete delete step
        accessPoliciesForResource = AccessPolicyEntity.findAllByResourceIdAndResourceClass(resourceId, resourceClass)

        // For each claim in the body, we need to first check whether the policy currently exists. If it does, we can update it (description ONLY)
        for(GrailsClaimBody.GrailsPolicyClaim claim  : claimBody.claims) {
          if (claim.id) {
            // If the claim has an ID, we assume it is an existing policy that needs to be updated
            AccessPolicyEntity existingPolicy = AccessPolicyEntity.findById(claim.id)
            // If we're handed a non existing policy ID, we should fail the request
            if (existingPolicy == null) {
              success = false
              failureMessage = "Access policy with ID ${claim.id} does not exist."
              log.error(failureMessage)
              break
            }

            // If existing policy is for a different resource, we should also fail the request
            if (existingPolicy.resourceId != resourceId) {
              success = false
              failureMessage = "Access policy ${existingPolicy.id} has resource ID: ${existingPolicy.resourceId} which does not match resource ID ${resourceId}."
              log.error(failureMessage)
              break
            }

            // If existing policy is for a different class, we should also fail the request
            if (existingPolicy.resourceClass != resourceClass) {
              success = false
              failureMessage = "Access policy ${existingPolicy.id} has resource class: ${existingPolicy.resourceClass} which does not match resource class ${resourceClass}."
              log.error(failureMessage)
              break
            }

            // Update the description field ONLY... once a policy is created, we don't change any other information about it.
            existingPolicy.description = claim.description
            existingPolicy.save(failOnError: true)
          } else if (
            accessPoliciesForResource.any { ap -> ap.policyId == claim.policyId && ap.type == claim.type } // Only create if there is no existing policy with the same policyId
          ) {
            success = false
            failureMessage = "Resource ${resourceId} already has an access policy with policyId ${claim.policyId}."
            log.error(failureMessage)
            break
          } else {
            // If no ID, we create a new policy
            new AccessPolicyEntity(
              policyId: claim.policyId,
              type: claim.type,
              description: claim.description,
              resourceId: resourceId,
              resourceClass: resourceClass
            ).save(failOnError: true)
          }
        }

        // Rollback the transaction if any of the operations failed
        if (!success) {
          transactionStatus.setRollbackOnly()
        }
      }
      sess.flush() // Ensure all changes are flushed to the database
    }


    // If the claims failed, respond with the failure message and code (rollback happened above, feels a little iffy)
    if (!success) {
      respond ([ message: failureMessage ], status: failureCode )
      return
    }

    respond ([ message: "Access policies updated for this resource" ], status: 201 )
  }

  @Transactional
  def policies() {
    AccessPolicyEntity.withNewSession {
      respond doTheLookup(AccessPolicyEntity) {
        eq 'resourceId', resolveRootOwnerId(params.id)
      }
    }
  }
}

package org.olf

import java.time.Instant
import java.time.LocalDate

import javax.servlet.http.HttpServletRequest

import org.grails.datastore.mapping.validation.ValidationException
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.hibernate.sql.JoinType
import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.erm.Entitlement
import org.olf.general.jobs.CoverageRegenerationJob
import org.olf.kb.AbstractCoverageStatement
import org.olf.kb.CoverageStatement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.validation.ObjectError
import org.springframework.web.context.request.RequestContextHolder

import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.k_int.okapi.OkapiTenantResolver

import grails.events.annotation.Subscriber
import grails.gorm.DetachedCriteria
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import grails.util.Holders
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional(readOnly=true)
public class CoverageService {
  private static UtilityService getUtilityService() {
    Holders.grailsApplication.mainContext.getBean('utilityService')
  }

  private static MessageSource getMessageSource() {
    Holders.grailsApplication.mainContext.getBean('messageSource')
  }

  private Map<String, Iterable<AbstractCoverageStatement>> addToRequestIfPresent (final Map<String, Iterable<AbstractCoverageStatement>> statements) {

    if (statements) {
      GrailsWebRequest rAtt = (GrailsWebRequest)RequestContextHolder.getRequestAttributes()
      if (rAtt) {
        final String controllerName = rAtt.controllerName
        final String actionName = rAtt.actionName
        final HttpServletRequest request = rAtt.request

        final String key = "${controllerName}.${actionName}.customCoverage"
        final Map<String, Set<AbstractCoverageStatement>> current = request.getAttribute(key) ?: [:]
        current.putAll(statements)
        request.setAttribute(key, current)
      }
    }

    statements
  }

  public Map<String, Iterable<AbstractCoverageStatement>> lookupCoverageOverrides (final Map resultsMap, final String agreementId = null) {
    final List<ErmResource> resources = resultsMap?.get('results')

    resources ? lookupCoverageOverrides(resources, agreementId) : [:]
  }

  public Map<String, Iterable<AbstractCoverageStatement>> lookupCoverageOverrides (final Iterable<ErmResource> resources, final String agreementId = null) {

    if (!resources || resources.size() < 1) return [:]

    // Grab the resources
    final List statementQuery = Entitlement.createCriteria().list {

      createAlias 'resource', 'ermResource'
      createAlias 'ermResource.contentItems', 'pcis', JoinType.LEFT_OUTER_JOIN
      if (agreementId) {
        eq 'owner.id', agreementId
      } else {
        isNotNull 'owner.id'
      }

      or {

        final Set<String> ids = resources.collect{ it.id }

        // Linked to package.
        'in' 'resource.id', ids

        and {
          eq 'ermResource.class', Pkg
          'in' 'pcis.id', ids
        }
      }

      projections {
        property ('id')
        property ('resource.id')
        property ('pcis.id')
      }
    }

    Entitlement ent
    final Map<String, Set<AbstractCoverageStatement>> statements = statementQuery.collectEntries {
      if (!ent || ent.id != it[0]) {
        // Change the entitlement.
        ent = Entitlement.read (it[0])
      }

      // Add the coverage from the entitlement. Call collect to create a copy of the collection.
      [ "${it[2] ?: it[1]}" : ent.coverage.collect() ]
    }

    // Add to the request (if there is one) and return.
    addToRequestIfPresent (statements)
  }

  public String nullIfBlank( final String value ) {
    return (value?.trim()?.length() ?: 0) > 0 ? value : null
  }

  // FIXME this indicates a need to refactor the beforeValidate call which forces a calculate coverage on every save
  public static void saveResourceWithoutCalculatingCoverage (ErmResource resource, boolean flush = true, boolean failOnError = true) {
    resource.doNotCalculateCoverage = true;
    resource.save(failOnError: failOnError, flush: flush)
  }

  /**
   * Set coverage from schema
   */
  public static void setCoverageFromSchema (
    final ErmResource resource,
    final Iterable<CoverageStatementSchema> coverage_statements,
    final boolean calculateCoverageAtEnd = true
  ) {
    // resource is null for logging unless a gorm operation is applied to it... something something proxying something :p
    //log.debug("CoverageService::setCoverageFromSchema(${resource}, ${coverage_statements}, ${calculateCoverageAtEnd})")

//    ErmResource.withTransaction {

      final Set<CoverageStatement> statements = []
      try {

        // Clear the existing coverage, or initialize to empty set.
        if (resource.coverage) {
          statements.addAll( resource.coverage.collect() )

          resource.coverage.clear()

          // I don't think we need to save here... On my head be it :p
          //saveResourceWithoutCalculatingCoverage(resource, false, true)
        }
        for ( CoverageStatementSchema cs : coverage_statements ) {
          /* Not using utilityService.checkValidBinding here
           * because we have custom error logging behaviour
           */
          if (cs.validate()) {
            CoverageStatement new_cs = new CoverageStatement([
              startDate   : cs.startDate,
              endDate     : cs.endDate,
              startVolume : ("${cs.startVolume}".trim() ? cs.startVolume : null),
              startIssue  : ("${cs.startIssue}".trim() ? cs.startIssue : null),
              endVolume   : ("${cs.endVolume}".trim() ? cs.endVolume : null),
              endIssue    : ("${cs.endIssue}".trim() ? cs.endIssue : null)
            ])

            resource.addToCoverage( new_cs )

            resource.doNotCalculateCoverage = true // Validate is called inside utility service here -- don't trigger calculate coverage
            // This will _already_ log out errors, add extra context around what a failure means in this case.
            if (!utilityService.checkValidBinding(resource, "Coverage statements (${coverage_statements}) invalid. Coverage for ${resource} will be reset (${statements})")) {
              throw new ValidationException('Adding coverage statement invalidates Resource', resource.errors)
            }

            saveResourceWithoutCalculatingCoverage(resource, false, false)
          } else {
            // Not valid coverage statement
            cs.errors.allErrors.each { ObjectError error ->
              /* TODO Perhaps in future we can extend checkValidBinding to this use case */
              // ERM-1932 coverage startDate nullable errors should not make it in the user's error log
              if (!(error.getArguments()[0] == 'startDate' && error.getCode() == 'nullable')) {
                log.error (messageSource.getMessage(error, LocaleContextHolder.locale))
              }
            }

            // Throwing a ValildationException here we "reset" if even one coverageStatement is wrong.
            // Without this we simply ignore the incorrect statement and try to continue...
            throw new ValidationException('Coverage statement is incorrect', cs.errors)
          }
        }
      } catch (ValidationException e) {
        // Don't bother erroring this to the user, the above validation errors will log through UtilityService
        log.debug("Coverage changes to resource ${resource} not saved. \n${e.message}")
        //e.printStackTrace() // Can turn off except for dev

        // In this case we must RESET the coverage 
        // This shouldn't need to be a log error, as the validation error above comes from somewhere which ALREADY logs as error.
        resource.coverage?.clear();

        statements.each {
          resource.addToCoverage( it )
        }
      }

      // Save and flush (Don't propogate coverage changes yet)
      // Flush is NECESSARY here so that changelistener lookups have access to all created coverageStatements
      saveResourceWithoutCalculatingCoverage(resource)

      if (calculateCoverageAtEnd) {
        // If we want this to propogate down we need to run the change listener here specifically.
        changeListener(resource);
        // Again, if changeListener ends up doing more things
        // we may need to separate out the coverage-only part
      }
//    }
  }

  /**
   * Given an PlatformTitleInstance calculate the coverage based on the higher level
   * PackageContentItem coverage values linked to this PTI
   *
   * @param pti The PlatformTitleInstance
   */
  public static void calculateCoverage( final PlatformTitleInstance pti ) {
    // log.debug 'Calculate coverage for PlatformTitleInstance {}', pti.id

      // Use a sub query to select all the coverage statements linked to PCIs,
      // linked to this pti
      List<org.olf.dataimport.erm.CoverageStatement> allCoverage = CoverageStatement.executeQuery(
        """
          SELECT cs FROM CoverageStatement cs WHERE
          cs.resource.id IN (
            SELECT pci.id FROM PackageContentItem pci WHERE
            pci.pti.id = :ptiId
          )
        """.toString(), [ptiId: pti.id]
      ).collect { CoverageStatement cs ->
        new org.olf.dataimport.erm.CoverageStatement([
          'startDate': cs.startDate,
          'endDate': cs.endDate
        ])
      }
      allCoverage = collateCoverageStatements(allCoverage)

      setCoverageFromSchema(pti, allCoverage)
  }

  /**
   * Given an TitleInstance calculate the coverage based on the higher level
   * PackageContentItem coverage values linked to this PTI
   *
   * @param ti The TitleInstance
   */
  public static void calculateCoverage( final TitleInstance ti ) {
    // Use a sub query to select all the coverage statements linked to PTIs,
    // linked to this TI
//    TitleInstance.withTransaction {

      List<org.olf.dataimport.erm.CoverageStatement> allCoverage = CoverageStatement.executeQuery(
        """
          SELECT cs FROM CoverageStatement cs WHERE
          cs.resource.id IN (
            SELECT pti.id FROM PlatformTitleInstance pti WHERE
            pti.titleInstance.id = :tiId
          )
        """.toString(), [tiId: ti.id]
      ).collect { CoverageStatement cs ->
        new org.olf.dataimport.erm.CoverageStatement([
          'startDate': cs.startDate,
          'endDate': cs.endDate
        ])
      }

      allCoverage = collateCoverageStatements(allCoverage)

      setCoverageFromSchema(ti, allCoverage)
//    }
  }

  private static int dateWithinCoverage(CoverageStatementSchema cs, LocalDate date, int defaultValue) {

    // This is ambiguous. Null date could be open start or open end. Let the calling method sort it out
    // We'll respond with null.
    if (date == null) return defaultValue

    if (cs.startDate == null && cs.endDate == null) return 0
    if (date == null) return ( cs.startDate ? -1 : (cs.endDate ? 1 : 0) )

    if (cs.endDate == null || date <= cs.endDate) {
      return ( cs.startDate == null || date >= cs.startDate ? 0 : -1 )
    }

    if (cs.startDate == null || date >= cs.startDate) {
      return ( cs.endDate == null || date <= cs.endDate ? 0 : 1 )
    }

    // If we get this far we know it outside the start/or end date
    (date > cs.endDate ? 1 : -1)
  }

  public static List<CoverageStatementSchema> collateCoverageStatements( final Iterable<CoverageStatementSchema> coverage_statements ) {

    // Define our list
    List<CoverageStatementSchema> results = []

    // Return early if we can.
    if (coverage_statements.size() < 2) {
      results.addAll(coverage_statements)
      return results
    }

    for (CoverageStatementSchema cs : coverage_statements) {
      // Use an iterator for in-place editing of the collection.
      boolean absorbed = subsume(results.listIterator(), cs)
    }

    results
  }

  private static boolean subsume (ListIterator<CoverageStatementSchema> iterator, CoverageStatementSchema statement) {
    boolean absorbed = false
    while (!absorbed && iterator.hasNext()) {
      CoverageStatementSchema current = iterator.next()

      int comparison = dateWithinCoverage(current, statement.startDate, -1)
      if (comparison == 0) {
        // Starts within current item, check end.
        comparison = dateWithinCoverage(current, statement.endDate, 1)
        if (comparison == 0) {
          // Also within. This item is already dealt with.
          // No action needed.
          absorbed = true
        } else {
          // End date beyond this statement.
          if (iterator.hasNext()) {
            // There is a next statement. We need to see if the next statement includes the end date
            // and if it does then we should remove this statement. If not, we need to adjust this statement.
            final CoverageStatementSchema next = iterator.next()

            // Move back, immediately. This takes TWO previous calls, thanks to the implementation of the iterator
            iterator.previous()
            iterator.previous()

            // There is overlap
            if (dateWithinCoverage(next, statement.endDate, 1) >= 0) {
              // Then we should remove this item and deal with it as part of the next item.
              iterator.remove()
              absorbed = subsume(iterator, statement)
            } else {
              // Lengthen this one.
              current.endDate = statement.endDate
              absorbed = true
            }
          } else {
            // Lengthen this one.
            current.endDate = statement.endDate
            absorbed = true
          }
        }

      } else if (comparison < 0) {
        // Starts before current item, check end.
        comparison = dateWithinCoverage(current, statement.endDate, 1)

        if (comparison < 0) {

          // Ends before current statement. Add.
          // Add before this current one. For that we first need to move backwards.

          // I am not quite sure why this next/previous works as intended without the "doubling up" we've measured before
          iterator.previous()

          iterator.add(statement)
          iterator.next() // Sets the pointer internally back to the correct position.
          absorbed = true
        } else if (comparison == 0) {
          // Within current. Just increase startdate
          current.startDate = statement.startDate
          absorbed = true

        } else {
          if (iterator.hasNext()) {
            // There is a next statement. We need to see if the next statement includes the end date
            // and if it does then we should remove this statement. If not, we need to adjust this statement.
            final CoverageStatementSchema next = iterator.next()

            // Move back, immediately. This takes TWO previous calls, thanks to the implementation of the iterator
            iterator.previous()
            iterator.previous()

            // There is overlap
            if (dateWithinCoverage(next, statement.endDate, 1) >= 0) {
              // Then we should remove this item and deal with it as part of the next item.
              iterator.remove()
              absorbed = subsume(iterator, statement)
            } else {
              // Lengthen this one.
              current.endDate = statement.endDate
              current.startDate = statement.startDate
              absorbed = true
            }

          } else {
            // Lengthen this one.
            current.endDate = statement.endDate
            current.startDate = statement.startDate
            absorbed = true
          }
        }
      } else {
        // Starts after the current statement.
        // We don't need to do anything as the method will
        // just move on and check the next statement.
      }
    }

    // If we get this far and absorbed is false then we should just add the item.
    if (!absorbed) {
      iterator.add(statement)
    }

    absorbed
  }

  private static PackageContentItem asPCI (ErmResource res) {
    res instanceof PackageContentItem ? res : null
  }

  private static PlatformTitleInstance asPTI (ErmResource res) {
    res instanceof PlatformTitleInstance ? res : null
  }

  private static TitleInstance asTI (ErmResource res) {
    res instanceof TitleInstance ? res : null
  }


  // NOTE -- this is slightly misnamed. As of right now this is _only_ a change coverage listener.
  // This means this whole method is not triggered when doNotCalculate coverage transient is
  // set on the resource
  public static void changeListener(ErmResource res) {

    final PackageContentItem pci = asPCI(res)
    if ( pci ) {
      log.trace "PCI updated, regenerate PTI's coverage"
      //log.debug "PCI updated, regenerate PTI's coverage"
      calculateCoverage( pci.pti )
    }

    final PlatformTitleInstance pti = asPTI(res)
    if ( pti ) {
      log.trace "PTI updated regenerate TI's coverage"
      //log.debug "PTI updated regenerate TI's coverage"
      calculateCoverage( pti.titleInstance )
    }

    final TitleInstance ti = asTI(res)
    if ( ti ) {
      log.trace 'TI updated'
      //log.debug("TI updated")
    }
  }

  private static final Version COVERAGE_IMPROVEMENTS_VERSION = Version.forIntegers(2,3) // Version trigger.

  @Subscriber('okapi:tenant_enabled')
  public void onTenantEnabled (final String tenantId, final boolean existing_tenant, final boolean upgrading, final String toVersion, final String fromVersion) {
    if (upgrading && fromVersion) {
      try {
        if (Version.valueOf(fromVersion).compareTo(COVERAGE_IMPROVEMENTS_VERSION) < 0) {
          // We are upgrading from a version prior to when the coverage changes were introduced,
          // lets schedule a job to retrospectively alter the coverage statements
          log.debug "Regenerate coverage based on tenant upgrade prior to improvements being present"
          triggerRegenrationForTenant(tenantId)
        }
      } catch(ParseException pex) {
        // From version couldn't be parsed as semver we should ignore.
        log.debug "${fromVersion} could not be parsed as semver not running Coverage Regeneration."
      }
    }
  }

  @Subscriber('okapi:tenant_regen_coverage')
  public void onTenantRegenCoverage(final String tenantId, final String value, final String existing_tenant, final String upgrading, final String toVersion, final String fromVersion) {
    log.debug "Regenerate coverage based on explicit request during tenant activation"
    triggerRegenrationForTenant(tenantId)
  }

  private void triggerRegenrationForTenant(final String tenantId) {
    final String tenant_schema_id = OkapiTenantResolver.getTenantSchemaName(tenantId)
    Tenants.withId(tenant_schema_id) {

      CoverageRegenerationJob job = CoverageRegenerationJob.findByStatusInList([
        CoverageRegenerationJob.lookupStatus('Queued'),
        CoverageRegenerationJob.lookupStatus('In progress')
      ])

      if (!job) {
        job = new CoverageRegenerationJob(name: "Coverage Regeneration ${Instant.now()}")
        job.setStatusFromString('Queued')
        job.save(failOnError: true, flush: true)
      } else {
        log.debug('Regeneration job already running or scheduled. Ignore.')
      }
    }
  }

  public void triggerRegenration () {
    // Select all PTIs and regenerate coverage for them.
    final int batchSize = 100

    int count = 0
    List<PlatformTitleInstance> ptis = PlatformTitleInstance.createCriteria().list ([max: batchSize, offset: batchSize * count]) {
//      eq 'titleInstance.id', '632bc9df-8e01-4d7b-8e91-3107dbc3b43c'
      order 'id'
    }
    while (ptis && ptis.size() > 0) {
      count ++
      ptis.each { final PlatformTitleInstance pti ->

        PlatformTitleInstance.withNewTransaction {
          log.trace "Recalculating coverage for PTI ${pti.id}"
          calculateCoverage( pti )
        }
      }

      // Next page...
      ptis = PlatformTitleInstance.createCriteria().list ([max: batchSize, offset: batchSize * count]) {
        order 'id'
      }
//      ptis = null
    }
  }

}

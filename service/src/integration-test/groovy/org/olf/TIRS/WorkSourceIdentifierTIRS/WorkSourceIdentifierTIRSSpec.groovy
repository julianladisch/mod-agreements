package org.olf.TIRS.WorkSourceIdentifier

import org.olf.TIRS.TIRSSpec

import org.olf.dataimport.internal.TitleInstanceResolverService
import org.olf.dataimport.internal.titleInstanceResolvers.WorkSourceIdentifierTIRSImpl
//import org.olf.dataimport.internal.titleInstanceResolvers.IdFirstTIRSImpl

import org.olf.dataimport.internal.titleInstanceResolvers.TIRSException

import org.springframework.context.annotation.Bean
import org.springframework.core.io.Resource

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.kb.RemoteKB
import org.olf.kb.Identifier
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.TitleInstance
import org.olf.kb.ErmTitleList
import org.olf.kb.Work

import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.transactions.Transactional
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import groovy.transform.CompileStatic

import java.time.Instant

import spock.lang.*

import groovy.json.JsonOutput

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
@Transactional
class WorkSourceIdentifierTIRSSpec extends TIRSSpec {
  def cleanup() {
    /* We need to ensure that the fallbackToIdFirstTIRSCache is
     * cleared after every test so we can check the fallback methods
     * work as expected. Realistically at least one of these tests will
     * leave a Work without SourceId though, so probably doesn't
     * strictly _need_ to be run after every test, but hey ho.
     */
    titleInstanceResolverService.fallbackToIdFirstTIRSTimestamp = Instant.EPOCH
  }

  @Shared PackageContentImpl brainOfTheFirm

  @Shared
  String resource_path = "${base_resource_path}/workSourceTIRS"

  @Shared
  String citation_path = "${resource_path}/citations"

  // Helper to avoid having to fill out package location every time
  @Ignore
  Map importPackageTest(String package_name) {
    return importPackageFromFileViaService(package_name, resource_path)
  }

  @Ignore
  PackageContentImpl citationFromFile(String citation_file_name) {
    return bindMapToCitationFromFile(citation_file_name, citation_path)
  }


  // Assumes you're already in a Tenant context
  @Ignore
  void deleteWorkSourceIdentifier(String sourceIdentifierValue) {
    Work work = getWorkFromSourceId(sourceIdentifierValue);
    IdentifierOccurrence io = IdentifierOccurrence.executeQuery("""
      SELECT io FROM IdentifierOccurrence AS io
      WHERE io.resource.id = :workId
    """.toString(), [workId: work.id])[0]

    IdentifierOccurrence.executeUpdate("""
      DELETE FROM IdentifierOccurrence AS io
        WHERE io.id = :ioId
    """.toString(), [ioId: io.id])
  }

  // Helper function forgetting original data for later comparison
@Ignore
  Map getOriginalData(String sourceIdentifierValue) {
    List<TitleInstance> tis = getFullTIsForWork(getWorkFromSourceId(sourceIdentifierValue).id);
    TitleInstance electronicTi = tis.find(ti -> ti.subType.value == 'electronic');
    TitleInstance printTi = tis.find(ti -> ti.subType.value == 'print');

    Set<IdentifierOccurrence> originalIdentifiers = electronicTi.identifiers;
    Set<IdentifierOccurrence> originalPrintIdentifiers = printTi?.identifiers ?: [];

    return (
      [
        tis: tis,
        electronicTi: electronicTi,
        printTi: printTi,
        originalIdentifiers: originalIdentifiers,
        originalPrintIdentifiers: originalPrintIdentifiers
      ]
    )
  }

  // Helper function for resolving a title instance and obtaining information about it for comparison
  @Ignore
  Map resolveTIAndReturnNewData(String citation_file_name) {
    String resolvedWorkSourceId;
    String resolvedTiId = titleInstanceResolverService.resolve(citationFromFile(citation_file_name), true);
    TitleInstance resolvedTi = TitleInstance.get(resolvedTiId);
    resolvedWorkSourceId = resolvedTi.work.sourceIdentifier.identifier.value

    Set<TitleInstance> relatedTitles = resolvedTi.relatedTitles;
    TitleInstance resolvedPrintSibling = resolvedTi.relatedTitles?.getAt(0)

    Set<IdentifierOccurrence> resolvedIdentifiers = resolvedTi.identifiers
    Set<IdentifierOccurrence> resolvedApprovedIdentifiers = resolvedTi.approvedIdentifierOccurrences


    return (
      [
        resolvedTi: resolvedTi,
        relatedTitles: relatedTitles,
        resolvedPrintSibling: resolvedPrintSibling,
        resolvedIdentifiers: resolvedIdentifiers,
        resolvedApprovedIdentifiers: resolvedApprovedIdentifiers,
        resolvedWorkSourceId: resolvedWorkSourceId,
      ]
    )
  }


  void 'Bind to content' () {
    when: 'Attempt the bind'
      brainOfTheFirm = citationFromFile('brain_of_the_firm.json')    
    then: 'Everything is good'
      noExceptionThrown()
  }

  // Test directly (but only if titleInstanceResolverService is as expected)
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test title creation' () {
    when: 'WorkSourceIdentifierTIRS is passed a title citation'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        titleInstanceResolverService.resolve(brainOfTheFirm, true);
      }
    then: 'No exceptions'
      noExceptionThrown()
  }

  // Transaction needs to be different for subsequent lookup via API...
  // this won't be an issue in production systems
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test title creation -- lookup' () {
    when: 'We fetch titles matching \'Brain of the firm\''
      def tiGet = doGet("/erm/titles", [filters: ['name==Brain of the firm'], stats: true]);
    then: 'We get the expected TIs'
      assert tiGet.total == 2 // One print, one electronic
    when: 'We inspect electronic and print tis'
      def electronicTI = tiGet.results?.find {ti -> ti.subType.value == 'electronic'}
      def printTI = tiGet.results?.find {ti -> ti.subType.value == 'print'}
    then: 'We have expected results'
      assert electronicTI != null;
      assert printTI != null;

      assert electronicTI.identifiers.size() == 2;
      assert printTI.identifiers.size() == 1;
  }


  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test rejection without sourceIdentifier fields' () {
    when: 'WorkSourceIdentifierTIRS is passed a title citation without sourceIdentifierNamespace'
      brainOfTheFirm.sourceIdentifierNamespace = null;

      Long code
      String message
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          String tiId = titleInstanceResolverService.resolve(brainOfTheFirm, true);
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'We got an expected error'
      assert code == TIRSException.MISSING_MANDATORY_FIELD
      assert message == 'Missing source identifier namespace'
    when: 'WorkSourceIdentifierTIRS is passed a title citation without sourceIdentifier'
      brainOfTheFirm.sourceIdentifier = null;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          String tiId = titleInstanceResolverService.resolve(brainOfTheFirm, true);
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    
    then: 'We got an expected error'
      assert code == TIRSException.MISSING_MANDATORY_FIELD
      assert message == 'Missing source identifier'
    cleanup:
      brainOfTheFirm.sourceIdentifierNamespace = 'k-int';
      brainOfTheFirm.sourceIdentifier = 'botf-123';
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Ingest via package service works as expected' () {
    when: 'We ingest wsitirs_pkg'
      Map result = importPackageTest('wsitirs_pkg.json')

    then: 'Package imported'
      result.packageImported == true
  }

  // Transaction needs to be different for subsequent lookup via API...
  // this won't be an issue in production systems
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Ingest via package service works as expected -- lookup' () {
    when: "Looked up package with name"
      List resp = doGet("/erm/packages", [filters: ['name==Work Source TIRS Package']])

    then: "Package found"
      resp.size() == 1
      resp[0].id != null
    when: "Looking up the number of TIs in the system"
      // Ignore the tis from the first test
      def tiGet = doGet("/erm/titles", [filters: ['name!=Brain of the firm'], stats: true]);
    then: "We have the expected number"
      assert tiGet.total == 20
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when matching multiple works' () {
    when: 'We create a work that duplicates one already in the system'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        // Grab existing identifier
        Identifier identifier = Identifier.executeQuery("""
          SELECT iden from Identifier as iden
            WHERE iden.value = :value and iden.ns.value = :ns
          """.toString(),
          [value:'aaa-001', ns:'k-int']
        )[0]

        IdentifierOccurrence sourceIdentifier = new IdentifierOccurrence([
          identifier: identifier,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ])

        Work duplicateWork = new Work([
          title: 'Duplicate work',
          sourceIdentifier: sourceIdentifier
        ]).save(failOnError: true, flush: true)
      }
    then: 'Everything saved as expected'
      noExceptionThrown()
    when: 'Looking up works for this sourceIdentifier' // There is no endpoint
      Integer workCount;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        workCount = Work.executeQuery("""
          SELECT COUNT(work.id) from Work as work
            WHERE work.sourceIdentifier.identifier.value = :value
          """.toString(),
          [value:'aaa-001']
        )[0]
      }
    then: 'We see two works'
      assert workCount == 2
    when: 'WorkSourceIdentifierTIRS attempts to match on this duplicated work'
      Long code
      String message
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          titleInstanceResolverService.resolve(citationFromFile('multiple_work_match.json'), true)
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'We get the expected error'
      assert message == 'Matched 2 with source identifier K-Int:aaa-001'
      assert code == TIRSException.MULTIPLE_WORK_MATCHES
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when matching work with no electronic TIs' () {
    when: 'We remove the title instances set up for a work'
      Work work;
      Integer tiCount;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        // Grab existing work
        work = getWorkFromSourceId('aab-002')

        // Delete TIs
        deleteTIsFromWork(work.id)

        tiCount = TitleInstance.executeQuery("""
          SELECT COUNT(ti.id) from TitleInstance as ti
            WHERE ti.work.id = :workId
          """.toString(),
          [workId: work.id]
        )[0]
      }
    then: 'There are no TIs with works for this one.'
      assert tiCount == 0;
    when: 'We ingest a new electronic title for this work'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        titleInstanceResolverService.resolve(citationFromFile('one_work_match_zero_electronic_ti_match.json'), true)
      }
    then: 'It ingests without error'
      noExceptionThrown()
    when: 'We look up titles for this work'
      // As seen above, API lookups need to occur in a separate transaction. Instead fetch direct from DB
      // We will continue this theme below -- we don't need to test the API in _this_ test suite
      List<TitleInstance> tis = [];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        tis = TitleInstance.executeQuery("""
          SELECT ti FROM TitleInstance AS ti
          WHERE ti.work.id = :workId
        """.toString(), [workId: work.id])
      }
    then: 'We get the expected number of TIs'
      assert tis.size() == 2 // One print, one electronic
    when: 'We inspect electronic and print tis'
      def electronicTI = tis.find {ti -> ti.subType.value == 'electronic'}
      def printTI = tis.find {ti -> ti.subType.value == 'print'}
    then: 'We have an electronic and a print TI on the work with expected identifiers'
      assert electronicTI != null;
      assert printTI != null;

      assert electronicTI.name == 'One Work Match-Zero Electronic TI Match - NEW';
      assert printTI.name == 'One Work Match-Zero Electronic TI Match - NEW'

      assert electronicTI.identifiers.any { id -> id.identifier.value == '2345-6789-x'};
      assert printTI.identifiers.any { id -> id.identifier.value == 'bcde-fghi-x'};
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when matching work with many electronic TIs' () {
    when: 'We add a new electronic title instance for a work'
      Work work;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId('aac-003')
        TitleInstance newTI = new TitleInstance([
          name: 'Secondary Title Instance For Work aac-003',
          type: TitleInstance.lookupType('Serial'),
          subType: TitleInstance.lookupSubType('Electronic'),
          work: work
        ]).save(flush:true, failOnError: true);
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We get electronic title instances for this work'
      List<TitleInstance> electronicTIs;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId('aac-003')
        electronicTIs = TitleInstance.executeQuery("""
          SELECT ti FROM TitleInstance ti
            WHERE ti.subType.value = 'electronic' AND
                  ti.work.id = :workId
        """.toString(), [workId: work.id])
      }
    then: 'We see multiple TIs'
      assert electronicTIs.size() > 1
    when: 'WorkSourceIdentifierTIRS attempts to match on this work'
      Long code
      String message
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        try {
          titleInstanceResolverService.resolve(citationFromFile('one_work_match_many_electronic_ti_match.json'), true)
        } catch (TIRSException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'We get the expected error'
      assert message == "Multiple (2) electronic title instances found on Work: ${work.id}, skipping"
      assert code == TIRSException.MULTIPLE_TITLE_MATCHES
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when matching work with one electronic TI' () {
    given: 'We have a single electronic TI in the system for a given work'
      Work work;
      String tiId
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId('aad-004')
        def tisForWork = getTIsForWork(work.id, 'electronic')
        assert tisForWork.size() == 1; // Should only be one TI for this work
        tiId = tisForWork[0];
      }
    when: 'We resolve a title for this work'
      String resolvedTiId;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedTiId = titleInstanceResolverService.resolve(citationFromFile('one_work_match_one_electronic_ti_match.json'), true)
      }
    then: 'We resolve to the same title'
      assert tiId == resolvedTiId
    when: 'We inspect the title'
      TitleInstance theTi;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        theTi = TitleInstance.get(tiId);
      }
    then: 'It has been updated as part of the resolve'
      // We will check identifier and sibling match resolves LATER
      assert theTi.name == "One Work Match-One Electronic TI Match (RENAMED)"
  }

  // Set up case where a work does NOT have a sourceId, and there are multiple titles which are matchable from eissn
  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when no work is found and we match multiple TIs' () {
    when: 'We delete a work sourceId'
      String workSourceId = 'aae-005';
      String eissn = '5678-9012'
      String issn = 'efgh-ijkl'
      Work work;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        deleteWorkSourceIdentifier(workSourceId)

        // Attempt to grab work from sourceId
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'Work is no longer findable from sourceId'
      assert work == null
    when: 'We set up secondary TI that IdFirst fallback will find'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        Identifier eissnId = Identifier.executeQuery("""
          SELECT iden FROM Identifier iden
          WHERE iden.value = :eissn
        """. toString(), [eissn: eissn])[0];

        TitleInstance newTI = new TitleInstance([
          name: "Secondary Title Instance with eissn ${eissn}",
          type: TitleInstance.lookupType('Serial'),
          subType: TitleInstance.lookupSubType('Electronic'),
        ]);

        IdentifierOccurrence newIo = new IdentifierOccurrence([
          identifier: eissnId,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ]).save(failOnError: true);

        newTI.addToIdentifiers(newIo);
        newTI.save(failOnError: true, flush: true)
      }
    then: 'All good'
      noExceptionThrown()
    when: "We count IdentifierOccurrences for the eissn ${eissn}"
      Integer ioCount;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        ioCount = IdentifierOccurrence.executeQuery("""
          SELECT COUNT(iden.id) FROM IdentifierOccurrence AS iden
          WHERE iden.identifier.value = :eissn
        """.toString(), [eissn: eissn])[0]
      }
    then: 'We see 2 IdentifierOccurrences, one for each of the TIs in the system'
      assert ioCount == 2;
    when: 'We fetch the existing TIs and Siblings'
      List<String> existingTiIds;
      List<String> existingSiblingIds;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        existingTiIds = IdentifierOccurrence.executeQuery("""
          SELECT io.resource.id FROM IdentifierOccurrence io
          WHERE io.identifier.value = :eissn
        """.toString(), [eissn: eissn])

        existingSiblingIds = IdentifierOccurrence.executeQuery("""
          SELECT io.resource.id FROM IdentifierOccurrence io
          WHERE io.identifier.value = :issn
        """.toString(), [issn: issn])  
      }
    then: 'We see the state we expect'
      assert existingTiIds.size() == 2; // One from regular ingest and one multiple added in setup for this test
      assert existingSiblingIds.size() == 1;
    
    when: 'We resolve a title with non-matching sourceId and there are multiple matches'
      String resolvedTiId;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedTiId = titleInstanceResolverService.resolve(citationFromFile('zero_work_match_multiple_electronic_ti_match.json'), true)
      }
    then: 'There are no exceptions thrown'
      noExceptionThrown()
    when: 'We get the resolved TI'
      TitleInstance resolvedTi
      Set<TitleInstance> relatedTitles;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedTi = TitleInstance.get(resolvedTiId);
        relatedTitles = resolvedTi.getRelatedTitles();
      }
    then: 'We have created whole new TI and siblings'
      assert !existingTiIds.contains(resolvedTi.id);
      relatedTitles.each { rt ->
        assert !existingSiblingIds.contains(rt.id);
      }

      assert resolvedTi.name == "Brand new title matching eissn 5678-9012"
    when: 'We double check that the numbers now line up'
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        existingTiIds = IdentifierOccurrence.executeQuery("""
          SELECT io.resource.id FROM IdentifierOccurrence io
          WHERE io.identifier.value = :eissn
        """.toString(), [eissn: eissn])

        existingSiblingIds = IdentifierOccurrence.executeQuery("""
          SELECT io.resource.id FROM IdentifierOccurrence io
          WHERE io.identifier.value = :issn
        """.toString(), [issn: issn])  
      }
    then: 'We get the expected values'
      assert existingTiIds.size() == 3; // One new TI
      assert existingSiblingIds.size() == 2; // One new sibling
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Creation of new TI within fallbackToIdFirstTIRS' () {
    when: 'We set up a work that does not have a sourceIdentifier'
      String originalTiId;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        originalTiId = titleInstanceResolverService.resolve(citationFromFile('test_title.json'), true);
        deleteWorkSourceIdentifier('tt-123-abc');
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We check number of Works in system without sourceIdentifiers'
      Integer worksWithoutSourceIds;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        worksWithoutSourceIds = Work.executeQuery("""
          SELECT COUNT(w) FROM Work w
          LEFT JOIN IdentifierOccurrence io ON io.resource = w.id
          WHERE io.id = null
        """.toString())[0]
      }
    then: 'At least one such Work exists'
      assert worksWithoutSourceIds > 0
    when: 'We resolve a brand new title'
      String tiId;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        tiId = titleInstanceResolverService.resolve(citationFromFile('test_title2.json'), true);
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We lookup the new title'
      TitleInstance newTI;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        newTI = TitleInstance.get(tiId);
      }
    then: 'It is a brand new title created by IdFirstTIRS'
      // There is no way to be certain this fell back to IdFirstTIRS in code
      // -- hence the checks above that should ensure that it did when coupled
      // with cleanup enforcing the fallbackToIdFirstTIRS check on each test
      assert originalTiId != tiId
      assert newTI.name == 'TestTitle but this one should not be able to match via fuzzy title match';
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback -- Match on identifiers' () {
    when: 'We remove work source id'
      String workSourceId = 'aaf-006'; // Making sure this is the same throughout the test
      Map originalData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        originalData = getOriginalData(workSourceId);
        deleteWorkSourceIdentifier(workSourceId);
      }
    then: 'We have the expected TIs and an originalTiId'
      assert originalData.tis.size() == 1
      assert originalData.printTi == null;
      assert originalData.electronicTi.name == 'Zero Work Match-Single TI Out (A -- Match on identifiers)'
      assert originalData.electronicTi.id != null;
    when: 'We attempt to lookup the work by sourceId'
      Work work
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
    when: 'We resolve what should match on identifiers in IdFirstTIRS fallback'
      Map resolvedData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedData = resolveTIAndReturnNewData('match_on_identifiers.json');
      }
    then: 'We have matched to the expected title and it has updated basic metadata'
      // SAVE wibling/id wrangling for later
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id;
      assert resolvedData.resolvedTi.name == 'TITLE MATCH on identifier eissn:6789-0123-A'

      // Work sourceId has been reset
      assert resolvedData.resolvedWorkSourceId == workSourceId;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback -- Match on sibling' () {
    when: 'We remove work source id'
      String workSourceId = 'aag-007'; // Making sure this is the same throughout the test

      Map originalData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        originalData = getOriginalData(workSourceId);
        deleteWorkSourceIdentifier(workSourceId);
      }
    then: 'We have the expected TIs and an originalTiId'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Zero Work Match-Single TI Out (B -- Match on sibling)'
      assert originalData.printTi != null
      assert originalData.printTi.name == 'Zero Work Match-Single TI Out (B -- Match on sibling)'
  
      assert originalData.electronicTi.id != null;
      assert originalData.printTi.id != null
    when: 'We attempt to lookup the work by sourceId'
      Work work
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
    when: 'We resolve what should match on sibling in IdFirstTIRS fallback'
      Map resolvedData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedData = resolveTIAndReturnNewData('match_on_sibling.json');
      }
    then: 'We have matched to the expected title and it has updated basic metadata'
      // SAVE wibling/id wrangling for later (although we can check it's the same sibling)
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id;
      assert resolvedData.relatedTitles.size() == 1;
      assert originalData.printTi.id == resolvedData.resolvedPrintSibling.id;

      assert resolvedData.resolvedTi.name == 'TITLE MATCH on sibling identifier issn:fghi-jklm-B'
      assert resolvedData.resolvedPrintSibling.name == 'TITLE MATCH on sibling identifier issn:fghi-jklm-B'

      // Work sourceId has been reset
      assert resolvedData.resolvedWorkSourceId == workSourceId;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback -- Match on fuzzy title' () {
    when: 'We remove work source id'
      String workSourceId = 'aah-008'; // Making sure this is the same throughout the test
      Map originalData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        originalData = getOriginalData(workSourceId);
        deleteWorkSourceIdentifier(workSourceId);
      }
    then: 'We have the expected TIs and an originalTiId'
      assert originalData.tis.size() == 1
      assert originalData.electronicTi.name == 'Totally unique title (C -- Match on title)'

      assert originalData.originalIdentifiers.size() == 1;
      assert originalData.originalIdentifiers.find( io -> io.identifier.value == '6789-0123-C-1') != null;

      assert originalData.electronicTi.id != null;
    when: 'We attempt to lookup the work by sourceId'
      Work work
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
    when: 'We resolve what should match on fuzzy title in IdFirstTIRS fallback'
      Map resolvedData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedData = resolveTIAndReturnNewData('match_on_fuzzy_title.json');
      }
    then: 'We have matched to the expected title and it has updated basic metadata'
      // SAVE wibling/id wrangling for later -- but we can double check identifiers have changed
      // (IMPORTANT we only have non-class-one identifiers for it to fall back to fuzzy title match)
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id;
      assert resolvedData.resolvedTi.name == 'Totally unique title (C -- Match on title)' // Name will remain the same since we matched on it

      // Sneak peek at identifier wrangling
      assert resolvedData.resolvedApprovedIdentifiers.size() == 1;
      assert resolvedData.resolvedApprovedIdentifiers.find( io -> io.identifier.value == '6789-0123-C-1') == null;
      assert resolvedData.resolvedApprovedIdentifiers.find( io -> io.identifier.value == '6789-0123-C-2') != null;

      assert resolvedData.resolvedIdentifiers.size() == 2 // One approved one error
      assert resolvedData.resolvedIdentifiers.findAll { ri -> ri.status.value == 'approved' }.size() == 1
      assert resolvedData.resolvedIdentifiers.findAll { ri -> ri.status.value == 'error' }.size() == 1

      // Work sourceId has been reset
      assert resolvedData.resolvedWorkSourceId == workSourceId;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback with no sourceId attached' () {
    when: 'We remove work source id'
      String workSourceId = 'aba-010'; // Making sure this is the same throughout the test
      Map originalData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        originalData = getOriginalData(workSourceId);
        deleteWorkSourceIdentifier(workSourceId);
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Zero Work Match-Single TI Out-No Source Id'
    when: 'We attempt to lookup the work by sourceId'
      Work work
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
     when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedData = resolveTIAndReturnNewData('zero_work_match_single_ti_match_no_source_id.json');
      }
    then: 'We have matched to the expected title and it has set the work source id'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'NEW TITLE FOR Zero Work Match-Single TI Out-No Source Id'
      assert resolvedData.resolvedWorkSourceId == workSourceId
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback with mismatched sourceId attached' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'aca-020'; // Making sure this is the same throughout the test
      Map originalData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        originalData = getOriginalData(workSourceId);
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Zero Work Match-Single TI Out-Mismatch Source Id'
     when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        resolvedData = resolveTIAndReturnNewData('zero_work_match_single_ti_match_mismatched_source_id copy.json');
      }
    then: 'We have matched to the expected title, but since work source id was mismatched we have created a new title instance'
      assert originalData.electronicTi.id != resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'NEWLY UPDATED TITLE FOR Zero Work Match-Single TI Out-Mismatch Source Id'

      assert resolvedData.resolvedWorkSourceId == 'aca-020-XX'
  }
}

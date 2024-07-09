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
        relatedTitles: electronicTi.relatedTitles,
        printTi: printTi,
        originalIdentifiers: originalIdentifiers,
        originalPrintIdentifiers: originalPrintIdentifiers
      ]
    )
  }

  // Helper function for resolving a title instance and obtaining information about it for comparison
  @Ignore
  Map resolveTIAndReturnNewData(String citation_file_name) {
    String resolvedTiId = titleInstanceResolverService.resolve(citationFromFile(citation_file_name), true);
    TitleInstance resolvedTi = TitleInstance.get(resolvedTiId);
    String workSourceId = resolvedTi.work.sourceIdentifier.identifier.value

    Set<TitleInstance> relatedTitles = resolvedTi.relatedTitles;
    // This only really works in cases with one print sibling
    TitleInstance printSibling = resolvedTi.relatedTitles?.getAt(0)

    Set<IdentifierOccurrence> identifiers = resolvedTi.identifiers
    Set<IdentifierOccurrence> approvedIdentifiers = resolvedTi.approvedIdentifierOccurrences
    Set<IdentifierOccurrence> nonApprovedIdentifiers = identifiers.findAll { ri -> ri.status.value == 'error' }
    
    Set<IdentifierOccurrence> siblingIdentifiers = printSibling?.identifiers ?: []
    Set<IdentifierOccurrence> approvedSiblingIdentifiers = printSibling?.approvedIdentifierOccurrences ?: []
    Set<IdentifierOccurrence> nonApprovedSiblingIdentifiers = printSibling?.identifiers?.findAll { ri -> ri.status.value == 'error' } ?: []


    return (
      [
        resolvedTi: resolvedTi,
        relatedTitles: relatedTitles,
        printSibling: printSibling,
        identifiers: identifiers,
        approvedIdentifiers: approvedIdentifiers,
        nonApprovedIdentifiers: nonApprovedIdentifiers,
        siblingIdentifiers: siblingIdentifiers,
        approvedSiblingIdentifiers: approvedSiblingIdentifiers,
        nonApprovedSiblingIdentifiers: nonApprovedSiblingIdentifiers,
        workSourceId: workSourceId,
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
      withTenant {
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
      withTenant {
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
      withTenant {
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
      assert tiGet.total == 29
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when matching multiple works' () {
    when: 'We create a work that duplicates one already in the system'
      withTenant {
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
      withTenant {
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
      withTenant {
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
      withTenant {
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
      withTenant {
        titleInstanceResolverService.resolve(citationFromFile('one_work_match_zero_electronic_ti_match.json'), true)
      }
    then: 'It ingests without error'
      noExceptionThrown()
    when: 'We look up titles for this work'
      // As seen above, API lookups need to occur in a separate transaction. Instead fetch direct from DB
      // We will continue this theme below -- we don't need to test the API in _this_ test suite
      List<TitleInstance> tis = [];
      withTenant {
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
      withTenant {
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
      withTenant {
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
      withTenant {
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
      withTenant {
        work = getWorkFromSourceId('aad-004')
        def tisForWork = getTIsForWork(work.id, 'electronic')
        assert tisForWork.size() == 1; // Should only be one TI for this work
        tiId = tisForWork[0];
      }
    when: 'We resolve a title for this work'
      String resolvedTiId;
      withTenant {
        resolvedTiId = titleInstanceResolverService.resolve(citationFromFile('one_work_match_one_electronic_ti_match.json'), true)
      }
    then: 'We resolve to the same title'
      assert tiId == resolvedTiId
    when: 'We inspect the title'
      TitleInstance theTi;
      withTenant {
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
      withTenant {
        deleteWorkSourceIdentifier(workSourceId)

        // Attempt to grab work from sourceId
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'Work is no longer findable from sourceId'
      assert work == null
    when: 'We set up secondary TI that IdFirst fallback will find'
      withTenant {
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
      withTenant {
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
      withTenant {
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
      withTenant {
        resolvedTiId = titleInstanceResolverService.resolve(citationFromFile('zero_work_match_multiple_electronic_ti_match.json'), true)
      }
    then: 'There are no exceptions thrown'
      noExceptionThrown()
    when: 'We get the resolved TI'
      TitleInstance resolvedTi
      Set<TitleInstance> relatedTitles;
      withTenant {
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
      withTenant {
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
      withTenant {
        originalTiId = titleInstanceResolverService.resolve(citationFromFile('test_title.json'), true);
        deleteWorkSourceIdentifier('tt-123-abc');
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We check number of Works in system without sourceIdentifiers'
      Integer worksWithoutSourceIds;
      withTenant {
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
      withTenant {
        tiId = titleInstanceResolverService.resolve(citationFromFile('test_title2.json'), true);
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We lookup the new title'
      TitleInstance newTI;
      withTenant {
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
      withTenant {
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
      withTenant {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
    when: 'We resolve what should match on identifiers in IdFirstTIRS fallback'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('match_on_identifiers.json');
      }
    then: 'We have matched to the expected title and it has updated basic metadata'
      // SAVE wibling/id wrangling for later
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id;
      assert resolvedData.resolvedTi.name == 'TITLE MATCH on identifier eissn:6789-0123-A'

      // Work sourceId has been reset
      assert resolvedData.workSourceId == workSourceId;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback -- Match on sibling' () {
    when: 'We remove work source id'
      String workSourceId = 'aag-007'; // Making sure this is the same throughout the test

      Map originalData = [:];
      withTenant {
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
      withTenant {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
    when: 'We resolve what should match on sibling in IdFirstTIRS fallback'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('match_on_sibling.json');
      }
    then: 'We have matched to the expected title and it has updated basic metadata'
      // SAVE wibling/id wrangling for later (although we can check it's the same sibling)
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id;
      assert resolvedData.relatedTitles.size() == 1;
      assert originalData.printTi.id == resolvedData.printSibling.id;

      assert resolvedData.resolvedTi.name == 'TITLE MATCH on sibling identifier issn:fghi-jklm-B'
      assert resolvedData.printSibling.name == 'TITLE MATCH on sibling identifier issn:fghi-jklm-B'

      // Work sourceId has been reset
      assert resolvedData.workSourceId == workSourceId;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback -- Match on fuzzy title' () {
    when: 'We remove work source id'
      String workSourceId = 'aah-008'; // Making sure this is the same throughout the test
      Map originalData = [:];
      withTenant {
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
      withTenant {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
    when: 'We resolve what should match on fuzzy title in IdFirstTIRS fallback'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('match_on_fuzzy_title.json');
      }
    then: 'We have matched to the expected title and it has updated basic metadata'
      // SAVE wibling/id wrangling for later -- but we can double check identifiers have changed
      // (IMPORTANT we only have non-class-one identifiers for it to fall back to fuzzy title match)
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id;
      assert resolvedData.resolvedTi.name == 'Totally unique title (C -- Match on title)' // Name will remain the same since we matched on it

      // Sneak peek at identifier wrangling
      assert resolvedData.approvedIdentifiers.size() == 1;
      assert resolvedData.approvedIdentifiers.find( io -> io.identifier.value == '6789-0123-C-1') == null;
      assert resolvedData.approvedIdentifiers.find( io -> io.identifier.value == '6789-0123-C-2') != null;

      assert resolvedData.identifiers.size() == 2 // One approved one error
      assert resolvedData.identifiers.findAll { ri -> ri.status.value == 'approved' }.size() == 1
      assert resolvedData.identifiers.findAll { ri -> ri.status.value == 'error' }.size() == 1

      // Work sourceId has been reset
      assert resolvedData.workSourceId == workSourceId;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback with no sourceId attached' () {
    when: 'We remove work source id'
      String workSourceId = 'aba-010'; // Making sure this is the same throughout the test
      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);
        deleteWorkSourceIdentifier(workSourceId);
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Zero Work Match-Single TI Out-No Source Id'
    when: 'We attempt to lookup the work by sourceId'
      Work work
      withTenant {
        work = getWorkFromSourceId(workSourceId)
      }
    then: 'We cannot find it'
      assert work == null;
     when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('zero_work_match_single_ti_match_no_source_id.json');
      }
    then: 'We have matched to the expected title and it has set the work source id'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'NEW TITLE FOR Zero Work Match-Single TI Out-No Source Id'
      assert resolvedData.workSourceId == workSourceId
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Zero work match, single TI match on fallback with mismatched sourceId attached' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'aca-020'; // Making sure this is the same throughout the test
      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Zero Work Match-Single TI Out-Mismatch Source Id'
     when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('zero_work_match_single_ti_match_mismatched_source_id.json');
      }
    then: 'We have matched to the expected title, but since work source id was mismatched we have created a new title instance'
      assert originalData.electronicTi.id != resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'NEWLY UPDATED TITLE FOR Zero Work Match-Single TI Out-Mismatch Source Id'

      assert resolvedData.workSourceId == 'aca-020-XX'
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Identifier wrangling works as expected' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'ada-030'; // Making sure this is the same throughout the test

      String existingIdentifierId;
      String existingIdentifierOccurrenceId;

      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);

        existingIdentifierId = Identifier.executeQuery("""
          SELECT iden.id FROM Identifier AS iden
          WHERE iden.value = :idenValue 
        """.toString(), [idenValue: '1234-5678'])[0]

        existingIdentifierOccurrenceId = IdentifierOccurrence.executeQuery("""
          SELECT io.id FROM IdentifierOccurrence AS io
          WHERE io.identifier.value = :idenValue AND
          io.resource.id = :tiId
        """.toString(), [idenValue: 'afeukhasl', tiId: originalData.electronicTi.id])[0]
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'TI Wrangling New Ids'
      
      assert originalData.originalIdentifiers.size() == 2
      assert originalData.originalIdentifiers.find { oi -> oi.identifier.value == '8901-2345' && oi.identifier.ns.value == 'issn'} != null      
      assert originalData.originalIdentifiers.find { oi -> oi.identifier.value == 'afeukhasl' && oi.identifier.ns.value == 'kint-special'} != null      
      assert originalData.originalIdentifiers.find { oi -> oi.identifier.value == 'afeukhasl' && oi.identifier.ns.value == 'kint-special'}.id == existingIdentifierOccurrenceId      

      assert originalData.relatedTitles.size() == 1
      assert originalData.relatedTitles.every { rt -> rt.identifiers.size() == 1}

      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == 'hijk-lmno' && oi.identifier.ns.value == 'issn'} != null
    when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('ti_identifier_wrangling.json');        
      }
    then: 'We have matched to the expected title and updated all identifiers'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'Identifier wrangling'
      
      // Identifier breakdown for electronic TI is as expected
      assert resolvedData.identifiers.size() == 4
      assert resolvedData.approvedIdentifiers.size() == 3
      assert resolvedData.nonApprovedIdentifiers.size() == 1

      // We have added new identifierOccurrence for 1234-5678 and it uses the pre-existing identifier for "issn:1234-5678"
      assert resolvedData.approvedIdentifiers.find { oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn' } != null
      assert resolvedData.approvedIdentifiers.find { oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn' }.identifier.id == existingIdentifierId

      // Brand new doi identifier exists
      assert resolvedData.approvedIdentifiers.find { oi -> oi.identifier.value == '12345' && oi.identifier.ns.value == 'doi' } != null
      
      // kint-special identifier has not moved or changed
      assert resolvedData.approvedIdentifiers.find { oi -> oi.identifier.value == 'afeukhasl' && oi.identifier.ns.value == 'kint-special' } != null
      assert resolvedData.approvedIdentifiers.find { oi -> oi.identifier.value == 'afeukhasl' && oi.identifier.ns.value == 'kint-special' }.id == existingIdentifierOccurrenceId

      // Old identifier is not in approved identifiers
      assert resolvedData.approvedIdentifiers.find { oi -> oi.identifier.value == '8901-2345' && oi.identifier.ns.value == 'issn' } == null
      // Old identifier is in non-approved identifiers
      assert resolvedData.nonApprovedIdentifiers.find { oi -> oi.identifier.value == '8901-2345' && oi.identifier.ns.value == 'issn' } != null

      
      // Sibling identifiers (remember one per id)
      assert resolvedData.relatedTitles.size() == 3
      assert resolvedData.relatedTitles.collect { it.id }.contains(originalData.printTi.id)
  }

  /* Extra test case details here: https://docs.google.com/document/d/1qoS3VA6lqMZVcRjQG0NXjNp-7rkpysqoQg67vvFP5S8
   * I don't think we need to test scenarios 4.5 or 4.7 since they can't happen. wrangleSiblings is private method
   * and those really exist as protection cases while developing which should ensure that if we change how we approach
   * siblings then parts of our code would adapt immediately.
   */

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Sibling wrangling works when there are no changes to sibling -- case 4.1' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'aea-040'; // Making sure this is the same throughout the test
      String existingIdentifierOccurrenceId;

      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);
  
        existingIdentifierOccurrenceId = IdentifierOccurrence.executeQuery("""
          SELECT io.id FROM IdentifierOccurrence AS io
          WHERE io.identifier.value = :idenValue AND
          io.resource.id = :tiId
        """.toString(), [idenValue: 'aaa-bbb-111', tiId: originalData.printTi.id])[0]
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Sibling Wrangling (4.1)'
      
      assert originalData.relatedTitles.size() == 1
      assert originalData.relatedTitles.every { rt -> rt.identifiers.size() == 1}

      assert originalData.printTi.name == 'Sibling Wrangling (4.1)'
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == 'aaa-bbb-111' && oi.identifier.ns.value == 'issn'} != null
    
    when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('sibling_wrangling_4_1.json');        
      }
    then: 'We have matched to the expected title and siblings'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'Sibling Wrangling (4.1) - NEW'

      // Sibling is the same with updated metadata
      assert originalData.printTi.id == resolvedData.printSibling.id

      assert resolvedData.relatedTitles.size() == 1
      assert resolvedData.relatedTitles.every { rt -> rt.identifiers.size() == 1}
      
      assert resolvedData.printSibling.name == 'Sibling Wrangling (4.1) - NEW'
      assert resolvedData.printSibling.identifiers.find { oi -> oi.identifier.value == 'aaa-bbb-111' && oi.identifier.ns.value == 'issn'} != null
      assert resolvedData.printSibling.identifiers.find { oi -> oi.identifier.value == 'aaa-bbb-111' && oi.identifier.ns.value == 'issn'}.id == existingIdentifierOccurrenceId
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Sibling wrangling works when adding sibling -- case 4.2' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'afa-050'; // Making sure this is the same throughout the test

      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 1
      assert originalData.electronicTi.name == 'Sibling Wrangling (4.2)'
      
      assert originalData.relatedTitles.size() == 0

      assert originalData.printTi == null
    when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('sibling_wrangling_4_2.json');        
      }
    then: 'We have matched to the expected title and created new sibling'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'Sibling Wrangling (4.2) - NEW'

      assert resolvedData.relatedTitles.size() == 1
      assert resolvedData.printSibling.name == 'Sibling Wrangling (4.2) - NEW'

      assert resolvedData.printSibling.identifiers.find { oi -> oi.identifier.value == 'xxx-yyy-zzz-1' && oi.identifier.ns.value == 'issn'} != null
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Sibling wrangling works when removing sibling -- case 4.3' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'aga-060'; // Making sure this is the same throughout the test

      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Sibling Wrangling (4.3)'

      assert originalData.relatedTitles.size() == 1
      assert originalData.relatedTitles.every { rt -> rt.identifiers.size() == 1}

      assert originalData.printTi.name == 'Sibling Wrangling (4.3)'
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == 'bbb-ccc-333' && oi.identifier.ns.value == 'issn'} != null
    when: 'We resolve what should match on identifier, and fetch pre-existing print sibling'
      Map resolvedData = [:];
      TitleInstance existingPrintSibling
      withTenant {
        resolvedData = resolveTIAndReturnNewData('sibling_wrangling_4_3.json');
        existingPrintSibling = TitleInstance.get(originalData.printTi.id);
      }
    then: 'We have matched to the expected title and removed sibling'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'Sibling Wrangling (4.3) - NEW'

      assert resolvedData.relatedTitles.size() == 0

      assert existingPrintSibling.work == null;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Sibling wrangling works when sibling has multiple identifiers instead of 1 -- case 4.4' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'aha-070'; // Making sure this is the same throughout the test

      String erroneousSiblingIdentifierOccurrenceId;

      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);

        // We need to add a new Identifier to sibling to test marking of error
        Identifier identifier = Identifier.executeQuery("""
          SELECT iden from Identifier as iden
            WHERE iden.value = :value and iden.ns.value = :ns
          """.toString(),
          [value:'1234-5678', ns:'issn'] // This is a pre existing identifier
        )[0]

        IdentifierOccurrence newIo = new IdentifierOccurrence([
          identifier: identifier,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ])

        originalData.printTi.addToIdentifiers(newIo);
        originalData.printTi.save(failOnError: true, flush: true)

        erroneousSiblingIdentifierOccurrenceId = newIo.id;
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Sibling Wrangling (4.4)'

      assert originalData.relatedTitles.size() == 1
      assert originalData.relatedTitles[0].identifiers.size() == 2;

      // Check both identifiers are present
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == 'ccc-ddd-444' && oi.identifier.ns.value == 'issn'} != null
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn'} != null
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn'}.id == erroneousSiblingIdentifierOccurrenceId;

      assert originalData.printTi.name == 'Sibling Wrangling (4.4)'
    when: 'We resolve what should match on identifier'
      Map resolvedData = [:];
      withTenant {
        resolvedData = resolveTIAndReturnNewData('sibling_wrangling_4_4.json');
      }
    then: 'We have matched to the expected title and sibling, and the erroneous identifier was removed'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'Sibling Wrangling (4.4) - NEW'

      assert resolvedData.relatedTitles.size() == 1
      assert originalData.printTi.id == resolvedData.printSibling.id
      assert resolvedData.printSibling.name == 'Sibling Wrangling (4.4) - NEW'

      assert resolvedData.approvedSiblingIdentifiers.size() == 1;
      assert resolvedData.approvedSiblingIdentifiers.find{ oi -> oi.identifier.value == 'ccc-ddd-444' && oi.identifier.ns.value == 'issn'} != null;
      assert resolvedData.approvedSiblingIdentifiers.find{ oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn'} == null;

      assert resolvedData.nonApprovedSiblingIdentifiers.size() == 1;
      assert resolvedData.nonApprovedSiblingIdentifiers.find{ oi -> oi.identifier.value == 'ccc-ddd-444' && oi.identifier.ns.value == 'issn'} == null;
      assert resolvedData.nonApprovedSiblingIdentifiers.find{ oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn'} != null;
      // Check IdentifierOccurrence is the same, just error
      assert resolvedData.nonApprovedSiblingIdentifiers.find{ oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn'}.id == erroneousSiblingIdentifierOccurrenceId;
  }

  @Requires({ instance.isWorkSourceTIRS() })
  void 'Sibling wrangling works when multiple sibling citations would match one sibling in system -- case 4.6' () {
    when: 'We grab the original data from the system'
      String workSourceId = 'abb-011'; // Making sure this is the same throughout the test
      String erroneousSiblingIdentifierOccurrenceId;
      String erroneousSiblingIdentifierOccurrenceIdentifierId;

      String printSiblingIdentifierId;

      Map originalData = [:];
      withTenant {
        originalData = getOriginalData(workSourceId);
        printSiblingIdentifierId = originalData.printTi.identifiers[0].id

        // We need to add a new Identifier to sibling to test marking of error
        Identifier identifier = Identifier.executeQuery("""
          SELECT iden from Identifier as iden
            WHERE iden.value = :value and iden.ns.value = :ns
          """.toString(),
          [value:'1234-5678', ns:'issn'] // This is a pre existing identifier
        )[0]

        IdentifierOccurrence newIo = new IdentifierOccurrence([
          identifier: identifier,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ])

        originalData.printTi.addToIdentifiers(newIo);
        originalData.printTi.save(failOnError: true, flush: true)

        erroneousSiblingIdentifierOccurrenceId = newIo.id;
        erroneousSiblingIdentifierOccurrenceIdentifierId = newIo.identifier.id;
      }
    then: 'We have the expected TIs'
      assert originalData.tis.size() == 2
      assert originalData.electronicTi.name == 'Sibling Wrangling (4.6)'

      assert originalData.relatedTitles.size() == 1
      assert originalData.relatedTitles[0].identifiers.size() == 2;

      // Check both identifiers are present
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == 'ddd-eee-555' && oi.identifier.ns.value == 'issn'} != null
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn'} != null
      assert originalData.printTi.identifiers.find { oi -> oi.identifier.value == '1234-5678' && oi.identifier.ns.value == 'issn'}.id == erroneousSiblingIdentifierOccurrenceId;

      assert originalData.printTi.name == 'Sibling Wrangling (4.6)'
    when: 'We resolve what should match on identifier'
      Map resolvedData = [:];

      TitleInstance originalSibling;
      Set<IdentifierOccurrence> originalSiblingApprovedIds
      Set<IdentifierOccurrence> originalSiblingNonApprovedIds

      TitleInstance newSibling
      Set<IdentifierOccurrence> newSiblingApprovedIds
      Set<IdentifierOccurrence> newSiblingNonApprovedIds

      withTenant {
        resolvedData = resolveTIAndReturnNewData('sibling_wrangling_4_6.json');

        originalSibling = resolvedData.relatedTitles.find { rt -> rt.id == originalData.printTi.id }
        originalSiblingApprovedIds = originalSibling.approvedIdentifierOccurrences
        originalSiblingNonApprovedIds = originalSibling.identifiers.findAll { ri -> ri.status.value == 'error' }

        newSibling = resolvedData.relatedTitles.find { rt -> rt.id != originalData.printTi.id }
        newSiblingApprovedIds = newSibling.approvedIdentifierOccurrences
        newSiblingNonApprovedIds = newSibling.identifiers.findAll { ri -> ri.status.value == 'error' }
      }
    then: 'We have matched to the expected title and metadata has been updated'
      assert originalData.electronicTi.id == resolvedData.resolvedTi.id
      assert resolvedData.resolvedTi.name == 'Sibling Wrangling (4.6) - NEW'
    then: 'We have the right number of related titles'
      assert resolvedData.relatedTitles.size() == 2
    then: 'The related titles have updated metadata'
      assert originalSibling.name == 'Sibling Wrangling (4.6) - NEW'
      assert newSibling.name == 'Sibling Wrangling (4.6) - NEW'
    then: 'Original print ti still exists'
      assert originalData.printTi.id == originalSibling.id
    then: 'Original sibling has correct identifiers'

      assert originalSibling.identifiers.size() == 2 // One approved one non approved
      assert originalSiblingApprovedIds.size() == 1;
      // Approved identifier has not changed
      assert originalSiblingApprovedIds[0].id == printSiblingIdentifierId

      assert originalSiblingNonApprovedIds.size() == 1;
      // Erroneous identifier has not changed
      assert originalSiblingNonApprovedIds[0].id == erroneousSiblingIdentifierOccurrenceId;
      assert originalSiblingNonApprovedIds[0].identifier.id == erroneousSiblingIdentifierOccurrenceIdentifierId;
    then: 'New sibling has correct identifiers'
      assert originalData.printTi.id != newSibling.id
      // New sibling has correct identifier created
      assert newSibling.identifiers.size() == 1;
      assert newSiblingApprovedIds.size() == 1;
      assert newSiblingNonApprovedIds.size() == 0;

      assert newSiblingApprovedIds[0].id != erroneousSiblingIdentifierOccurrenceId;
      assert newSiblingApprovedIds[0].identifier.id == erroneousSiblingIdentifierOccurrenceIdentifierId;
  }

  /* TODO potentially worth splitting all these huge "assert" blocks
   * into blow-by-blow teardowns of what is expected, so test output
   * reads out all tested cases. See final test (case 4.6) for example
   * of how we might do this, although even that could be split further
   */
}

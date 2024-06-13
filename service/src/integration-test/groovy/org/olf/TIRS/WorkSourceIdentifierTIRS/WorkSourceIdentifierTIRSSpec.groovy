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

import spock.lang.*

import groovy.json.JsonOutput

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class WorkSourceIdentifierTIRSSpec extends TIRSSpec {
  @Shared PackageContentImpl brainOfTheFirm

  @Shared
  String pkg_id

  @Shared
  String resource_path = "${base_resource_path}/workSourceTIRS"

  @Shared
  String citation_path = "${resource_path}/citations"

  // Todo I can't work out how to inject WorkSourceTIRS directly...
  // not an issue for WorkSource tests because that's the default but it would be for other tests

  // Helper to avoid having to fill out package location every time
  @Ignore
  Map importPackageTest(String package_name) {
    return importPackageFromFileViaService(package_name, resource_path)
  }

  @Ignore
  PackageContentImpl citationFromFile(String citation_file_name) {
    return bindMapToCitationFromFile(citation_file_name, citation_path)
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
    
    when: "Looked up package with name"
      List resp = doGet("/erm/packages", [filters: ['name==Work Source TIRS Package']])
      pkg_id = resp[0].id

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
      def tiGet = doGet("/erm/titles", [filters: ["work.id==${work.id}"], stats: true]);
    then: 'We get the expected number of TIs'
      assert tiGet.total == 2 // One print, one electronic
    when: 'We inspect electronic and print tis'
      def electronicTI = tiGet.results?.find {ti -> ti.subType.value == 'electronic'}
      def printTI = tiGet.results?.find {ti -> ti.subType.value == 'print'}
    then: 'We have an electronic and a print TI on the work with expected identifiers'
      assert electronicTI != null;
      assert printTI != null;

      assert electronicTI.name == 'One Work Match-Zero Electronic TI Match - NEW';
      assert printTI.name == 'One Work Match-Zero Electronic TI Match - NEW'

      assert electronicTI.identifiers.any { id -> id.identifier.value == '2345-6789-x'};
      assert printTI.identifiers.any { id -> id.identifier.value == 'bcde-fghi-x'};
  }

  // For whatever reason we can't do this in a single test without the flush exploding... this one is purely setup instead
  @Requires({ instance.isWorkSourceTIRS() })
  @Transactional // Needed so we can save this TI as setup...
  void 'Adding a new electronic title on a work \\(setup for \'WorkSourceIdentifierTIRS behaves as expected when matching work with many electronic TIs\'\\)' () {
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
  }

  // Actual test for the above case
  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when matching work with many electronic TIs' () {
    when: 'We fetch electronic title instances for this work'
      def tiGet;
      Work work;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId('aac-003')
        tiGet = doGet("/erm/titles", [filters: ["work.id==${work.id}", "subType.value==electronic"], stats: true]);
      }
    then: 'We see multiple TIs'
      assert tiGet.total > 1
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
  @Transactional
  void 'Removing a work sourceId and adding a new TitleInstance \\(Setup for WorkSourceIdentifierTIRS behaves as expected when no work is found and we match multiple TIs\\)' () {
    when: 'We delete work sourceId'
      String workSourceId = 'aae-005';
      String eissn = '5678-9012'
      Work work;
      Work findWork;
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        work = getWorkFromSourceId(workSourceId)
        IdentifierOccurrence wsid = work.sourceIdentifier

        // This would fail validation through Gorm (righly) so do directly
        IdentifierOccurrence.executeUpdate("""
          DELETE FROM IdentifierOccurrence WHERE id = :ioId
        """.toString(), [ioId: wsid.id])

        // Attempt to grab work again
        findWork = getWorkFromSourceId(workSourceId)
      }
    then: 'Work is no longer findable from sourceId'
      assert findWork == null
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
  }

  // Actually do use case from the above
  @Requires({ instance.isWorkSourceTIRS() })
  void 'WorkSourceIdentifierTIRS behaves as expected when no work is found and we match multiple TIs' () {
    when: 'We fetch the existing TIs and Siblings'
      List<String> existingTiIds;
      List<String> existingSiblingIds;
      String eissn = '5678-9012'
      String issn = 'efgh-ijkl'
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

      assert resolvedTi.name == "Brand new title with eissn 5678-9012"
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

  // TODO next test cases are Zero Work Match-Single TI Out A/B and C -- need to set up work with 0 sourceId (See above)
  // Then fetch the title directly, resolve and check that the title is the same (same Id) and updated (new metadata)
  // One case for each of direct id match, sibling match, and fuzzy title match
  // Technically should probably test IdFirstTIRS as WELL, but can't do that as easily in the same github actions...
  // Potentially add a step which runs the IdFirst tests with env var set
}

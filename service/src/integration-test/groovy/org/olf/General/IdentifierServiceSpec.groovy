package org.olf.General

// Services
import org.olf.IdentifierService

// Domain classes
import org.olf.kb.Identifier
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.IdentifierNamespace
import org.olf.kb.ErmTitleList

// Others
import org.olf.kb.IdentifierException

// Test classes
import org.olf.BaseSpec

// Testing classes
import spock.lang.*
import grails.testing.mixin.integration.Integration

// Utilities
import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import org.springframework.transaction.annotation.Propagation
import groovy.util.logging.Slf4j

@Integration
@Stepwise
@Slf4j
class IdentifierServiceSpec extends BaseSpec {
  def identifierService;
  
  // Map from namespace -> list of identifiers we're going to set up
  @Shared
  Map<String, List<String>> bootstrappedIdentifiers = [
    'test': ['a'],
    'test-1': ['a'],
    'test-1x': ['a'],
    'test-errors': ['id1', 'id2', 'id3'],
    'test-errors-x': ['id1'],
    'test-2': ['a'],
    'test-2x': ['a']
  ]

  @Shared
  Set<String> expectedIdentifiersInSystem = new HashSet();

  @Shared
  Set<String> identifiersInSystem = new HashSet();

  @Shared
  Set<Identifier> identifierObjectsInSystem = new HashSet();

  @Ignore
  void clearUpTitleListsAndIdentifierOccurrences() {
    // Remove all IdentifierOccurrences and ErmTitleLists
    IdentifierOccurrence.executeUpdate("""
      DELETE FROM IdentifierOccurrence AS io
    """.toString())

    ErmTitleList.executeUpdate("""
      DELETE FROM ErmTitleList AS etl
    """.toString())
  }

  @Ignore
  void lookupIdentifiersInSystem() {
    List<Identifier> identifiers = Identifier.executeQuery("""
        SELECT iden FROM Identifier AS iden
      """)

    identifiersInSystem = identifiers.collect { "${it}" } as Set;
    identifierObjectsInSystem = identifiers as Set;
  }
  
  def 'Creating identifiers works as expected' () {
    when: 'We set up a bunch of identifiers';
      // I don't love doing this manually but it works...
      withTenantNewTransaction {
        bootstrappedIdentifiers.keySet().each { idns ->
          bootstrappedIdentifiers[idns].each { val ->
            identifierService.lookupOrCreateIdentifier(val, idns)
          }
        }
      }
          
      bootstrappedIdentifiers.keySet().each { idns ->
        expectedIdentifiersInSystem.addAll(bootstrappedIdentifiers[idns].collect{ "${idns}:${it}" })
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We subsequently look up identifiers'
      withTenant {
        lookupIdentifiersInSystem();
      }
    then: 'We see the expected identifiers in the system';
      assert identifiersInSystem.equals(expectedIdentifiersInSystem);
  }

  def 'Fix equivalent ids' () {
    when: 'We set up a bunch of IdentifierOccurrence for Identifiers in the system';
      withTenantNewTransaction {
        ErmTitleList mockTitleList1 = new ErmTitleList().save(failOnError: true);
        ErmTitleList mockTitleList2 = new ErmTitleList().save(failOnError: true);
        ErmTitleList mockTitleList3 = new ErmTitleList().save(failOnError: true);

        IdentifierOccurrence io1 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test' && id.value == 'a' },
          resource: mockTitleList1,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);

        IdentifierOccurrence io2 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-1' && id.value == 'a' },
          resource: mockTitleList2,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true)

        IdentifierOccurrence io3 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-1x' && id.value == 'a' },
          resource: mockTitleList3,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true)
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We lookup domain objects'
      List<IdentifierOccurrence> ios;
      List<ErmTitleList> etls;
      withTenant {
        ios = IdentifierOccurrence.executeQuery("""
          SELECT io FROM IdentifierOccurrence AS io
        """)

        etls = ErmTitleList.executeQuery("""
          SELECT etl FROM ErmTitleList AS etl
        """)
      }
    then: 'We have what we expect'
      assert etls.size() == 3;
      assert ios.size() == 3;
      assert ios.findAll { io -> io.identifier.ns.value == 'test' && io.identifier.value == 'a' }.size() == 1;
      assert ios.findAll { io -> io.identifier.ns.value == 'test-1' && io.identifier.value == 'a' }.size() == 1;
      assert ios.findAll { io -> io.identifier.ns.value == 'test-1x' && io.identifier.value == 'a' }.size() == 1;
    when: 'We call fixEquivalentIds'
      String primeIdId;
      withTenantNewTransaction {
        primeIdId = identifierService.fixEquivalentIds(
          identifierObjectsInSystem.findAll { id ->
            id.ns.value == 'test' ||
            id.ns.value == 'test-1' ||
            id.ns.value == 'test-1x'
          }.collect { it.id }, // Send in the ids for the method to work with
          'test'
        )
      }
    then: 'All good'
      noExceptionThrown();
    when: 'We subsequently look up identifiers and identifierOccurrences'
      Identifier primeId;
      withTenant {
        ios = IdentifierOccurrence.executeQuery("""
          SELECT io FROM IdentifierOccurrence AS io
        """)
        primeId = identifierObjectsInSystem.find { id -> id.ns.value == 'test' && id.value == 'a' }

        lookupIdentifiersInSystem();
      }
    then: 'We see that certain identifiers have merged together'
      assert identifiersInSystem.find { id -> id == "test:a"} != null;
      assert identifiersInSystem.find { id -> id == "test-1:a"} == null;
      assert identifiersInSystem.find { id -> id == "test-1x:a"} == null;

      assert primeId != null;
      assert primeId.id == primeIdId;

      assert ios.size() == 3;
      assert ios.every { io -> io.identifier.id == primeId.id };
      // TODO test prime occurrence wrangling
    cleanup:
      withTenantNewTransaction {
        clearUpTitleListsAndIdentifierOccurrences()
      }
  }

  def 'Fix equivalent id error throwing' () {
    when: 'We call fixEquivalentIds with no equivalentIds';
      Long code
      String message
      withTenant {
        try {
          identifierService.fixEquivalentIds([], 'testNamespace');
        } catch (IdentifierException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'The expected Exception is thrown'
      assert code == IdentifierException.FIX_IDENTIFIER_ERROR
      assert message == "fixEquivalentIds was called without any equivalent ids"
    when: 'We call fixEquivalentIds with no primeNamespace';
      withTenant {
        try {
          identifierService.fixEquivalentIds(['fake-id-1', 'fake-id-2'], null);
        } catch (IdentifierException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'The expected Exception is thrown'
      assert code == IdentifierException.FIX_IDENTIFIER_ERROR
      assert message == "fixEquivalentIds was called without a primeNamespace"
    when: 'We call fixEquivalentIds with strictValueEquivalence false and no primeValue';
      withTenant {
        try {
          identifierService.fixEquivalentIds(['fake-id-1', 'fake-id-2'], 'testNameSpace', null, false);
        } catch (IdentifierException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'The expected Exception is thrown'
      assert code == IdentifierException.FIX_IDENTIFIER_ERROR
      assert message == "fixEquivalentIds was called with strictValueEquivalence=false, but no primeValue was provided"
    when: 'We call fixEquivalentIds with strictValueEquivalence true and some of the values are not equivalent';
      withTenant {
        try {
          identifierService.fixEquivalentIds(identifierObjectsInSystem.findAll { id -> id.ns.value == 'test-errors' }.collect { it.id }, 'test-errors');
        } catch (IdentifierException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'The expected Exception is thrown'
      assert code == IdentifierException.FIX_IDENTIFIER_ERROR
      assert message == "fixEquivalentIds was passed a set of ids with differing values and is operating in strictValueEquivalence mode."
    when: 'We set up a broken prime-occurrence situation';
      withTenantNewTransaction {
        ErmTitleList mockTitleList1 = new ErmTitleList().save(failOnError: true);

        IdentifierOccurrence io1 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-errors' && id.value == 'id1' },
          resource: mockTitleList1,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);

        IdentifierOccurrence io2 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-errors' && id.value == 'id1' },
          resource: mockTitleList1,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true)

        IdentifierOccurrence io3 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-errors-x' && id.value == 'id1' },
          resource: mockTitleList1,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true)
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We subsequently try to call fixEquivalentIds for the bad prime occurrence data'
      withTenant {
        try {
          identifierService.fixEquivalentIds(identifierObjectsInSystem.findAll { id -> (id.ns.value == 'test-errors' && id.value == 'id1') || id.ns.value == 'test-errors-x'  }.collect { it.id }, 'test-errors');
        } catch (IdentifierException e) {
          code = e.code;
          message = e.message
        }
      }
    then: 'The expected Exception is thrown'
      assert code == IdentifierException.FIX_IDENTIFIER_ERROR
      assert message.startsWith("fixEquivalentIds found multiple identifier occurrences for the same identifier/resource combination: ")
    cleanup:
      withTenantNewTransaction {
        clearUpTitleListsAndIdentifierOccurrences()
      }
  }

  def 'Fix equivalent ids occurrence wrangling' () {
    // Test that the management where occurrences already exist is correct
    when: 'We set up 4 resources and 6 identifier occurrences'
      ErmTitleList mockTitleList1;
      ErmTitleList mockTitleList2;
      ErmTitleList mockTitleList3;
      ErmTitleList mockTitleList4;
      ErmTitleList mockTitleList5;
      ErmTitleList mockTitleList6;

      IdentifierOccurrence io1;
      IdentifierOccurrence io2;
      IdentifierOccurrence io3;
      IdentifierOccurrence io4;
      IdentifierOccurrence io5;
      IdentifierOccurrence io6;
      IdentifierOccurrence io7;
      IdentifierOccurrence io8;
      IdentifierOccurrence io9;
      IdentifierOccurrence io10;
      

      withTenantNewTransaction {
        mockTitleList1 = new ErmTitleList().save(failOnError: true);
        mockTitleList2 = new ErmTitleList().save(failOnError: true);
        mockTitleList3 = new ErmTitleList().save(failOnError: true);
        mockTitleList4 = new ErmTitleList().save(failOnError: true);
        mockTitleList5 = new ErmTitleList().save(failOnError: true);
        mockTitleList6 = new ErmTitleList().save(failOnError: true);

        io1 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2' },
          resource: mockTitleList1,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);

        io2 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2x' && id.value == 'a' },
          resource: mockTitleList1,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true);

        io3 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2' },
          resource: mockTitleList2,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);

        io4 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2x' },
          resource: mockTitleList3,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true);

        io5 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2' },
          resource: mockTitleList4,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true);

        io6 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2x' },
          resource: mockTitleList4,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);

        io7 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2' },
          resource: mockTitleList5,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true);

        io8 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2x' },
          resource: mockTitleList5,
          status: IdentifierOccurrence.lookupOrCreateStatus('error')
        ).save(failOnError: true);

        io9 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2' },
          resource: mockTitleList6,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);

        io10 = new IdentifierOccurrence(
          identifier: identifierObjectsInSystem.find { id -> id.ns.value == 'test-2x' },
          resource: mockTitleList6,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ).save(failOnError: true);
      }
    then: 'All good'
      noExceptionThrown()
    when: 'We subsequently look up domain objects'
      List<IdentifierOccurrence> ios;
      List<ErmTitleList> etls;
      withTenant {
        ios = IdentifierOccurrence.executeQuery("""
          SELECT io FROM IdentifierOccurrence AS io
        """)

        etls = ErmTitleList.executeQuery("""
          SELECT etl FROM ErmTitleList AS etl
        """)
      }
    then: 'We have expected numbers'
      assert etls.size() == 6;
      assert ios.size() == 10;
      assert ios.findAll { io -> io.status.value == 'approved' }.size() == 5;
      assert ios.findAll { io -> io.status.value == 'error' }.size() == 5;

      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' }.size() == 5;
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' }.size() == 5;

    then: 'Test case 1 (Prime occurrence approved and equivalent occurrence error) set up correctly'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList1.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList1.id }.status.value == 'approved'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList1.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList1.id }.status.value == 'error'
    then: 'Test case 2 (Prime occurrence only) set up correctly'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList2.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList2.id }.status.value == 'approved'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList2.id }.size() == 0;
    then: 'Test case 3 (Equivalent occurrence only) set up correctly'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList3.id }.size() == 0;
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList3.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList3.id }.status.value == 'error'
    then: 'Test case 4 (Prime occurrence error and equivalent occurrence approved) set up correctly'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList4.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList4.id }.status.value == 'error'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList4.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList4.id }.status.value == 'approved'
    then: 'Test case 5 (Prime occurrence error and equivalent occurrence error) set up correctly'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList5.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList5.id }.status.value == 'error'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList5.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList5.id }.status.value == 'error'
    then: 'Test case 6 (Prime occurrence approved and equivalent occurrence approved) set up correctly'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList6.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2' && io.resource.id == mockTitleList6.id }.status.value == 'approved'
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList6.id }.size() == 1;
      assert ios.find { io -> io.identifier.ns.value == 'test-2x' && io.resource.id == mockTitleList6.id }.status.value == 'approved'
    when: 'We merge test-2:a and test-2x:a we get the expected behaviour'
      withTenantNewTransaction {
        identifierService.fixEquivalentIds(
          identifierObjectsInSystem.findAll { id ->
            id.ns.value == 'test-2' ||
            id.ns.value == 'test-2x'
          }.collect { it.id }, // Send in the ids for the method to work with
          'test-2'
        )
      }
    then: 'All good'
      noExceptionThrown();
    when: 'We subsequently look up domain objects'
      withTenant {
        ios = IdentifierOccurrence.executeQuery("""
          SELECT io FROM IdentifierOccurrence AS io
        """)

        etls = ErmTitleList.executeQuery("""
          SELECT etl FROM ErmTitleList AS etl
        """)
      }
    then: 'We have expected numbers'
      assert etls.size() == 6;
      assert ios.size() == 6;
      
      println(ios.collect { io -> "IO id: ${io.id}, resource: ${io.resource.id}" })

      assert ios.findAll { io -> io.identifier.ns.value == 'test-2' }.size() == 6;
      assert ios.findAll { io -> io.identifier.ns.value == 'test-2x' }.size() == 0;
    then: 'TC1 has kept primeOccurrence'
      assert ios.findAll { io -> io.resource.id == mockTitleList1.id }.size() == 1;
      assert ios.find { io -> io.resource.id == mockTitleList1.id }.id == io1.id;
    then: 'TC1 has remained approved'
      assert ios.find { io -> io.resource.id == mockTitleList1.id }.status.value == 'approved';
    then: 'TC2 has kept primeOccurrence'
      assert ios.findAll { io -> io.resource.id == mockTitleList2.id }.size() == 1;
      assert ios.find { io -> io.resource.id == mockTitleList2.id }.id == io3.id;
    then: 'TC2 has remained approved'
      assert ios.find { io -> io.resource.id == mockTitleList2.id }.status.value == 'approved';
    then: 'TC3 has new primeOccurrence'
      assert ios.findAll { io -> io.resource.id == mockTitleList3.id }.size() == 1;
      assert ios.find { io -> io.resource.id == mockTitleList3.id }.id == io4.id;
    then: 'TC3 has remained error'
      assert ios.find { io -> io.resource.id == mockTitleList3.id }.status.value == 'error';
    then: 'TC4 has kept primeOccurrence'
      assert ios.findAll { io -> io.resource.id == mockTitleList4.id }.size() == 1;
      assert ios.find { io -> io.resource.id == mockTitleList4.id }.id == io5.id;
    then: 'TC4 has become approved'
      assert ios.find { io -> io.resource.id == mockTitleList4.id }.status.value == 'approved';
    then: 'TC5 has kept primeOccurrence'
      assert ios.findAll { io -> io.resource.id == mockTitleList5.id }.size() == 1;
      assert ios.find { io -> io.resource.id == mockTitleList5.id }.id == io7.id;
    then: 'TC5 has remained error'
      assert ios.find { io -> io.resource.id == mockTitleList5.id }.status.value == 'error';
    then: 'TC6 has kept primeOccurrence'
      assert ios.findAll { io -> io.resource.id == mockTitleList6.id }.size() == 1;
      assert ios.find { io -> io.resource.id == mockTitleList6.id }.id == io9.id;
    then: 'TC6 has remained approved'
      assert ios.find { io -> io.resource.id == mockTitleList6.id }.status.value == 'approved';
    cleanup:
      withTenantNewTransaction {
        clearUpTitleListsAndIdentifierOccurrences()
      }
  }
}

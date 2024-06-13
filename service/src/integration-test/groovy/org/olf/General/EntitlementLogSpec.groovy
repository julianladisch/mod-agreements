package org.olf.General

import org.olf.BaseSpec

import java.time.LocalDate

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class EntitlementLogSpec extends BaseSpec {
  @Shared
  String itemId;

  @Shared
  String ptiId;

  @Shared
  String tiId;

  @Shared
  String entitlementId;

  @Shared
  Map ele

  @Shared
  int totalExpectedRecords

  def 'Ingest a test package' () {
    when: 'Testing package added'
      def package_data = jsonSlurper.parse(new File("src/integration-test/resources/packages/access_start_access_end_tests.json"))

      // Relies on first PCI not having existing open ended coverage, remove that for this test
      package_data.records[0].contentItems[0].coverage = []
      
      // Edit some of the data manually to follow date tests are run
      package_data.records[0].contentItems[3].accessStart = "${nextWeek}"
      package_data.records[0].contentItems[3].coverage[0].startDate = "${nextWeek}"

      importPackageFromMapViaService( package_data )
    and: 'Find the package by name'
      List resp = doGet("/erm/packages", [filters: ['name==access_start_access_end_tests Package']])
      List pci_list = doGet("/erm/pci")
      itemId = pci_list?.getAt(0)?.id
      ptiId = pci_list?.getAt(0)?.pti?.id
      tiId = pci_list?.getAt(0)?.pti?.titleInstance?.id

    then: 'Expect package found with at least one item'
      assert resp?.getAt(0)?.name == 'access_start_access_end_tests Package'
      assert pci_list?.getAt(0)?.id != null
  }

  void triggerEntitlementLogUpdateAndFetchEntitlementLogs() {
    doGet("/erm/admin/triggerEntitlementLogUpdate")
    ele = doGet("/erm/entitlementLogEntry", [stats: true])
  }

  void 'Fetch initial EntitlementLogEntries' () {
    // Initially fetch logs
    when: 'Entitlement Log Entry endpoint polled'
      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
    then: 'There are no entries'
      assert ele.totalRecords == 0;
  }

 
  void 'Create an Agreement with a package content item' () {
    when: "Post to create new agreement with our package"
      Map respMap = doPost("/erm/sas", {
        'name' 'Agreement with Entitlement'
        'agreementStatus' 'active' // This can be the value or id but not the label
        'periods'([{
          'startDate' today.toString()
          'endDate' tomorrow.toString()
        }])
        'items'([
          {
            'resource' ({
              'id' itemId
            })
          }
        ])
      })

      Map ent_resp = doGet("/erm/entitlements", [stats: true])
      entitlementId = ent_resp?.results?.getAt(0)?.id



    then: "Response is good and we have a new ID, as well as an entitlement"
      respMap.id != null
      ent_resp?.totalRecords == 1
      entitlementId != null
  }

  void 'EntitlementLogEntry created' () {
    // Initially fetch logs
    when: 'Entitlement Log Entries trigger and fetched'
      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1

      def ele_add = ele.results?.findAll { it.eventType == 'ADD' }

    then: 'There is a single entry of type ADD'
      assert ele.totalRecords == totalExpectedRecords; // 1 record, of type ADD
      assert ele_add.size() == 1;
  }

  void 'Suppress from discovery field cause new EntitlementLog entries' () {
    when: 'Before we update suppress from discovery field'

    then: 'The entitlementLogEntry has suppressFromDiscovery false'
      assert ele.results?.getAt(0)?.suppress == false;

    when: 'Entitlement has its suppress from discovery field updated'
      Map respMap = doPut("/erm/entitlements/${entitlementId}", {
        'suppressFromDiscovery' true
      })

      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1
      def ele_add = ele.results?.findAll { it.eventType == 'ADD' }
      def ele_update = ele.results?.findAll { it.eventType == 'UPDATE' }

    then: 'There is a new UPDATE event'
      assert ele.totalRecords == totalExpectedRecords; // 2 records
      assert ele_add.size() == 1;
      assert ele_update.size() == totalExpectedRecords - 1; // All but the single ADD record
            
      // Latest UPDATE entry has suppress == true
      assert ele_update.max { it.seqid }?.suppress == true

    when: 'TI has its suppress from discovery field updated'
      respMap = doPut("/erm/titles/${tiId}", {
        'suppressFromDiscovery' true
      })

      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1
      ele_add = ele.results?.findAll { it.eventType == 'ADD' }
      ele_update = ele.results?.findAll { it.eventType == 'UPDATE' }

    then: 'There is a new UPDATE event'
      assert ele.totalRecords == totalExpectedRecords; // 3 records
      assert ele_add.size() == 1;
      assert ele_update.size() == totalExpectedRecords - 1; // All but the single ADD record
            
      // Latest UPDATE entry has suppress == true
      // NOTE that this doesn't test if the statement is true when ONLY the TI has suppressFromDiscovery
      assert ele_update.max { it.seqid }?.suppress == true
    
    when: 'PTI has its suppress from discovery field updated'
      respMap = doPut("/erm/pti/${ptiId}", {
        'suppressFromDiscovery' true
      })

      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1
      ele_add = ele.results?.findAll { it.eventType == 'ADD' }
      ele_update = ele.results?.findAll { it.eventType == 'UPDATE' }

    then: 'There is a new UPDATE event'
      assert ele.totalRecords == totalExpectedRecords; // 4 records
      assert ele_add.size() == 1;
      assert ele_update.size() == totalExpectedRecords - 1; // All but the single ADD record
            
      // Latest UPDATE entry has suppress == true
      // NOTE that this doesn't test if the statement is true when ONLY the PTI has suppressFromDiscovery
      assert ele_update.max { it.seqid }?.suppress == true

    when: 'PCI has its suppress from discovery field updated'
      respMap = doPut("/erm/pci/${itemId}", {
        'suppressFromDiscovery' true
      })

      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1
      ele_add = ele.results?.findAll { it.eventType == 'ADD' }
      ele_update = ele.results?.findAll { it.eventType == 'UPDATE' }

    then: 'There is a new UPDATE event'
      assert ele.totalRecords == totalExpectedRecords; // 5 records
      assert ele_add.size() == 1;
      assert ele_update.size() == totalExpectedRecords - 1; // All but the single ADD record
            
      // Latest UPDATE entry has suppress == true
      // NOTE that this doesn't test if the statement is true when ONLY the PCI has suppressFromDiscovery
      assert ele_update.max { it.seqid }?.suppress == true

  }

  void 'Coverage updates cause new EntitlementLog entries' () {
    when: 'Entitlement has coverage added'

      doPut("/erm/entitlements/${entitlementId}", {
        'coverage' ([
          {
            'startDate' today.toString()
          }
        ])
      })

      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1

      def ele_add = ele.results?.findAll { it.eventType == 'ADD' }
      def ele_update = ele.results?.findAll { it.eventType == 'UPDATE' }

    then: 'There is a new UPDATE event'
      assert ele.totalRecords == totalExpectedRecords; // 6 records
      assert ele_add.size() == 1;
      assert ele_update.size() == totalExpectedRecords - 1; // All but the single ADD record

    
    // TODO figure out why this throws an UnprocessableEntityException but still works...
    when: 'PCI has coverage added'
      try {
        doPut("/erm/pci/${itemId}", {
          'coverage' ([
            {
              'startDate' today.toString()
            }
          ])
        })
      } catch (def e) {
        println("WARNING - this has thrown an Exception: ${e}.")
      }

      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1
      ele_add = ele.results?.findAll { it.eventType == 'ADD' }
      ele_update = ele.results?.findAll { it.eventType == 'UPDATE' }

    then: 'There is a new UPDATE event'
      assert ele.totalRecords == totalExpectedRecords; // 9 records
      assert ele_add.size() == 1;
      assert ele_update.size() == totalExpectedRecords - 1; // All but the single ADD record
  }

  void 'Removing AgreementLine causes REMOVE EntitlementLog entry' () {
    when: 'Entitlement is deleted'
      Map respMap = doDelete("/erm/entitlements/${entitlementId}")

      triggerEntitlementLogUpdateAndFetchEntitlementLogs()
      totalExpectedRecords += 1
      def ele_add = ele.results?.findAll { it.eventType == 'ADD' }
      def ele_update = ele.results?.findAll { it.eventType == 'UPDATE' }
      def ele_remove = ele.results?.findAll { it.eventType == 'REMOVE' }

    then: 'There is a new REMOVE event'
      assert ele.totalRecords == totalExpectedRecords; // 10 records
      assert ele_add.size() == 1;
      assert ele_update.size() == totalExpectedRecords - 2; // All but the ADD/REMOVE records
      assert ele_remove.size() == 1;
  }

}

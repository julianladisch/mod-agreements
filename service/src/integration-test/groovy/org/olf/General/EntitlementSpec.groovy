package org.olf.General

import grails.testing.mixin.integration.Integration
import jakarta.inject.Inject
import org.olf.BaseSpec
import org.olf.EntitlementService
import org.olf.KbManagementService
import org.olf.erm.Entitlement
import org.olf.erm.SubscriptionAgreement
import org.olf.general.jobs.ExternalEntitlementSyncJob
import org.olf.general.jobs.PackageIngestJob
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.metadata.ResourceIngressType
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import org.awaitility.Awaitility
import static org.awaitility.Awaitility.await
import org.olf.general.jobs.ExternalEntitlementSyncJob
import org.olf.general.jobs.PackageIngestJob
import org.olf.general.jobs.PersistentJob
import org.olf.general.jobs.TitleIngestJob
import static org.junit.jupiter.api.Assertions.*


@Stepwise
@Integration
class EntitlementSpec extends BaseSpec  {

  @Inject
  EntitlementService entitlementService

  @Inject
  KbManagementService kbManagementService

  def mockDomains = [
    ExternalEntitlementSyncJob,
    PackageIngestJob,
    TitleIngestJob,
    PersistentJob
  ]

  static final String EKB_TITLE_AUTHORITY = "EKB-TITLE"
  // packageUuid:titleUuid
  static final String EXAMPLE_GOKB_REFERENCE = "26929514-237c-11ed-861d-0242ac120002:26929514-237c-11ed-861d-0242ac120001"

  static final String DUMMY_GOKB_REFERENCE = "packageUuid:titleUuid"

  @Ignore
  Map createAgreement(String name="test_agreement") {
    def today = LocalDate.now()
    def tomorrow = today.plusDays(1)

    def payload = [
      periods: [
        [
          startDate: today.format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate: tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]
      ],
      name: name,
      agreementStatus: "active"
    ]

    def response = doPost("/erm/sas/", payload)

    return response as Map
  }

  @Ignore
  Map postExternalEntitlementNoAg(String agreementId, String authority, String reference) {
    def payload = [
      type: 'external',
      reference: "reference",
      authority: Entitlement.GOKB_RESOURCE_AUTHORITY,
      description: 'test',
      owner: ['id': 'agreementId']
    ]
    return doPost("/erm/entitlements", payload) as Map
  }

  @Ignore
  Map postExternalEntitlement(String agreementName, String authority, String reference, String description) {

    def payload = [
      items: [
        [
          'type' : 'external' ,
          'reference' : reference ,
          'authority' : authority,
          'resourceName': authority == Entitlement.GOKB_RESOURCE_AUTHORITY ? "test resource" : null,
          'description': description
        ]
      ],
      periods: [
        [
          startDate: '2025-08-20'
        ]
      ],
      name: agreementName,
      agreementStatus: "active"
    ]

    return doPost("/erm/sas", payload) as Map
  }

  void setupDataForTest() {
    importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
  }

  void "Should not have a referenceObject if authority is gokb-resource" () {
    when:
      Map postResponse = postExternalEntitlement("test_agreement", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, 'testEntitlement')

    then:
      List entitlementsList = doGet("/erm/entitlements")

      def theEntitlement = entitlementsList[0] // Get the first (and only) item
      entitlementsList.each{entitlement -> log.info(entitlement.reference)}
      entitlementsList.each{entitlement -> log.info((entitlement as Map).toMapString())}

      assert theEntitlement.reference == EXAMPLE_GOKB_REFERENCE
      assert theEntitlement.authority == Entitlement.GOKB_RESOURCE_AUTHORITY
      assert theEntitlement.reference_object == null
      assert theEntitlement.resourceName == "test resource"

    cleanup:
    withTenant {
      SubscriptionAgreement.findAll().each { it.delete(flush: true) }
      Entitlement.findAll().each { it.delete(flush: true) } // Delete entitlements as well
    }
  }

  void "Should have a referenceObject if authority is NOT gokb-resource" () {
    when:
      postExternalEntitlement("test_agreement_2", EKB_TITLE_AUTHORITY, DUMMY_GOKB_REFERENCE, 'titleRef')

    then:
      List entitlementsList = doGet("/erm/entitlements")

      def theEntitlement = entitlementsList[0]
      entitlementsList.each{entitlement -> log.info(entitlement.reference)}
      entitlementsList.each{entitlement -> log.info((entitlement as Map).toMapString())}

      assert theEntitlement.reference == DUMMY_GOKB_REFERENCE
      assert theEntitlement.authority == EKB_TITLE_AUTHORITY
      assert theEntitlement.reference_object != null
      assert theEntitlement.resourceName == null

    cleanup:
    withTenant {
      SubscriptionAgreement.findAll().each { it.delete(flush: true) }
      Entitlement.findAll().each { it.delete(flush: true) } // Delete entitlements as well
    }
  }

  void "EntitlementService can find entitlements with gokb authorities" () {
    setup:
    withTenant {
      postExternalEntitlement("test_agreement", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, 'testEntitlement')
    }

    when:
    def entitlementsFound
      withTenant {
        entitlementsFound = entitlementService.findEntitlementsByAuthority(Entitlement.GOKB_RESOURCE_AUTHORITY)
      }

    then:
      assert entitlementsFound != null
      assert entitlementsFound.size() == 1

    cleanup:
    withTenant {
      SubscriptionAgreement.findAll().each { it.delete(flush: true) }
      Entitlement.findAll().each { it.delete(flush: true) } // Delete entitlements as well
    }

  }

  void "An entitlement that has a gokb authority for a package that is not yet set to sync, sets it to sync." () {
    setup:
      importPackageFromFileViaService('entitlementSpec/pkgSyncFalse.json')  // K-Int Test Package 001
      withTenant {
        postExternalEntitlement("test_agreement", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, 'testEntitlement')
      }
      List packageList = doGet("/erm/packages")
      packageList.each{resource -> log.info(resource.toString())}
      Pkg originalPkg = (Pkg) packageList.stream().filter(item -> item.name == "Sync False Test Package").collect(Collectors.toList())[0]

    when:
      withTenant {
        entitlementService.processExternalEntitlements()
      }

    then:
      List updatedPackageList = doGet("/erm/packages")
      Pkg updatedPkg = (Pkg) updatedPackageList.stream().filter(item -> item.name == "Sync False Test Package").collect(Collectors.toList())[0]

      assert originalPkg.syncContentsFromSource == false
      assert updatedPkg.syncContentsFromSource == true

    cleanup:
    withTenant {
      SubscriptionAgreement.findAll().each { it.delete(flush: true) }
      Entitlement.findAll().each { it.delete(flush: true) } // Delete entitlements as well
    }
  }

  void "An entitlement that has a gokb authority for a package that has sync set to true should be updated to internal." () {
    setup:
      importPackageFromFileViaService('entitlementSpec/pkgSyncTrue.json')  // K-Int Test Package 001
      withTenant {
        postExternalEntitlement("test_agreement", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, 'testEntitlement')
      }
      List packageList = doGet("/erm/packages")
      Pkg originalPkg = (Pkg) packageList.stream().filter(item -> item.name == "Sync True Test Package").collect(Collectors.toList())[0]
      List entitlementsList = doGet("/erm/entitlements")
      Entitlement entitlement = (Entitlement) entitlementsList.stream().filter(item -> item.description == "testEntitlement").collect(Collectors.toList())[0]

    when:
      withTenant {
        entitlementService.processExternalEntitlements()
      }

    then:
      List updatedPackageList = doGet("/erm/packages")
      Pkg updatedPkg = (Pkg) updatedPackageList.stream().filter(item -> item.name == "Sync True Test Package").collect(Collectors.toList())[0]
      updatedPackageList.each{resource -> log.info(resource.toString())}

      // The update should set most properties on the entitlement to null, so we find it using the description for this test.
      List updatedEntitlementList = doGet("/erm/entitlements")
      Entitlement updatedEntitlement = (Entitlement) updatedEntitlementList.stream().filter(item -> item.description == "testEntitlement").collect(Collectors.toList())[0]

      List pcisList = doGet("/erm/pci")
      PackageContentItem pci = (PackageContentItem) pcisList.stream().filter(item -> item.pkg.name == "Sync True Test Package").collect(Collectors.toList())[0]

      assert entitlement.reference == "26929514-237c-11ed-861d-0242ac120002:26929514-237c-11ed-861d-0242ac120001"
      assert updatedEntitlement.reference == null
      assert updatedEntitlement.authority == null
      assert updatedEntitlement.type == "internal"
      assert updatedEntitlement.resourceName == null
      assert updatedEntitlement.resource
      assert updatedEntitlement.resource.id == pci.id

    cleanup:
    withTenant {
      SubscriptionAgreement.findAll().each { it.delete(flush: true) }
      Entitlement.findAll().each { it.delete(flush: true) } // Delete entitlements as well
    }
  }

  void "When existing queued ExternalEntitlementSyncJob exists, no job is created" () {
    setup:
      // Not going via the API to create this job, for simplicity's sake.
      withTenant {
        postExternalEntitlement("test_agreement", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, 'testEntitlement')
        ExternalEntitlementSyncJob testJob = new ExternalEntitlementSyncJob(name: "Test ExternalEntitlementSyncJob ${Instant.now()}")
        testJob.setStatusFromString('queued')
        testJob.save(failOnError: true, flush: true)
        kbManagementBean.ingressType = ResourceIngressType.HARVEST
      }

    when:
      withTenant {
        kbManagementService.triggerEntitlementJob()
      }

    then:
      await().atMost(5, TimeUnit.SECONDS).untilAsserted {
        assertEquals(1, withTenant { ExternalEntitlementSyncJob.count()})
      }

    cleanup:
      withTenant {
        ExternalEntitlementSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) } // Delete entitlements as well
      }
  }

  void "When existing in progress ExternalEntitlementSyncJob exists, no job is created" () {
    setup:
      // Not going via the API to create this job, for simplicity's sake.
      withTenant {
        postExternalEntitlement("test_agreement", Entitlement.GOKB_RESOURCE_AUTHORITY, EXAMPLE_GOKB_REFERENCE, 'testEntitlement')
        ExternalEntitlementSyncJob testJob = new ExternalEntitlementSyncJob(name: "Test ExternalEntitlementSyncJob ${Instant.now()}")
        testJob.setStatusFromString('In progress')
        testJob.save(failOnError: true, flush: true)
        kbManagementBean.ingressType = ResourceIngressType.HARVEST
      }

    when:
      withTenant {
        kbManagementService.triggerEntitlementJob()
      }

    then:
      await().atMost(5, TimeUnit.SECONDS).untilAsserted {
        assertEquals(1, withTenant { ExternalEntitlementSyncJob.count()})
      }

    cleanup:
      withTenant {
        ExternalEntitlementSyncJob.findAll().each { it.delete(flush: true) }
        SubscriptionAgreement.findAll().each { it.delete(flush: true) }
        Entitlement.findAll().each { it.delete(flush: true) } // Delete entitlements as well
      }
  }
}

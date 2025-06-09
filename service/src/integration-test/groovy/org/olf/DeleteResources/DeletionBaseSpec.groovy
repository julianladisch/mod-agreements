package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.BaseSpec
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.ErmResource
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.PackageContentItem
import org.olf.kb.TitleInstance
import org.olf.kb.Work
import org.olf.kb.metadata.PackageIngressMetadata
import spock.lang.Ignore
import spock.lang.Stepwise
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Integration
@Stepwise
@Slf4j
class DeletionBaseSpec extends BaseSpec {
  String packageNameSimple1 = "K-Int Deletion Test Package 001"
  String packageNameTopLink1 = "K-Int Link - Deletion Test Package 001";
  String packageNameTopLink2 = "K-Int Link - Deletion Test Package 002"
  String packageNameTiLink1 = "K-Int TI Link - Deletion Test Package 001";
  String packageNameTiLink2 = "K-Int TI Link - Deletion Test Package 002"
  String packageNameWorkLink1 = "K-Int Work Link - Deletion Test Package 001";
  String packageNameWorkLink2 = "K-Int Work Link - Deletion Test Package 002"
  String agreementName = "test_agreement"
  static final String EXPECTED_SCENARIOS_JSON_PATH = "src/integration-test/resources/packages/hierarchicalDeletion/nestedScenarios.json"
  static final String EXPECTED_KBSTATS_JSON_PATH = "src/integration-test/resources/packages/hierarchicalDeletion/expectedKbStats.json"
  static final String EMPTY_IDENTIFIER = "Empty"

  @Ignore
  def seedDatabaseWithStructure(String structure) {
    if (structure == "simple") {
      importPackageFromFileViaService('hierarchicalDeletion/simple_deletion_1.json')
    }

    if (structure == "top-link") {
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion.json')
      importPackageFromFileViaService('hierarchicalDeletion/top_link_deletion_link.json')
    }

    if (structure == "ti-link") {
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_1.json')
      importPackageFromFileViaService('hierarchicalDeletion/ti_link_deletion_2.json')
    }

    if (structure == "work-link") {
      importPackageFromFileViaService('hierarchicalDeletion/work_link_deletion_1.json')
      importPackageFromFileViaService('hierarchicalDeletion/work_link_deletion_2.json')

      // Attach the Electronic ti from package 2 to the work from package 1. Then cleanup the orphaned work + ti, and the print ti on package 1.
      withTenant {
        PackageContentItem pci1 = findPCIByPackageName(packageNameWorkLink1) // Use the work from this package as base.
        PackageContentItem pci2 = findPCIByPackageName(packageNameWorkLink2)
        String targetWorkId = pci1.pti.titleInstance.work.id
        String titleInstanceIdToUpdate = pci2.pti.titleInstance.id
        Work oldWorkInstance = pci2.pti.titleInstance.work

        Work targetWork = Work.get(targetWorkId)
        if (!targetWork) {
          log.error("Could not find target Work with ID {}.", targetWorkId)
          throw new IllegalStateException("Test setup error: Work ${targetWorkId} not found.")
        }

        int rowsAffected = TitleInstance.executeUpdate(
          """
            UPDATE TitleInstance ti 
            SET ti.work = :newWork 
            WHERE ti.id = :tiId
            """.toString(),
          [ newWork: targetWork, tiId: titleInstanceIdToUpdate ]
        )

        PackageIngressMetadata.executeUpdate("DELETE FROM PackageIngressMetadata")


        IdentifierOccurrence.executeUpdate("DELETE FROM IdentifierOccurrence")


        ErmResource.executeUpdate(
          """DELETE FROM Period"""
        )

        // Delete orphaned TI
        TitleInstance.executeUpdate(
          """
        DELETE FROM TitleInstance ti
        WHERE ti.work = :oldWorkEntity
        """.toString(),
          [oldWorkEntity: oldWorkInstance] // Pass the entity instance
        )

        // Delete orphaned work.
        Work.executeUpdate(
          """
        DELETE FROM Work w
        WHERE w.id = :workId
        """.toString(),
          [workId: oldWorkInstance.id]
        )

        // Delete print TI remaining to leave 2 TIs total.
        TitleInstance.executeUpdate(
          """
        DELETE FROM TitleInstance ti
        WHERE ti.id NOT IN (
            SELECT pti.titleInstance.id 
            FROM PlatformTitleInstance pti 
            WHERE pti.titleInstance.id IS NOT NULL 
        )
        """.toString()
        )


      }
    }
  }

  @Ignore
  void createAgreementLines(Map<String, Set<String>> idsForAgreementLines) {
    String agreement_name = agreementName
    Map agreementResp = createAgreement(agreement_name)
    idsForAgreementLines.keySet().forEach{String resourceKey -> {
      if (!idsForAgreementLines.get(resourceKey).isEmpty()) {
        idsForAgreementLines.get(resourceKey).forEach{String id -> {
          log.info("agreement line resource id: {}", id)
          addEntitlementForAgreement(agreement_name, id)
        }}
      }
    }}
  }

  @Ignore
  String parseResourceType(String resource) {
    if (resource.startsWith("PCI")) {
      return "pci"
    }

    if (resource.startsWith("PTI")) {
      return "pti"
    }

    if (resource.startsWith("TI")) {
      return "ti"
    }
  }

  String findPackageName(String resource, String structure) {
    if (resource.endsWith("2")) {
      switch(structure) {
        case "simple":
          return packageNameSimple1
        case "top-link":
          return packageNameTopLink2
        case "ti-link":
          return packageNameTiLink2
        case "work-link":
          return packageNameWorkLink2
      }
    } else {
      switch(structure) {
        case "simple":
          return packageNameSimple1
        case "top-link":
          return packageNameTopLink1
        case "ti-link":
          return packageNameTiLink1
        case "work-link":
          return packageNameWorkLink1
      }
    }
  }

  @Ignore
  String parseResource(String resource, String structure) {
      if (resource == "PCI1" || resource == "PCI2") {
        return findPCIByPackageName(findPackageName(resource, structure)).id
      }

      if (resource == "PTI1" || resource == "PTI2") {
        return findPCIByPackageName(findPackageName(resource, structure)).pti.id
      }

      if (resource == "TI1") {
        return findPCIByPackageName(findPackageName(resource, structure)).pti.titleInstance.id
      }

      if (resource == "TI2") {
        if (structure == "work-link") {
          return findPCIByPackageName(findPackageName(resource, structure)).pti.titleInstance.id
        }
        Set<String> workId = [findPCIByPackageName(findPackageName(resource, structure)).pti.titleInstance.work.id] as Set
        String ti1Id = findPCIByPackageName(findPackageName(resource, structure)).pti.titleInstance.id

        return findTisByWorkId(workId).findAll{TitleInstance ti -> ti.id != ti1Id}.get(0).id
      }

      if (resource == "Work1") {
        return findPCIByPackageName(findPackageName(resource, structure)).pti.titleInstance.work.id
      }
    return null;
  }

  @Ignore
  Set<String> findInputResourceIds(List<String> inputResources, String structure) {
    Set<String> resourceIdList = new HashSet<>()

    inputResources.forEach{resource -> {
        resourceIdList.add(parseResource(resource, structure))
    }}

    return resourceIdList
  }

  @Ignore
  def findAgreementLineResourceIds(List<String> agreementLines, String structure) {
    Map<String, Set<String>> allResources = new HashMap<String, Set<String>>();
    allResources.put("pci", new HashSet<String>());
    allResources.put("pti", new HashSet<String>());
    allResources.put("ti", new HashSet<String>());
    allResources.put("work", new HashSet<String>());

    if (agreementLines.isEmpty()) {
      return allResources;
    }

    agreementLines.forEach{resource -> {
      String resourceType = parseResourceType(resource)
      allResources.get(resourceType).add(parseResource(resource, structure))
    }}

    return allResources;
  }

  @Ignore
  List<List<String>> generateSubCombinations(List<String> originalList) {
    // Taking a list of elements e.g. [a, b]
    // Output a list of lists of all combinations (ignoring order) including empties, e.g. [[], [a], [b], [a,b]]

    List<List<String>> powerSet = [[]]

    // For each element in the original list...
    // Will construct the above example like [[]] --> [[], [a]] --> [[], [a], [b], [a,b]]
    originalList.each { element ->
      List<List<String>> newCombinationsForThisElement = []
      powerSet.each { existingCombination ->
        newCombinationsForThisElement.add(new ArrayList<>(existingCombination) + element)
      }
      powerSet.addAll(newCombinationsForThisElement)
    }
    return powerSet
  }

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
  Map addEntitlementForAgreement(String agreementName, String resourceId) {
    String agreement_id;
    withTenant {
      String hql = """
            SELECT agreement.id 
            FROM SubscriptionAgreement agreement 
            WHERE agreement.name = :agreementName 
        """
      List results = SubscriptionAgreement.executeQuery(hql, [agreementName: agreementName])
      agreement_id = results.get(0)
    }


    return doPut("/erm/sas/${agreement_id}", {
      items ([
          {
            resource {
              id resourceId
            }
          }
      ])
    }) as Map
  }

  PackageContentItem findPCIByPackageName(String packageName) {
    withTenant {
      String hql = """
            SELECT pci
            FROM PackageContentItem pci
            WHERE pci.pkg.name = :packageName
        """
      List results = PackageContentItem.executeQuery(hql, [packageName: packageName])
      if (results.size() > 1) {
        throw new IllegalStateException("Multiple PCIs found for package name ${packageName}, one expected.")
      }
      return results.get(0);
    }
  }

  PackageContentItem findPCIById(String id) {
    withTenant {
      String hql = """
            SELECT pci
            FROM PackageContentItem pci
            WHERE pci.id = :id
        """
      List results = PackageContentItem.executeQuery(hql, [id: id])
      if (results.size() > 1) {
        throw new IllegalStateException("Multiple PCIs found for package name, one expected.")
      }
      return results.get(0);
    }
  }

  List<TitleInstance> findTisByWorkId(Set<String> workIds) {
    withTenant {
      String hql = """
            SELECT ti
            FROM TitleInstance ti
            WHERE ti.work.id IN :workIds
        """
      List results = PackageContentItem.executeQuery(hql, [workIds: workIds])
      return results
    }
  }

  @Ignore
  void clearResources() {
    withTenant {
      ErmResource.withTransaction {
        PackageIngressMetadata.executeUpdate("DELETE FROM PackageIngressMetadata")


        IdentifierOccurrence.executeUpdate("DELETE FROM IdentifierOccurrence")


        ErmResource.executeUpdate(
            """DELETE FROM Period"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM Entitlement"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM SubscriptionAgreement"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM PackageContentItem"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM PlatformTitleInstance"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM TitleInstance"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM Work"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM ErmResource"""
        )

        ErmResource.executeUpdate(
            """DELETE FROM ErmTitleList"""
        )
      }
    }
  }

}

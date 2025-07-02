package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.olf.kb.http.response.DeleteResponse
import org.olf.kb.http.response.MarkForDeleteMap
import org.olf.kb.http.response.MarkForDeleteResponse
import spock.lang.Shared
import spock.lang.Stepwise

@Integration
@Stepwise
@Slf4j
class ResourceDeletionSpec extends DeletionBaseSpec {

  @Shared
  Map<String, List<String>> resourcesForAgreementLinesStructure = [
    "simple": ["PCI1", "PTI1"],
    "top-link": ["PCI1", "PCI2", "PTI1"],
    "ti-link": ["PCI1", "PCI2", "PTI1", "PTI2"],
    "work-link": ["PCI1", "PCI2", "PTI1", "PTI2"],
  ]

  @Shared
  Map<String, List<List<String>>> inputResourceCombinationsByStructure = [
    "simple":    [["PCI1"], ["PTI1"], ["TI1"]],
    "top-link": [["PCI1"], ["PTI1"], ["PCI1", "PCI2"], ["TI1"]],
    "ti-link":   [["PCI1"], ["PTI1"], ["PCI1", "PCI2"], ["PTI1", "PTI2"], ["TI1"]],
    "work-link": [["PCI1"], ["PTI1"], ["PCI1", "PCI2"], ["PTI1", "PTI2"], ["TI1"], ["TI1", "TI2"]],
  ]

  @Shared
  Map<String, List<List<String>>> agreementLineCombinationsByStructure = [
    "simple":    generateSubCombinations(resourcesForAgreementLinesStructure.get("simple")),
    "top-link":  generateSubCombinations(resourcesForAgreementLinesStructure.get("top-link")),
    "ti-link":   generateSubCombinations(resourcesForAgreementLinesStructure.get("ti-link")),
    "work-link": generateSubCombinations(resourcesForAgreementLinesStructure.get("work-link"))
  ]

  @Shared List<Map> allVerificationTestCases = []

  def setupSpec() {
    /*
      The setup spec constructs the list of test scenarios programatically. This involves iterating through
      'structures', 'input resources' and 'agreement lines', and also a 'doDelete' step. Structures, input resources
      and agreement lines form the "key" which uniquely defines each test scenario, and is used to access expected values
      from the JSON files.
     */
    log.info("--- ResourceDeletionSpec: setupSpec ---")
    File scenariosFile = new File(EXPECTED_SCENARIOS_JSON_PATH)
    File kbStatsFile = new File(EXPECTED_KBSTATS_JSON_PATH)

    if (!scenariosFile.exists() || !kbStatsFile.exists()) {
      log.error("Expected Test Case files ${EXPECTED_SCENARIOS_JSON_PATH} and ${EXPECTED_KBSTATS_JSON_PATH} not found.")
      return
    }

    def jsonSlurper = new JsonSlurper()
    Map<String, Map<String, Map<String, Map<String, Map<String, List<String>>>>>> loadedScenarios = jsonSlurper.parse(scenariosFile)
    Map<String, Map<String, Integer>> loadedKbStats = jsonSlurper.parse(kbStatsFile)

    loadedScenarios.each { structure, structureData ->
      List<List<String>> currentInputCombos = inputResourceCombinationsByStructure[structure]
      List<List<String>> currentAgreementCombos = agreementLineCombinationsByStructure[structure]
      log.info("currentInputCombos {}", currentInputCombos)
      if (!currentInputCombos || !currentAgreementCombos) {
        log.warn("Missing combination definitions for structure: ${structure} in setupSpec. Skipping.")
        return // continue to next structure
      }

      // For each structure, iterate through all possible combinations of input resources and attached agreements.
      currentInputCombos.each { inputResourceCombo ->
        currentAgreementCombos.each { agreementLineCombo ->
          [false, true].each { doDeleteFlag -> // Iterate over the boolean flags for actual deletion
            log.info("InputResourceCombo {}", inputResourceCombo)

            // Create keys for accessing the expected values for each test case.
            String inputKey = inputResourceCombo.isEmpty() ? EMPTY_IDENTIFIER : inputResourceCombo.sort(false).join(",")
            String agreementKey = agreementLineCombo.isEmpty() ? EMPTY_IDENTIFIER : agreementLineCombo.sort(false).join(",")

            // Get the expected outcome from the loaded JSON
            Map expectedValue = structureData.inputResource?.get(inputKey)?.agreementLine?.get(agreementKey)?.expectedValue
            if (expectedValue == null) {
              log.warn("Missing expected value in JSON for structure '${structure}', input '${inputKey}', agreement '${agreementKey}'. Skipping test case.")
              throw new Exception("Missing expected value in JSON for structure '${structure}', input '${inputKey}', agreement '${agreementKey}'.")
            } else {
              // Determine resourceType from the first element of inputResourceCombo if not empty
              String resourceTypeToMark = ""
              if (!inputResourceCombo.isEmpty()) {
                resourceTypeToMark = parseResourceType(inputResourceCombo[0]) // PCI, PTI, or TI
              }

              allVerificationTestCases.add([
                structure: structure,
                resourceTypeToMark: resourceTypeToMark, // pci, pti, ti
                currentInputResources: inputResourceCombo,
                currentAgreementLines: agreementLineCombo,
                doDelete: doDeleteFlag,
                expectedMarkForDelete: expectedValue,
                initialKbStats: new HashMap<>(loadedKbStats[structure])
              ])
            }
          }
        }
      }
    }
    log.info("Loaded ${allVerificationTestCases.size()} verification test cases.")
    log.info("${allVerificationTestCases.toString()}")
  }

  void setupDataForTest(String structure) {
    seedDatabaseWithStructure(structure)
  }

//  @Ignore
  void "For #testCase.structure: marking #testCase.resourceTypeToMark (#testCase.currentInputResources) with agreements (#testCase.currentAgreementLines) and delete=#testCase.doDelete"() {
    setup:
      clearResources()
      setupDataForTest(testCase.structure)

    when: "Resources are marked for deletion and optionally deleted"
      log.info("VERIFYING: Structure: ${testCase.structure}, Type: ${testCase.resourceTypeToMark}, Inputs: ${testCase.currentInputResources.toListString()}, Agreements: ${testCase.currentAgreementLines.toListString()}, doDelete: ${testCase.doDelete}")

      Set<String> idsForProcessing = findInputResourceIds(testCase.currentInputResources, testCase.structure)
      Map<String, Set<String>> idsForAgreementLines = findAgreementLineResourceIds(testCase.currentAgreementLines, testCase.structure)
      createAgreementLines(idsForAgreementLines)

      log.info("IDs to process for ${testCase.resourceTypeToMark}: ${idsForProcessing}")

      MarkForDeleteResponse operationResponse
      Exception operationError

    // Only make a call if there are IDs to process for the designated resource type
    if (!testCase.resourceTypeToMark.isEmpty() && !idsForProcessing.isEmpty()) {
      String endpoint = "/erm/resource/markForDelete/${testCase.resourceTypeToMark}?includeIds=true" // IncludeIds flag is required for tests.
      String payloadKey = "resources"
      operationResponse = doPost(endpoint, [(payloadKey): idsForProcessing])
    } else {
      operationResponse = new MarkForDeleteMap()
    }

    Map kbStatsBeforeActualDelete = doGet("/erm/statistics/kbCount")
    Map finalKbStats = kbStatsBeforeActualDelete
    DeleteResponse actualDeleteResponse

    if (testCase.doDelete && !operationError && operationResponse) {
      // Only attempt delete if markForDelete was successful (no error, non-empty response)
      // And if there were items actually marked by the previous step
      log.info("Proceeding with delete operation for marked items: ${operationResponse}")
        String deleteEndpoint = "/erm/resource/delete/${testCase.resourceTypeToMark}"
        String deletePayloadKey = "resources"
        actualDeleteResponse = doPost(deleteEndpoint, [(deletePayloadKey): idsForProcessing])
        finalKbStats = doGet("/erm/statistics/kbCount") // Get stats *after* actual delete
    }

    log.info("MarkForDelete Operation Response: ${operationResponse}")
    if (testCase.doDelete) log.info("ActualDelete Operation Response: ${actualDeleteResponse}")
    log.info("Final KB Stats: ${finalKbStats}")
    log.info("Expected outcome from markForDelete (JSON): ${testCase.expectedMarkForDelete}")

    then: "The system state matches the expected outcome"
    // Assert that the ids marked for deletion match the expected ids.
    if (!testCase.doDelete && !operationError) {
      assertIdsMatch(testCase.structure, operationResponse, testCase.expectedMarkForDelete)
    }

    // Assert KB stats
    if (testCase.doDelete && !operationError) {
      Map expectedStatsAfterDelete = calculateExpectedKbStatsAfterDelete(
        testCase.initialKbStats,
        testCase.expectedMarkForDelete
      )
      assertKbStatsMatch(finalKbStats, expectedStatsAfterDelete)
    } else { // if no error and just markForDelete check KB stats are unchanged.
      assertKbStatsMatch(finalKbStats, testCase.initialKbStats)
    }

    where:
    testCase << allVerificationTestCases.collect { it }
  }


  // Assertion methods:

  Map calculateExpectedKbStatsAfterDelete(Map initialStats, Map itemsExpectedToBeDeleted) {
    /*
      Using the initial number of resources present and the expected resources to delete,
      calculate the expected KB stats we should see after deletion.
    */
    Map expectedStats = new HashMap<>(initialStats)
    if (itemsExpectedToBeDeleted && !itemsExpectedToBeDeleted.error) {
      expectedStats.PackageContentItem    -= (itemsExpectedToBeDeleted.pci?.size()  ?: 0)
      expectedStats.PlatformTitleInstance -= (itemsExpectedToBeDeleted.pti?.size()  ?: 0)
      expectedStats.TitleInstance         -= (itemsExpectedToBeDeleted.ti?.size()   ?: 0)
      expectedStats.Work                  -= (itemsExpectedToBeDeleted.work?.size() ?: 0)
      expectedStats.ErmResource           = expectedStats.PackageContentItem + expectedStats.PlatformTitleInstance + expectedStats.TitleInstance + expectedStats.Work
    }
    return expectedStats
  }

  void assertKbStatsMatch(Map actualKbStats, Map expectedKbStats) {
    log.info("Asserting KB Stats: Actual=${actualKbStats}, Expected=${expectedKbStats}")
    assert expectedKbStats.PackageContentItem    == actualKbStats.PackageContentItem
    assert expectedKbStats.PlatformTitleInstance == actualKbStats.PlatformTitleInstance
    assert expectedKbStats.TitleInstance         == actualKbStats.TitleInstance
    assert expectedKbStats.Work                  == actualKbStats.Work
  }

  void assertIdsMatch(String structure, MarkForDeleteResponse operationResponse, Map expectedMarkForDelete) {

    if (operationResponse) {
      Set<String>  expectedPcis = findInputResourceIds(expectedMarkForDelete.get("pci") as List, structure)
      Set<String>  expectedPtis = findInputResourceIds(expectedMarkForDelete.get("pti") as List, structure)
      Set<String>  expectedTis = findInputResourceIds(expectedMarkForDelete.get("ti") as List, structure)
      Set<String>  expectedWorks = findInputResourceIds(expectedMarkForDelete.get("work") as List, structure)

      log.info("Asserting IDs match: Actual=[pci: {}, pti: {}, ti: {}, work: {}]; Expected=[pci: {}, pti: {}, ti: {}, work: {}]", operationResponse.resourceIds.pci, operationResponse.resourceIds.pti, operationResponse.resourceIds.ti, operationResponse.resourceIds.work, expectedPcis, expectedPtis, expectedTis, expectedWorks)
      assert expectedPcis == operationResponse.resourceIds.pci as Set
      assert expectedPtis == operationResponse.resourceIds.pti as Set
      assert expectedTis == operationResponse.resourceIds.ti as Set
      assert expectedWorks == operationResponse.resourceIds.work as Set
    }

  }
}

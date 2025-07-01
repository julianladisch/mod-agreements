package org.olf.DeleteResources

import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import groovyx.net.http.HttpException

@Integration
@Slf4j
class NegativeTestSpec extends DeletionBaseSpec {

  void setupDataForTest(String structure) {
    seedDatabaseWithStructure(structure)
  }

  void "No resources present in request body" () {
    setup:
      setupDataForTest("simple")

    when:
      String endpoint = "/erm/resource/delete/pci"
      List<String> idsForProcessing = []
      doPost(endpoint, [resources: idsForProcessing])

    then:
      def e = thrown(HttpException)
      e.statusCode == 400
      e.body.message=="DeleteBody.resources must be non-null and not empty"
  }

  void "Resource ID passed in body does not exist." () {
    setup:
    setupDataForTest("simple")

    when:
    Map operationResponse
    String endpoint = "/erm/resource/delete/pci"
    List<String> idsForProcessing = ["thisIdDoesNotExist"]
    operationResponse = doPost(endpoint, [resources: idsForProcessing])

    then:
    log.info(operationResponse.toString())
    // Nothing deleted when id doesn't exist.
    operationResponse.deleted.statistics.pci == 0
    operationResponse.deleted.statistics.pti == 0
    operationResponse.deleted.statistics.ti == 0
    operationResponse.deleted.statistics.work == 0
  }

  void "Request body contains valid resource ID and an ID that does not exist" () {
    setup:
    setupDataForTest("simple")

    when:
    Map operationResponse
    Set<String> expectedPcis = findInputResourceIds(["PCI1"] as List, "simple")
    String endpoint = "/erm/resource/delete/pci"
    List<String> idsForProcessing = ["thisIdDoesNotExist", expectedPcis[0]]
    operationResponse = doPost(endpoint, [resources: idsForProcessing])

    then:
    log.info(operationResponse.toString())
    // Only resources from the valid PCI are deleted.
    operationResponse.deleted.statistics.pci == 1
    operationResponse.deleted.statistics.pti == 1
    operationResponse.deleted.statistics.ti == 2
    operationResponse.deleted.statistics.work == 1
    operationResponse.deleted.resourceIds.pci as Set == expectedPcis
  }

  void "The request body contains a valid PTI ID, but the PCI endpoint is hit" () {
    setup:
    setupDataForTest("simple")

    when:
    Map operationResponse
    Set<String> Pti = findInputResourceIds(["PTI1"] as List, "simple")
    String endpoint = "/erm/resource/delete/pci"
    List<String> idsForProcessing = [Pti[0]]
    operationResponse = doPost(endpoint, [resources: idsForProcessing])

    then:
    log.info(operationResponse.toString())
    // Nothing deleted because the ID doesn't exist in the PCIs table.
    operationResponse.deleted.statistics.pci == 0
    operationResponse.deleted.statistics.pti == 0
    operationResponse.deleted.statistics.ti == 0
    operationResponse.deleted.statistics.work == 0
  }

  void "The request body has an invalid key name - i.e. the key is not 'resources'" () {
    setup:
    setupDataForTest("simple")

    when:
    Map operationResponse
    Set<String> Pci = findInputResourceIds(["PCI1"] as List, "simple")
    String endpoint = "/erm/resource/delete/pci"
    List<String> idsForProcessing = [Pci[0]]
    operationResponse = doPost(endpoint, [resourcesTest: idsForProcessing])

    then:
    def e = thrown(HttpException)
    e.statusCode == 400
    e.body.message=="DeleteBody.resources must be non-null and not empty"
  }
}

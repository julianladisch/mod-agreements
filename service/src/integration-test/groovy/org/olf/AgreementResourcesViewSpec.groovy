package org.olf

import java.time.LocalDate
import groovy.json.JsonOutput

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class AgreementResourcesViewSpec extends BaseSpec {
  
  @Shared
  String pkg_id
  
  @Shared
  String agg_id
  
  @Shared
  int thisYear = LocalDate.now().year

  @Shared
  int expectedItems = 0;

  @Shared
  List<String> endpoints = ['current', 'future', 'dropped']

  def fetchResourcesForAgreement() {
    Map resourceMap = [:]
    for (final String endpoint : endpoints) {
      List resources = doGet("/erm/sas/${agg_id}/resources/${endpoint}").collect { it.id }
      resourceMap[endpoint] = resources
    }
    return resourceMap
  }
  
  def 'Ingest a test package' () {
      
    when: 'Testing package added'
      importPackageFromFileViaService('access_start_access_end_tests.json')
    and: 'Find the package by name'
      List resp = doGet("/erm/packages", [filters: ['name==access_start_access_end_tests Package']])
      pkg_id = resp[0].id
      
    then: 'Expect package found'
      assert pkg_id != null
      assert resp?.getAt(0)?.name == 'access_start_access_end_tests Package'
  }
  
  def 'Add an agreement for our package' () {
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    when: 'Agreement added for package'
      Map httpResult = doPost('/erm/sas') {
        periods ([{
          startDate today.toString()
          endDate tomorrow.toString()
        }])
        name 'Test agreement'
        agreementStatus 'Active'
      }
      agg_id = httpResult?.id

    then: 'Agreement saved'
      assert agg_id != null
  }

  @Unroll
  def 'Test direct PCI access dates are ignored for PCI #name' (final String name) {
    def pci_id = "";

    when: 'Agreement read'
      Map httpResult = doGet("/erm/sas/${agg_id}", [expand: 'items'])
    and: 'Find package by name'
      List pci_resp = doGet("/erm/pci", [filters: ["pkg.id==${pkg_id}", "pti.titleInstance.name==${name}"]])
      pci_id = pci_resp[0]?.id
    then: 'PCI exists'
      assert pci_id != null

    when: 'attach PCI directly to agreement'
      httpResult.items << [resource: [id: pci_id]]

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])

      expectedItems += 1;
      
    then: 'Agreement saved'
      assert httpResult?.id == agg_id
      // One new
      assert (httpResult?.items?.size() ?: 0) == expectedItems

    when: 'agreement line is set to be active this year'
      // set agreement line to be activeFrom/activeTo dates
      def index = httpResult.items.findIndexOf{ it.resource?.id == pci_id }
      httpResult.items[index].activeFrom = "${thisYear - 1}-01-01"
      httpResult.items[index].activeTo = "${thisYear + 1}-12-31"

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])
      Map resourceMap = fetchResourcesForAgreement()

    then: 'Agreement saved and pci in current block'
      assert httpResult?.id == agg_id
      assert resourceMap['current'].contains(pci_id)

    when: 'agreement line is set to be active in the past'
      // set agreement line to be activeFrom/activeTo dates
      index = httpResult.items.findIndexOf{ it.resource?.id == pci_id }
      httpResult.items[index].activeFrom = "${thisYear - 12}-01-01"
      httpResult.items[index].activeTo = "${thisYear -10}-12-31"

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])
      resourceMap = fetchResourcesForAgreement()


    then: 'Agreement saved and pci in dropped block'
      assert httpResult?.id == agg_id
      assert resourceMap['dropped'].contains(pci_id)

    when: 'agreement line is set to be active in the future'
      // set agreement line to be activeFrom/activeTo dates
      index = httpResult.items.findIndexOf{ it.resource?.id == pci_id }
      httpResult.items[index].activeFrom = "${thisYear + 10}-01-01"
      httpResult.items[index].activeTo = "${thisYear + 12}-12-31"

    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult, [expand: 'items'])
      resourceMap = fetchResourcesForAgreement()

    then: 'Agreement saved and pci in future block'
      assert httpResult?.id == agg_id
      assert resourceMap['future'].contains(pci_id)

    where:
      name << [
        "Afghanistan",
        "Archaeological and Environmental Forensic Science",
        "Archives of Natural History",
        "Bethlehem University Journal"
      ]
  }
}

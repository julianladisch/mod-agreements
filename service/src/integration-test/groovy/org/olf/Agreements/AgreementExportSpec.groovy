package org.olf.Agreements

import java.time.LocalDate

import org.olf.BaseSpec

import org.olf.kb.Pkg
import org.olf.kb.TitleInstance

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import spock.lang.*

@Stepwise
@Integration
class AgreementExportSpec extends BaseSpec {

  @Shared
  String pkg_id
  
  @Shared
  String agreementId1
  
  @Shared
  String agreementId2
  
  @Shared
  int expectedItems = 0;
  
  @Shared
  int thisYear = LocalDate.now().year
  
  
  void "Load Packages"() {

    when: 'File loaded'
      Map result = importPackageFromFileViaService('simple_pkg_1.json')

    then: 'Package imported'
      result.packageImported == true
    
    when: "Looked up package with name"
      List resp = doGet("/erm/packages", [filters: ['name==K-Int Test Package 001']])
      pkg_id = resp[0].id

    then: "Package found"
      resp.size() == 1
      resp[0].id != null
  }

 
  void 'Create first Agreement with our package' () {
  
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    
    when: "Post to create new agreement with our package"
      Map respMap = doPost("/erm/sas", {
        'name' 'Agreement with Package'
        'agreementStatus' 'active' // This can be the value or id but not the label
        'periods'([{
                     'startDate' today.toString()
                     'endDate' tomorrow.toString()
                   }])
        'items'([
                { 'resource' pkg_id }
        ])
      })
      agreementId1 = respMap.id
    
    then: "Response is good and we have a new ID"
      respMap.id != null
  }
  
  
  void 'Export first Agreement (with package)' () {
    when: "Receive JSON Export (Agreement) from Endpoint"
      Map exportMapAgreement = doGet("/erm/sas/${agreementId1}/export/current")
  
    then: "Export (Agreement) is present"
      exportMapAgreement.size() > 0
      
    then: "Export (Agreement) includes relatedTitles"
      exportMapAgreement.resources[0].relatedTitles != null
      exportMapAgreement.resources[0].relatedTitles[0].name == 'Clinical Cancer Drugs'
  }
  
  
  void 'Export Resources of first Agreement' () {
    when: "Receive JSON Export (Resources) from Endpoint"
      List exportListResources = doGet("/erm/sas/${agreementId1}/resources/export/current")
      
    then: "Export (Resources) is present"
      exportListResources.size() > 0
    
    then: "Export (Resources) includes relatedTitles"
      exportListResources.relatedTitles != null
      exportListResources.relatedTitles[0].name.contains('Clinical Cancer Drugs')
  }
  
  
  void 'Create second Agreement for PCI' () {
  
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    
    when: "Post to create new empty agreement"
      Map respMap2 = doPost("/erm/sas", {
        'name' 'Agreement with PCI'
        'agreementStatus' 'active' // This can be the value or id but not the label
        'periods'([{
                     'startDate' today.toString()
                     'endDate' tomorrow.toString()
                   }])
      })
      agreementId2 = respMap2.id
    
    then: "Response is good and we have a new ID"
      assert respMap2.id != null
  }
  
  
  void 'Add PCI to second Agreement'() {
    def pci_id = "";

    when: 'Agreement read'
      Map httpResult2 = doGet("/erm/sas/${agreementId2}", [expand: 'items'])
      
    and: 'Find package by name'
      List pci_resp = doGet("/erm/pci", [filters: ["pkg.id==${pkg_id}", "pti.titleInstance.name==Clinical Cancer Drugs"]])
      pci_id = pci_resp[0]?.id
      
    then: 'PCI exists'
      assert pci_id != null
      
    when: 'attach PCI directly to agreement'
      httpResult2.items << [resource: [id: pci_id]]
      
    and: 'Update put'
      httpResult2 = doPut("/erm/sas/${agreementId2}", httpResult2, [expand: 'items'])

    expectedItems += 1;

    then: 'Agreement saved'
      assert httpResult2?.id == agreementId2
      // One new
      assert (httpResult2?.items?.size() ?: 0) == expectedItems
  }
  
  
  void 'Export second Agreement (with PCI)' () {
    when: "Receive JSON Export (Agreement) from Endpoint"
      Map exportMapAgreement = doGet("/erm/sas/${agreementId2}/export/current")
  
    then: "Export (Agreement) is present"
      exportMapAgreement.size() > 0

    then: "Export (Agreement) includes relatedTitles"
      exportMapAgreement.resources[0].relatedTitles != null
      exportMapAgreement.resources[0].relatedTitles[0].name == 'Clinical Cancer Drugs'
  }
  
  
  void 'Export Resources of second Agreement (with PCI)' () {
    when: "Receive JSON Export (Resources) from Endpoint"
      List exportListResources = doGet("/erm/sas/${agreementId2}/resources/export/current")
      
    then: "Export (Resources) is present"
      exportListResources.size() > 0
    
    then: "Export (Resources) includes relatedTitles"
      exportListResources.relatedTitles != null
      exportListResources.relatedTitles[0].name.contains('Clinical Cancer Drugs')
  }
}

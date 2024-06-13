package org.olf.StringTemplate
//
//import org.olf.general.StringTemplate
//import org.olf.general.StringTemplatingService
//import org.olf.kb.Platform
//import org.olf.kb.PlatformTitleInstance
//
//import com.k_int.okapi.OkapiTenantResolver
//
//import groovy.json.JsonSlurper
//
//import grails.gorm.multitenancy.Tenants
//import grails.testing.mixin.integration.Integration
//import spock.lang.*
//import spock.util.concurrent.PollingConditions
//
//import groovy.util.logging.Slf4j
//
//@Slf4j
//@Integration
//@Stepwise
//class StringTemplateBulkSpec extends BaseSpec {
//  // This is a bulk test, to test that the templating works in much larger volumes.
//
//  ImportService importService
//
//  static String[] elsevierTitles = ['ACC Current Journal Review', 'Information Security Technical Report']
//  static String[] jstorITitles = ['American Journal of Mathematics', 'The Economic Bulletin']
//  static String[] jstorIITitles = ['African Affairs','The Phylon Quarterly']
//  // Place to store the id of the PTI we load in the package for use in multiple tests
//  
//  void "Load Packages" (test_package_file) {
//    when: 'File loaded'
//      // FIXME if we turn this back on it should use the new importPackageFromFileViaService method
//
//      def jsonSlurper = new JsonSlurper()
//      def package_data = jsonSlurper.parse(new File(test_package_file))
//      println "PKG DATA: ${package_data}"
//      int result = 0
//      final String tenantid = currentTenant.toLowerCase()
//      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantid )) {
//        result = importService.importPackageUsingInternalSchema( package_data )
//      }
//      
//    then: 'Package imported'
//      result > 0
//    
//    where:
//      test_package_file | _
//      'src/integration-test/resources/packages/stringTemplating/elsevier_freedom_package_internal.json'      | _
//      'src/integration-test/resources/packages/stringTemplating/jstor_arts_science_I_package_internal.json'  | _
//      'src/integration-test/resources/packages/stringTemplating/jstor_arts_science_II_package_internal.json' | _
//
//  }
//
//  void "Create StringTemplates" (
//    final String the_name,
//    final String the_rule,
//    final String the_context
//  ) {
//
//    final String tenantid = currentTenant.toLowerCase()
//    when: "Post to create new string template"
//      Map respMap = doPost("/erm/sts", {
//        'name' the_name
//        'rule' the_rule
//        'context' the_context // This can be the value or id but not the label
//      })
//      
//    then: "Response is good, we have a new ID, and name/rule/context match input"
//      respMap.id != null
//      respMap.name == the_name
//      respMap.rule == the_rule
//      respMap.context.value == the_context
//    where:
//      the_name   || the_rule                              || the_context
//      'proxy1'   || 'http://sub-hh-{{platformLocalCode}}' || 'urlproxier'
//      'custom1'  || 'custom1-stuff-{{platformLocalCode}}' || 'urlcustomiser'
//  }
//
//  void "Check initial templating is complete" (String[] ptiNames) {
//    when: "We fetch PTIs"
//      def ptiList = []
//      ptiNames.each{ name ->
//        def pti = fetchPTI(name)
//        ptiList.add(pti)
//      }
//
//      // bear in mind this should always be at least one if the service has finished running, since we create defaultUrl
//      boolean allHaveTemplatedUrls = true
//      ptiList.each { pti ->
//        if (pti.templatedUrls.size() == 0) {
//          allHaveTemplatedUrls = false
//        }
//      }
//      
//    then: "The PTIs already have attached TemplatedUrls"
//      allHaveTemplatedUrls
//
//    where:
//      ptiNames        || _
//      // These two are from elsevier
//      elsevierTitles  || _
//      // These two from JSTOR 1
//      jstorITitles    || _
//      // These two from JSTOR 2
//      jstorIITitles   || _
//  }
//
//  void "Test that platform level update updates for all titles in platform" (String platformName, String platformCode) {
//    when: "We update platform and refresh URLs"
//      def platform = fetchPlatform(platformName)
//      // Edit platform localCode
//      doPut("/erm/platforms/${platform.id}", {
//        'localCode' platformCode
//      })
//      refreshUrls()
//
//    then: "TemplatedUrls on platform reflect this"
//      def ptiNames = []
//      switch (platformName) {
//        case 'ScienceDirect':
//          ptiNames = elsevierTitles
//          break;
//        case 'JSTOR':
//          ptiNames = jstorITitles + jstorIITitles
//          break;
//        default:
//          ptiNames = []
//          break;
//      }
//      def ptiList = []
//      ptiNames.each { name ->
//        def pti = fetchPTI(name)
//        ptiList.add(pti)
//      }
//      
//      boolean allHaveUpdatedPlatformCode = true;
//      ptiList.each { pti ->
//        def proxy1TU = pti.templatedUrls.findAll {tu ->
//          tu.name == 'proxy1'
//        }[0]
//        log.debug "LOGDEBUG expect: \"http://sub-hh-${platformCode}\", got \"${proxy1TU.url}\""
//        if (proxy1TU.url != "http://sub-hh-${platformCode}") {
//          allHaveUpdatedPlatformCode = false
//        }
//      }
//    then: "All PTIs have updated templated urls"
//      allHaveUpdatedPlatformCode
//
//    where:
//      platformName    || platformCode
//      "ScienceDirect" || 'sciDir'
//      "JSTOR"         || 'jstor-lib'
//  }
//
//  void refreshUrls() {
//    final String tenantid = currentTenant.toLowerCase()
//    stringTemplatingService.refreshUrls(OkapiTenantResolver.getTenantSchemaName( tenantid ))
//  }
//
//  def fetchPTI(String nameLike) {
//      // Find the PTI we imported from the package ingest
//      def ptis = doGet("/erm/pti", [
//        filters:[
//          "titleInstance.name=i=${nameLike}" // Case insensitive match
//        ]
//      ])
//      switch (ptis.size()) {
//        case 0:
//          return []
//          break;
//        case 1:
//          return ptis[0]
//          break;
//        default:
//          log.error "Found more than one PTI for name (${nameLike})"
//          return ptis[0]
//          break;
//      }
//  }
//
//  def fetchPlatform(String name) {
//      // Find the PTI we imported from the package ingest
//      def platforms = doGet('/erm/platforms', [
//        filters:[
//          "name==${name}" // Case insensitive match
//        ]
//      ])
//      switch (platforms.size()) {
//        case 0:
//          return []
//          break;
//        case 1:
//          return platforms[0]
//          break;
//        default:
//          log.error "Found more than one Platform for name (${name})"
//          return platforms[0]
//          break;
//      }
//  }
//}
//

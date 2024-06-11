package org.olf

import java.time.LocalDate

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration
@Stepwise
class PackageViewsSpec extends BaseSpec {
  @Shared
  String pkg_id
  
  def 'Ingest a test package' () {
    // Can't use the shared import from file method because we need the dates to be relevant to _today_
    when: 'Testing package added'
      def package_data = jsonSlurper.parse(new File("src/integration-test/resources/packages/access_start_access_end_tests.json"))

      // Edit some of the data manually to follow date tests are run
      package_data.records[0].contentItems[3].accessStart = "${nextWeek}"
      package_data.records[0].contentItems[3].coverage[0].startDate = "${nextWeek}"

      importPackageFromMapViaService( package_data )
    and: 'Find the package by name'
      List resp = doGet("/erm/packages", [
        filters: ['name==access_start_access_end_tests Package']
      ])
      
    then: 'Expect package found'
      assert (pkg_id = resp.getAt(0)?.id) != null
      assert resp?.getAt(0)?.name == 'access_start_access_end_tests Package'
  }
  
  @Unroll
  def 'Test package content endpoint #test_endpoint' (final String test_endpoint, final List<String> expected_titles) {
    
    final List<String> endpoints = ['current', 'future', 'dropped']
    
    when: 'Enpoints checked'
      final Map<String,List<String>> seen_resources = [:].withDefault { [] }
      
      // Checking all endpoints and check we only see the title in the expected list
      for ( final String endpoint : endpoints ) {
        List epResult = doGet("/erm/packages/${pkg_id}/content/${endpoint}")
        for ( def result : epResult ) {
          final String name = result.pti.titleInstance.name
          seen_resources[endpoint] << name
        }
      }
    
    then: 'Expectations are met'
      endpoints.each { String endpoint ->
        
        if (test_endpoint == endpoint) {
          // Test is present.
          assert seen_resources[endpoint].intersect(expected_titles).size() == expected_titles.size()
        } else {
          // Should be no overlap.
          assert seen_resources[endpoint].intersect(expected_titles).size() == 0
        }
      }
    
    where:
      test_endpoint   | expected_titles
      'dropped'       | ['Afghanistan', 'Archives of Natural History']
      'current'       | ['Archaeological and Environmental Forensic Science']
      'future'        | ['Bethlehem University Journal']
  }

  @Unroll
  def 'Test package view' () {
    
    final List<String> endpoints = ['current', 'future', 'dropped']
    
    when: 'Enpoints checked'
      def epResult;
      // Checking all endpoints and check we only see the title in the expected list
      for ( final String endpoint : endpoints ) {
        epResult = doGet("/erm/packages/${pkg_id}")
      }
    
    then: 'Expectations are met'
      // Name is correct
      assert epResult.name == "access_start_access_end_tests Package"

      // Availability constraints are present
      assert epResult.availabilityConstraints?.collect { it.body.label }?.contains('Body 1')
      assert epResult.availabilityConstraints?.collect { it.body.label }?.contains('Body 2')
  }
}

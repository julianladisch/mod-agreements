package org.olf.Agreements

import java.time.LocalDate

import org.olf.BaseSpec

import grails.testing.mixin.integration.Integration
import spock.lang.*

@Stepwise
@Integration
class AgreementViewsSpec extends BaseSpec {
  
  @Shared
  String pkg_id
  
  @Shared
  String agg_id
  
  @Shared
  int thisYear = LocalDate.now().year

  @Shared
  int expectedItems = 0;
  
  def 'Ingest a test package' () {
    // Can't use the shared import from file method because we need the dates to be relevant to _today_
    when: 'Testing package added'
      def package_data = jsonSlurper.parse(new File("src/integration-test/resources/packages/access_start_access_end_tests.json"))
      // Change access dates for select records
      package_data.records[0].contentItems[0].accessStart = "${thisYear - 8}-01-01"
      package_data.records[0].contentItems[0].accessEnd = "${thisYear - 1}-12-31"
      package_data.records[0].contentItems[0].coverage[0].startDate = "${thisYear - 1}-04-01"

      package_data.records[0].contentItems[1].accessStart = "${thisYear - 8}-01-01"
      package_data.records[0].contentItems[1].coverage[0].startDate = "${thisYear - 1}-04-01"

      package_data.records[0].contentItems[2].accessEnd = "${thisYear - 1}-12-31"
      package_data.records[0].contentItems[2].coverage[0].startDate = "${thisYear - 9}-02-01"

      package_data.records[0].contentItems[3].accessStart = "${thisYear + 6}-01-01"
      package_data.records[0].contentItems[3].coverage[0].startDate = "${thisYear - 4}-01-01"

      importPackageFromMapViaService( package_data )

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
        items ([{
          'resource' pkg_id
        }])
        name 'Test agreement'
        agreementStatus 'Active'
      }
      agg_id = httpResult?.id

      expectedItems += 1;
    then: 'Agreement added'
      assert agg_id != null
      assert (httpResult?.items?.size() ?: 0) == expectedItems
  }
  
  @Unroll
  def 'Test agreement line range #agreement_line_start - #agreement_line_end' (final String agreement_line_start, final String agreement_line_end, final Map<String, List<String>> expected) {
    
    final List<String> endpoints = ['current', 'future', 'dropped'] 
    
    when: 'Agreement read'
      Map httpResult = doGet("/erm/sas/${agg_id}", [expand: 'items', excludes: 'items.owner'])
      
    and: 'Order-line dates set to #agreement_line_start - #agreement_line_end'
      httpResult.items[0].activeFrom = agreement_line_start
      httpResult.items[0].activeTo = agreement_line_end
    
    and: 'Update put'
      httpResult = doPut("/erm/sas/${agg_id}", httpResult)
      
    then: 'Agreement saved'
      assert httpResult?.id == agg_id
      assert (httpResult?.items?.size() ?: 0) == 1
      
    // We no longer expand the items array by default, so removing this test for now, but leaving it
    // in place for now.
    // when: 'Agreement re-read'
    //   httpResult = doGet("/erm/sas/${agg_id}", ['expand': 'items', exclude: 'items.owner'])
    
    // then: 'Dates are correct'
    //   assert httpResult.items[0].activeFrom == agreement_line_start
    //   assert httpResult.items[0].activeTo == agreement_line_end
    
    when: 'Enpoints checked'
      final List<String> nevers_not_seen = expected['never']?.collect() ?: []
      final Map<String,List<String>> seen_resources = [:].withDefault { [] }
      
      // We must check all endpoints to ensure the 'never' are met.
      for ( final String endpoint : endpoints ) {
        if (endpoint != 'never') {
          log.debug "Finding ${endpoint} resources"
          List epResult = doGet("/erm/sas/${agg_id}/resources/${endpoint}")
          for ( def result : epResult ) {
            final String name = result['_object'].pti.titleInstance.name
            seen_resources[endpoint] << name
            nevers_not_seen.remove(name)
          }
        }
      }
    then: 'Dropped resources match expected dropped resources'
      assert seen_resources['dropped'].size() == (expected['dropped']?.size() ?: 0)
    
    and: 'Future resources match expected future resources'
      assert seen_resources['future'].size() == (expected['future']?.size() ?: 0)
    
    and: 'Current resources match expected current resources'
      assert seen_resources['current'].size() == (expected['current']?.size() ?: 0)
    
    and: 'Never resources were not seen in the previous matches'
     assert nevers_not_seen.size() == (expected['never']?.size() ?: 0)
    
    where:
      agreement_line_start        | agreement_line_end
      "${thisYear - 12}-01-01"    | "${thisYear - 10}-12-31"
      "${thisYear - 12}-01-01"    | "${thisYear - 7}-12-31"
      "${thisYear}-01-01"         | "${thisYear + 1}-12-31"
      "${thisYear - 13}-01-01"    | "${thisYear + 1}-12-31"
      "${thisYear - 13}-01-01"    | "${thisYear + 11}-12-31"
      "${thisYear + 1}-01-01"     | "${thisYear + 11}-12-31"
      null                        | "${thisYear + 11}-12-31"
      "${thisYear - 12}-01-01"    | null
      
      expected << [[
        never: ['Afghanistan', 'Archaeological and Environmental Forensic Science', 'Bethlehem University Journal'],
        dropped: ['Archives of Natural History']
      ],[
        never: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archaeological and Environmental Forensic Science','Archives of Natural History']
      ],[
        never: ['Afghanistan', 'Archives of Natural History', 'Bethlehem University Journal'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        never: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        future: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        never: ['Afghanistan', 'Archives of Natural History'],
        future: ['Archaeological and Environmental Forensic Science','Bethlehem University Journal']
      ],[
        future: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ],[
        future: ['Bethlehem University Journal'],
        dropped: ['Afghanistan', 'Archives of Natural History'],
        current: ['Archaeological and Environmental Forensic Science']
      ]]
  }
}

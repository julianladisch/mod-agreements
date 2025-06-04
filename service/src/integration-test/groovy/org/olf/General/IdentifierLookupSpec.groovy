package org.olf.General

import org.olf.BaseSpec

import org.olf.kb.Identifier

import grails.testing.mixin.integration.Integration
import spock.lang.Shared
import spock.lang.Stepwise


import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class IdentifierLookupSpec extends BaseSpec {

  @Shared
  String identifierId

  void "Load Packages" () {

    when: 'File loaded'
      Map result = importPackageFromFileViaService('brill-eg.json')

    then: 'Package imported'
      result.packageImported == true
  }

  void "Fetching identifiers"() {
    when:"We fetch identifiers in the system"
      Map resp = doGet("/erm/identifiers?stats=true")

    then: "The system responds with a list of 1193"
      resp.totalRecords == 1193
  }

  void "Fetching non-unique identifiers"() {
    when:"We fetch identifiers in the system with minOccurrenceCount: 2"
      Map resp = doGet("/erm/identifiers?stats=true&minOccurrenceCount=2")

    then: "The system responds with a list of 2"
      resp.totalRecords == 2
    and: "Each entry has an occurrence count"
      resp.results.each{result ->
        assert result.occurrenceCount != null

      }
  }

  void "Fetching identifier namespaces"() {
    when:"We fetch identifiers in the system"
      Map resp = doGet("/erm/identifiers/namespaces?stats=true")

    then: "The system responds with a list of 4"
      resp.totalRecords == 4
    and: "The namespaces are as expected"
      ["jstor", "issn", "gokb_id", "gokb_uuid"].each { ns -> {
        assert resp.results.find { id -> id.value == ns } != null
      }}
  }

  void "Testing identifier resource interactions"(
    String resource,
    Integer expectedResourceTotal,
    String resNameProp
  ) {
    when:"We fetch ${resource} records in the system"
      Integer count;
      withTenant {
        // Do this with HQL
        count = Identifier.executeQuery("""
          SELECT COUNT(res.id) FROM ${resource} res
        """.toString())[0]
      }

    then: "The system responds with a list of ${expectedResourceTotal}"
      count == expectedResourceTotal
    when: "We get a record from the system"
      // Do this with HQL -- def because this record could be one of a number of classes
      def res;
      withTenant {
        res = Identifier.executeQuery("""
            SELECT res FROM ${resource} res
          """.toString(), [], [max: 1])[0]
      }
    and: "We fetch identifiers for that record"
      Map resp = doGet("/erm/identifiers?filters=(occurrences.resource.id%3D%3D${res.id})&stats=true")
      String identifierId = resp.results[0]?.id
    then: "There is at least one identifier"
      resp.totalRecords != 0
    when: "We fetch that identifier"
      Map identiferResp = doGet("/erm/identifiers/${identifierId}")
    then: "It is as expected"
      // Fetched identifier matches the one we expected
      assert identiferResp.id == identifierId

      // Has namespace and value
      assert identiferResp.ns.value != null
      assert identiferResp.value != null

      // Has occurrences
      assert identiferResp.occurrences.size() > 0
      assert identiferResp.occurrences.any { io ->
        boolean classMatches = io.resource['class'].contains(resource)
        boolean idMatches =  io.resource.id == res.id
        boolean nameMatches = io.resource[resNameProp] == res[resNameProp];

        // Matches all the criteria
        return classMatches && idMatches && nameMatches
      }

    where:
      resource          | expectedResourceTotal | resNameProp
      'Pkg'             | 1                     | 'name'
      'TitleInstance'   | 792                   | 'name'
      'Work'            | 404                   | 'title'
  }
}


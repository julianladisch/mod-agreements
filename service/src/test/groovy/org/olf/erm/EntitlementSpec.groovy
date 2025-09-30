package org.olf.erm


import grails.plugin.json.view.test.JsonViewTest
import grails.testing.gorm.DataTest
import grails.web.databinding.GrailsWebDataBinder
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class EntitlementSpec extends Specification implements DataTest, JsonViewTest {

  GrailsWebDataBinder binder

  void setupSpec() {
    mockDomains Entitlement
  }
  
  @Unroll
  void 'Test metaClass of entitlement renders properly' () {
    given: 'Entitlement object'
      Entitlement te = new Entitlement([
        reference: 'test_ent'
      ])

    when: 'Entitlement metaClass is bound to'
      def coverageArray = []

      coverageArray << new HoldingsCoverage (startDate: LocalDate.parse("2012-01-01"), endDate: LocalDate.parse("2020-01-01"))
      coverageArray << new HoldingsCoverage (startDate: LocalDate.parse("2015-10-10"))

      te.metaClass.coverage = coverageArray

    then: 'Entitlement renders as expected'
      def result = render(template: "/entitlement/entitlement", model:['entitlement': te])
      assert result.json.coverage[0].startDate == "2012-01-01"
      assert result.json.coverage[0].endDate == "2020-01-01"
      assert result.json.coverage[1].startDate == "2015-10-10"
      assert result.json.coverage[1].endDate == null

  }
}
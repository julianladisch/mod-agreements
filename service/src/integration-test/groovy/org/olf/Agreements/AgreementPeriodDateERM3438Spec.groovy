package org.olf.Agreements


import org.olf.BaseSpec
import grails.testing.mixin.integration.Integration
import org.springframework.security.core.parameters.P
import spock.lang.Unroll

import java.time.LocalDate
import spock.lang.Stepwise
import spock.lang.Shared

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class AgreementPeriodDateERM3438Spec extends BaseSpec {

  @Shared
  List refdataList

  @Shared
  Map respMap

  void "Create global yes/no refdata category"() {
    when: "setup global yes/no refdata category"
      refdataList = doGet('/erm/refdata?filters=desc==Global.Yes_No')
    then: "Good response with refdata values"
      refdataList.size() == 1
      refdataList[0].values.size() == 2
  }

  @Unroll
  void "Setup custom properties"(String type, String name) {
    when: "Post to create a new custom property: ${name}"
      Map custPropPostMap = [
          primary        : true,
          retired        : false,
          defaultInternal: true,
          type           : type,
          label          : name,
          name           : name,
          description    : name,
          weight         : '0',
          ctx            : ''
      ]

      if (type == 'MultiRefdata' || type == 'Refdata') {
        custPropPostMap.category = refdataList[0].id
      }
      respMap = doPost('/erm/custprops', custPropPostMap)

    then: "Good response with ID"
      respMap.id != null
    where:
      type           | name
      'Refdata'      | 'refdataProp'
      'MultiRefdata' | 'multiRefdataProp'
      'Integer'      | 'integerProp'
      'MultiInteger' | 'multiIntegerProp'
  }

  @Unroll
  void "Agreement start date updates as expected"(
      String custProp,
      String agreementName
  ) {
    final LocalDate today = LocalDate.now()
    given: "Agreement is set up"
      Map agreementMap = [
        name           : agreementName,
        agreementStatus: 'active',
        periods        : [
          [startDate: today.toString()]
        ]
      ]

      if (custProp) {
        agreementMap.customProperties = [:]
        agreementMap.customProperties[custProp] = [[
          note      : '123',
          publicNote: '1'
        ]]

        switch (custProp) {
          case 'refdataProp':
            agreementMap.customProperties[custProp][0].value = refdataList[0].values[0]
            break
          case 'multiRefdataProp':
            agreementMap.customProperties[custProp][0].value = [refdataList[0].values[0]]
            break
          case 'integerProp':
            agreementMap.customProperties[custProp][0].value = 1
            break
          case 'multiIntegerProp':
            agreementMap.customProperties[custProp][0].value = [1]
            break
          default:
            agreementMap.customProperties[custProp][0].value = null
            break
        }
      }

      respMap = doPost('/erm/sas', agreementMap)
    when:
      "We subsequently fetch agreement ${agreementName}"
      respMap = doGet("/erm/sas/${respMap.id}")
    then: "Good response containing ID, expected custom properties and correct start date"
      respMap.id != null
      if (custProp) {
        respMap.customProperties[custProp].size() > 0
      } else {
        respMap.customProperties[custProp] == null
      }
      respMap.startDate == today.toString()
      respMap.name == agreementName
    when: "We put to the agreement with a new period start date"
      final LocalDate tomorrow = today.plusDays(1)
      Map agreementToUpdate = respMap
      agreementToUpdate.periods[0].startDate = tomorrow.toString()
      respMap = doPut("/erm/sas/${respMap.id}", agreementToUpdate)
    then: "Response is good and startDate has been updated to the same as period startDate"
      respMap.id != null
      respMap.startDate == tomorrow.toString()
    when:
      "Get agreement: ${agreementName}"
      respMap = doGet("/erm/sas/${respMap.id}")

    // This is the crux of the issue, we would expect the startDate == tomorrow as shown in the previous response,
    // But instead the start date is the "today" variable set prior to the PUT
    then: "Good response and startDate is the same as previously updated period startDate"
      respMap.id != null
      respMap.startDate == tomorrow.toString()

    where:
      custProp            | agreementName
      null                | 'Agreement no custprop'
      'integerProp'       | 'Agreement with single integer custprop'
      'multiIntegerProp'  | 'Agreement with multi integer custprop'
      'refdataProp'       | 'Agreement with single refdata custprop'
      'multiRefdataProp'  | 'Agreement with multi refdata custprop'
  }
}

package org.olf.General

import org.olf.BaseSpec

import grails.testing.mixin.integration.Integration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import spock.lang.Stepwise

@Integration
@Stepwise
class DateParsingSpec extends BaseSpec {
  
  def 'Test various date(/time) string formats' () {
    
    when: 'Create SubscriptionAgreement with current period'
      final Instant today = Instant.now().truncatedTo(ChronoUnit.SECONDS)
      final Instant yesterday = today.minus(1, ChronoUnit.DAYS)
      final Instant tomorrow = today.plus(1, ChronoUnit.DAYS)
      final Instant dayAfter = tomorrow.plus(1, ChronoUnit.DAYS)
      Map resp = doPost('/erm/sas', {
        name 'Test Agreement 1'
        agreementStatus 'Active'
        'periods' ([{
          'startDate' "${yesterday}"
          'endDate' "${dayAfter}"
          'cancellationDeadline' "${tomorrow.atZone(ZoneOffset.UTC).toLocalDate()}"
        }])
      })
      final sas_id = resp.id
    and: 'Refetch'
      resp = doGet("/erm/sas/${sas_id}")
    then: 'Expect dates to be unchanged'
    
      assert resp.cancellationDeadline == "${tomorrow.atZone(ZoneOffset.UTC).toLocalDate()}"
      assert resp.startDate == "${yesterday.atZone(ZoneOffset.UTC).toLocalDate()}"
      assert resp.endDate == "${dayAfter.atZone(ZoneOffset.UTC).toLocalDate()}"
  }
}


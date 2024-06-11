package org.olf.kb

import org.olf.CoverageService
import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.dataimport.erm.CoverageStatement
import java.time.LocalDate

import spock.lang.Specification
import spock.lang.Unroll

class CoverageServiceSpec extends Specification {
  @Unroll
  void 'Test collateCoverageStatements' (final List<CoverageStatementSchema> coverages, final List<CoverageStatementSchema> expectedCoverages) {
    CoverageService coverageService = new CoverageService();

    when: 'collateCoverageStatements is called with #coverages'
      List<CoverageStatementSchema> output = coverageService.collateCoverageStatements(coverages)

    then: 'output matches the expected output'
      assert output.size() == expectedCoverages.size()
      output.eachWithIndex { CoverageStatementSchema cs, index ->
        CoverageStatementSchema relevantExpectedCs = expectedCoverages[index]
        assert relevantExpectedCs.startDate == cs.startDate
        assert relevantExpectedCs.endDate == cs.endDate
      }

    where:
      coverages       | expectedCoverages
      // Test basic case
      [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')])]       | [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')])]
      // Two non-overlapping coverage statements
      [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2021-01-31')])]       | [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2021-01-31')])]
      // Same two statements, but provided in the wrong order
      [new CoverageStatement([startDate: LocalDate.parse('2021-01-31')]), new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')])]       | [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2021-01-31')])]
      // Two coverage statements, one completely absorbed by the other
      [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2019-01-01'), endDate: LocalDate.parse('2022-01-01')])]       | [new CoverageStatement([startDate: LocalDate.parse('2019-01-01'), endDate: LocalDate.parse('2022-01-01')])]
      // Two coverage statements, one completely absorbed by the other with an open end date
      [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2019-01-01')])]       | [new CoverageStatement([startDate: LocalDate.parse('2019-01-01')])]
      // Two overlapping coverage statements
      [new CoverageStatement([startDate: LocalDate.parse('2020-01-01'), endDate: LocalDate.parse('2021-01-01')]), new CoverageStatement([startDate: LocalDate.parse('2020-05-01'), endDate: LocalDate.parse('2022-01-01')])]       | [new CoverageStatement([startDate: LocalDate.parse('2020-01-01'), endDate: LocalDate.parse('2022-01-01')])]
      // Three coverage statements, two disparate and one overlapping
      [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2021-01-12')]), new CoverageStatement([startDate: LocalDate.parse('2019-01-01')])]       | [new CoverageStatement([startDate: LocalDate.parse('2019-01-01')])]
      // Three coverage statements, one before and then one after
      [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2019-01-12'), endDate: LocalDate.parse('2019-01-12')]), new CoverageStatement([startDate: LocalDate.parse('2021-01-01'), endDate: LocalDate.parse('2021-01-01')])]       | [new CoverageStatement([startDate: LocalDate.parse('2019-01-12'), endDate: LocalDate.parse('2019-01-12')]), new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2021-01-01'), endDate: LocalDate.parse('2021-01-01')])]
      // Three coverage statements, one before and then one further before
      [new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')]), new CoverageStatement([startDate: LocalDate.parse('2019-01-12'), endDate: LocalDate.parse('2019-01-12')]), new CoverageStatement([startDate: LocalDate.parse('2001-01-01'), endDate: LocalDate.parse('2001-01-01')])]       | [new CoverageStatement([startDate: LocalDate.parse('2001-01-01'), endDate: LocalDate.parse('2001-01-01')]), new CoverageStatement([startDate: LocalDate.parse('2019-01-12'), endDate: LocalDate.parse('2019-01-12')]), new CoverageStatement([startDate: LocalDate.parse('2020-12-23'), endDate: LocalDate.parse('2020-12-23')])]
  }
}
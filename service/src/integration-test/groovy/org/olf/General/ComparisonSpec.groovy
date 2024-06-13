package org.olf.General

import org.olf.BaseSpec

import org.olf.general.StringTemplate
import org.olf.kb.Platform
import org.olf.kb.PlatformTitleInstance

import com.k_int.okapi.OkapiTenantResolver

import groovy.json.JsonSlurper

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*
import spock.util.concurrent.PollingConditions

import groovy.util.logging.Slf4j

/** ToDo: make this test work with the newer background service apis */
@Ignore
@Slf4j
@Integration
@Stepwise
class ComparisonSpec extends BaseSpec {
  @Shared
  String pkg_id_1

    @Shared
  String pkg_id_2


  // Place to store the id of the PTI we load in the package for use in multiple tests
  void "Load Packages" () {
    when: 'Package 1 loaded'
      def result1 = doPost('/erm/packages/import') {
        header {
          dataSchema {
            name "mod-agreements-package"
            version 1.0
          }
        }
        records ([
          {
            source "Folio Testing"
            reference "comparison_test"
            name "comparison_test Package"
            packageProvider {
              name "DIKU"
            }
            availabilityScope "Global"
            lifecycleStatus "Current"
            sourceDataCreated "2022-01-01"
            sourceDataUpdated "2022-01-01"
            contentItems ([
              {
                depth "fulltext"
                platformTitleInstance {
                  platform "EUP Publishing"
                  platform_url "https://www.euppublishing.com"
                  url "https://www.euppublishing.com/loi/afg"
                  titleInstance {
                    name "Afghanistan"
                    identifiers ([
                      {
                        value "2399-357X"
                        namespace "issn"
                      }
                      {
                        value "2399-3588"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              },
              {
                depth "fulltext"
                platformTitleInstance {
                  platform "Archaeological and Environmental Forensic Science"
                  platform_url "http://www.equinoxjournals.com"
                  url "http://www.equinoxjournals.com/AEFS/"
                  titleInstance {
                    name "Archaeological and Environmental Forensic Science"
                    identifiers ([
                      {
                        value "2052-3378"
                        namespace "issn"
                      }
                      {
                        value "2052-3386"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              },
              {
                depth "fulltext"
                platformTitleInstance {
                  platform "EUP Publishing"
                  platform_url "https://www.euppublishing.com"
                  url "https://www.euppublishing.com/loi/anh"
                  titleInstance {
                    name "Archives of Natural History"
                    identifiers ([
                      {
                        value "0260-9541"
                        namespace "issn"
                      }
                      {
                        value "1755-6260"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              },
              {
                depth "fulltext"
                platformTitleInstance {
                  platform "JSTOR"
                  platform_url "https://www.jstor.org"
                  url "https://www.jstor.org/journal/bethunivj"
                  titleInstance {
                    name "Bethlehem University Journal"
                    identifiers ([
                      {
                        value "2521-3695"
                        namespace "issn"
                      }
                      {
                        value "2410-5449"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              }
            ])
          }
        ])
      }

      pkg_id_1 = result1.packageId

      and: 'Package 2 loaded'
      def result2 = doPost('/erm/packages/import') {
        header {
          dataSchema {
            name "mod-agreements-package"
            version 1.0
          }
        }
        records ([
          {
            source "Folio Testing"
            reference "comparison_test_2"
            name "comparison_test Package 2"
            packageProvider {
              name "DIKU"
            }
            availabilityScope "Global"
            lifecycleStatus "Current"
            sourceDataCreated "2022-01-01"
            sourceDataUpdated "2022-01-01"
            contentItems ([
              {
                depth "fulltext"
                platformTitleInstance {
                  platform "EUP Publishing"
                  platform_url "https://www.euppublishing.com"
                  url "https://www.euppublishing.com/loi/anh"
                  titleInstance {
                    name "Archives of Natural History"
                    identifiers ([
                      {
                        value "0260-9541"
                        namespace "issn"
                      }
                      {
                        value "1755-6260"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              },
              {
                depth "fulltext"
                platformTitleInstance {
                  platform "JSTOR"
                  platform_url "https://www.jstor.org"
                  url "https://www.jstor.org/journal/bethunivj"
                  titleInstance {
                    name "Bethlehem University Journal"
                    identifiers ([
                      {
                        value "2521-3695"
                        namespace "issn"
                      }
                      {
                        value "2410-5449"
                        namespace "eissn"
                      }
                    ])
                    type "serial"
                  }
                }
              }
            ])
          }
        ])
      }
      pkg_id_2 = result2.packageId

    then: 'Packages imported'
      assert pkg_id_1 != null
      assert pkg_id_2 != null
  }



  @Unroll
  def 'Test comparison report is generated' () {
    when: 'Comparison is created'

    String postData = """{
        "name": "test",
        "comparisonPoints": [
          {
            "titleList": "${pkg_id_1}",
            "date": "2021-10-15T00:00:00.000Z"
          },
          {
            "titleList": "${pkg_id_2}",
            "date": "2021-10-15T00:00:00.000Z"
          }
        ]
      }"""

      Map httpResult = doPost("/erm/jobs/comparison", postData)
      def comparisonJobId = httpResult.id

    and: 'Job is polled'
      def conditions = new PollingConditions(timeout: 300)
      conditions.eventually {
        httpResult = doGet("/erm/jobs/${comparisonJobId}")
        assert httpResult?.status?.value == 'ended'
        assert httpResult?.result?.value == 'success'
      }

    and: 'Comparison report is viewed'
      def report = new JsonSlurper().parse(doGet("/erm/jobs/${comparisonJobId}/downloadFileObject"))

    // TODO in future we could scrutinise the completed report to check it's behaving as expected
    then: 'Report is created as expected'
      assert report.size() == 4

  }
}

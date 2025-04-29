package org.olf

import com.k_int.web.toolkit.utils.GormUtils
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

// Need a GORM domain class to perform static API stuffs
import org.olf.kb.ErmResource

@Slf4j
@CurrentTenant
class StatisticsController {
  private static Map<String, Integer> getCounts(List<String> countClasses) {
    Map<String, Integer> counts = [:]
    GormUtils.withTransaction {
      countClasses.each { className ->
        Integer count = GormUtils.gormStaticApi(ErmResource).executeQuery(
            "SELECT COUNT (res) FROM ${className} res".toString()
        )[0]
        counts.put(className, count)
      }
    }

    return counts
  }


  public kbCount() {
    respond getCounts(["ErmResource", "Pkg", "PackageContentItem", "PlatformTitleInstance", "TitleInstance"])
  }

  public agreementCount() {
    respond getCounts(["SubscriptionAgreement", "Entitlement"])
  }
}

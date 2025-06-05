package org.olf.Agreements;

import org.olf.BaseSpec
import org.olf.erm.SubscriptionAgreement
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import spock.lang.*

import groovy.util.logging.Slf4j

import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Slf4j
@Stepwise
abstract class AgreementsBaseSpec extends BaseSpec {
  @Ignore
  Map createAgreement(String name="test_agreement") {
    def today = LocalDate.now()
    def tomorrow = today.plusDays(1)

    def payload = [
      periods: [
        [
          startDate: today.format(DateTimeFormatter.ISO_LOCAL_DATE),
          endDate: tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ]
      ],
      name: name,
      agreementStatus: "active"
    ]

    def response = doPost("/erm/sas/", payload)

    return response as Map
  }

  @Ignore
  Map addEntitlementForAgreement(String agreementName, String resourceId) {
    String agreement_id;
    withTenant {
      String hql = """
            SELECT agreement.id 
            FROM SubscriptionAgreement agreement 
            WHERE agreement.name = :agreementName 
        """
      List results = SubscriptionAgreement.executeQuery(hql, [agreementName: agreementName])
      agreement_id = results.get(0)
    }


    return doPut("/erm/sas/${agreement_id}", {
      items ([
        {
          resource {
            id resourceId
          }
        }
      ])
    }) as Map
  }

  @Ignore
  String getAgreementIdByName(String agreementName) {
    String agreement_id;
    withTenant {
      String hql = """
            SELECT agreement.id 
            FROM SubscriptionAgreement agreement 
            WHERE agreement.name = :agreementName 
        """
      List results = SubscriptionAgreement.executeQuery(hql, [agreementName: agreementName])
      agreement_id = results.get(0)
    }
    return agreement_id
  }

  Pkg findPkgByPackageName(String packageName) {
    log.info("Package name: " + packageName)
    withTenant {
      String hql = """
            SELECT package
            FROM Pkg package
            WHERE package.name = :packageName
        """
      List results = Pkg.executeQuery(hql, [packageName: packageName])
      return results.get(0);
    }
  }

  PackageContentItem findPCIByPackageName(String packageName, String titleName) {
    withTenant {
      String hql = """
            SELECT pci
            FROM PackageContentItem pci
            WHERE pci.pkg.name = :packageName
            AND pci.pti.titleInstance.work.title = :titleName
        """
      List results = PackageContentItem.executeQuery(hql, [packageName: packageName, titleName: titleName])
      return results.get(0);
    }
  }

}

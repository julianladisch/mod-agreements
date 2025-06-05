package org.olf.Agreements
import grails.testing.mixin.integration.Integration
import groovy.util.logging.Slf4j
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import spock.lang.Ignore
import spock.lang.Shared

@Integration
@Slf4j
class AgreementPublicLookupSpec extends AgreementsBaseSpec {
  @Shared
  String pkg_id

  @Ignore
  void assertAgreements(String resourceId, Set<String> expectedAgreements) {
    Collection<Object> records = doGet("/erm/sas/publicLookup?resourceId=${resourceId}").get("records")
    Set<String> observedAgreements = new HashSet<String>();
    for (Object record:records) {
      observedAgreements.add(record.name.toString())
    }

    assert records.size() == expectedAgreements.size();
    assert observedAgreements == expectedAgreements;
  }

  @Shared
  String pci1Id;

  @Shared
  String pti1Id;

  @Shared
  String ti1Id;

  @Shared
  String pci2Id;

  @Shared
  String pti2Id;

  @Shared
  String ti2Id;

  @Shared
  PackageContentItem test_package_1_pci;

  @Shared
  Pkg test_package_1;

  void "Load Packages"() {

    when: 'File loaded'
      Map result = importPackageFromFileViaService('publicLookup/publicLookupPackage1.json')
      importPackageFromFileViaService('publicLookup/publicLookupPackage2.json')
      importPackageFromFileViaService('publicLookup/publicLookupPackage3.json')
      importPackageFromFileViaService('publicLookup/publicLookupPackage4.json')
      importPackageFromFileViaService('publicLookup/publicLookupPackage5.json')
      importPackageFromFileViaService('publicLookup/publicLookupPackage6.json')

    then: 'Package imported'
      result.packageImported == true

    when: "Looked up package with name"
      List resp = doGet("/erm/packages", [filters: ['name==test_package_1']])
      doGet("/erm/packages", [filters: ['name==test_package_2']])
      doGet("/erm/packages", [filters: ['name==test_package_3']])
      doGet("/erm/packages", [filters: ['name==test_package_4']])
      doGet("/erm/packages", [filters: ['name==test_package_5']])
      doGet("/erm/packages", [filters: ['name==test_package_6']])
      pkg_id = resp[0].id

    then: "Package found"
      log.info(resp.toListString())
      resp.size() == 1
      resp[0].id != null
  }

  void "Create Agreements and lines"() {

    when:
      test_package_1 = findPkgByPackageName("test_package_1")
      createAgreement("Agreement A")
      addEntitlementForAgreement("Agreement A", test_package_1.id)

      test_package_1_pci = findPCIByPackageName("test_package_1", "Academy of Management Learning & Education")
      createAgreement("Agreement B")
      addEntitlementForAgreement("Agreement B", test_package_1_pci.id)

      createAgreement("Agreement C")
      Pkg test_package_6 = findPkgByPackageName("test_package_6")
      addEntitlementForAgreement("Agreement C", test_package_6.id)

      createAgreement("Agreement D")
      PackageContentItem test_package_6_pci = findPCIByPackageName("test_package_6", "Academy of Management Learning & Education")
      addEntitlementForAgreement("Agreement D", test_package_6_pci.id)

      createAgreement("Agreement E")
      Pkg test_package_2 = findPkgByPackageName("test_package_2")
      addEntitlementForAgreement("Agreement E", test_package_2.id)

      createAgreement("Agreement F")
      PackageContentItem test_package_2_pci = findPCIByPackageName("test_package_2", "Academy of Management Learning & Education")
      addEntitlementForAgreement("Agreement F", test_package_2_pci.id)

      pci1Id = test_package_1_pci.id
      pti1Id = test_package_1_pci.pti.id
      ti1Id = test_package_1_pci.pti.titleInstance.id

      pci2Id = test_package_2_pci.id
      pti2Id = test_package_2_pci.pti.id
      ti2Id = test_package_2_pci.pti.titleInstance.id

    then:
      log.debug("PCI ID: {}", pci1Id)
      Set pciExpected = ["Agreement A", "Agreement B"] as Set
      assertAgreements(pci1Id, pciExpected)

      log.debug("PTI ID: {}", pti1Id)
      Set ptiExpected = ["Agreement A", "Agreement B", "Agreement D", "Agreement F"] as Set
      assertAgreements(pti1Id, ptiExpected)

      log.debug("TI ID: {}", ti1Id)
      Set tiExpected = ["Agreement A", "Agreement B", "Agreement D", "Agreement F"] as Set
      assertAgreements(ti1Id, tiExpected)
  }

  void "Agreement B activeTo date in the past"() {

    when:
      String agreement_id = getAgreementIdByName("Agreement B")
      Map httpResult = doGet("/erm/sas/${agreement_id}", [expand: 'items'])
      def index = httpResult.items.findIndexOf{ it.resource?.id == test_package_1_pci.id }
      httpResult.items[index].activeFrom = "${thisYear - 2}-01-01"
      httpResult.items[index].activeTo = "${thisYear -1}-12-31"
      httpResult = doPut("/erm/sas/${agreement_id}", httpResult, [expand: 'items'])

    then:
      log.debug("PCI ID: {}", pci1Id)
      Set pciExpected = ["Agreement A"] as Set
      assertAgreements(pci1Id, pciExpected)

      log.debug("PTI ID: {}", pti1Id)
      Set ptiExpected = ["Agreement A", "Agreement D", "Agreement F"] as Set
      assertAgreements(pti1Id, ptiExpected)

      log.debug("TI ID: {}", ti1Id)
      Set tiExpected = ["Agreement A", "Agreement D", "Agreement F"] as Set
      assertAgreements(ti1Id, tiExpected)

  }

  void "Agreement B activeFrom date in the future and no activeTo date"() {
    when:
      String agreement_id = getAgreementIdByName("Agreement B")
      Map httpResult = doGet("/erm/sas/${agreement_id}", [expand: 'items'])
      def index = httpResult.items.findIndexOf{ it.resource?.id == test_package_1_pci.id }
      httpResult.items[index].activeFrom = "${thisYear + 1}-01-01"
      httpResult.items[index].activeTo = ""
      httpResult = doPut("/erm/sas/${agreement_id}", httpResult, [expand: 'items'])

    then:
      log.debug("PCI ID: {}", pci1Id)
      Set pciExpected = ["Agreement A"] as Set
      assertAgreements(pci1Id, pciExpected)

      log.debug("PTI ID: {}", pti1Id)
      Set ptiExpected = ["Agreement A", "Agreement D", "Agreement F"] as Set
      assertAgreements(pti1Id, ptiExpected)

      log.debug("TI ID: {}", ti1Id)
      Set tiExpected = ["Agreement A", "Agreement D", "Agreement F"] as Set
      assertAgreements(ti1Id, tiExpected)

  }

  void "Agreement B no active to/active from dates, Agreement A has activeTo date in the past"() {
    when:
      String agreementBId = getAgreementIdByName("Agreement B")
      String agreementAId = getAgreementIdByName("Agreement A")
      Map httpResultAgreementB = doGet("/erm/sas/${agreementBId}", [expand: 'items'])
      Map httpResultAgreementA = doGet("/erm/sas/${agreementAId}", [expand: 'items'])

      def indexB = httpResultAgreementB.items.findIndexOf{ it.resource?.id == test_package_1_pci.id }
      httpResultAgreementB.items[indexB].activeFrom = ""
      httpResultAgreementB.items[indexB].activeTo = ""
      httpResultAgreementB = doPut("/erm/sas/${agreementBId}", httpResultAgreementB, [expand: 'items'])

      def indexA = httpResultAgreementA.items.findIndexOf{ it.resource?.id == test_package_1.id }
      httpResultAgreementA.items[indexA].activeTo = "${thisYear -1}-12-31"
      httpResultAgreementA = doPut("/erm/sas/${agreementAId}", httpResultAgreementA, [expand: 'items'])

    then:
      log.debug("PCI ID: {}", pci1Id)
      Set pciExpected = ["Agreement B"] as Set
      assertAgreements(pci1Id, pciExpected)

      log.debug("PTI ID: {}", pti1Id)
      Set ptiExpected = ["Agreement B", "Agreement D", "Agreement F"] as Set
      assertAgreements(pti1Id, ptiExpected)

      log.debug("TI ID: {}", ti1Id)
      Set tiExpected = ["Agreement B", "Agreement D", "Agreement F"] as Set
      assertAgreements(ti1Id, tiExpected)
  }

  void "Agreement A has activeFrom date in future and no activeTo date."() {
    when:
      String agreementBId = getAgreementIdByName("Agreement B")
      String agreementAId = getAgreementIdByName("Agreement A")
      Map httpResultAgreementB = doGet("/erm/sas/${agreementBId}", [expand: 'items'])
      Map httpResultAgreementA = doGet("/erm/sas/${agreementAId}", [expand: 'items'])

      def indexB = httpResultAgreementB.items.findIndexOf{ it.resource?.id == test_package_1_pci.id }
      httpResultAgreementB.items[indexB].activeFrom = ""
      httpResultAgreementB.items[indexB].activeTo = ""
      httpResultAgreementB = doPut("/erm/sas/${agreementBId}", httpResultAgreementB, [expand: 'items'])

      def indexA = httpResultAgreementA.items.findIndexOf{ it.resource?.id == test_package_1.id }
      httpResultAgreementA.items[indexA].activeTo = ""
      httpResultAgreementA.items[indexA].activeFrom = "${thisYear + 1}-12-31"
      httpResultAgreementA = doPut("/erm/sas/${agreementAId}", httpResultAgreementA, [expand: 'items'])

    then:
      log.debug("PCI ID: {}", pci1Id)
      Set pciExpected = ["Agreement B"] as Set
      assertAgreements(pci1Id, pciExpected)

      log.debug("PTI ID: {}", pti1Id)
      Set ptiExpected = ["Agreement B", "Agreement D", "Agreement F"] as Set
      assertAgreements(pti1Id, ptiExpected)

      log.debug("TI ID: {}", ti1Id)
      Set tiExpected = ["Agreement B", "Agreement D", "Agreement F"] as Set
      assertAgreements(ti1Id, tiExpected)

  }
}

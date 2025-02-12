package org.olf

import com.k_int.okapi.OkapiHeaders
import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.testing.HttpSpec
import org.olf.dataimport.internal.TitleInstanceResolverService

import org.olf.dataimport.internal.KBManagementBean
import org.olf.dataimport.internal.KBManagementBean.KBIngressType

import org.olf.kb.Pkg
import org.olf.kb.TitleInstance

import grails.gorm.multitenancy.Tenants

import com.k_int.web.toolkit.utils.GormUtils

import groovyx.net.http.HttpException
import spock.lang.Stepwise
import spock.lang.Ignore
import spock.util.concurrent.PollingConditions

import java.time.LocalDate
import groovy.json.JsonSlurper
import spock.lang.*

import groovy.util.logging.Slf4j

// KBART file stuffs
import java.io.FileInputStream
import org.apache.commons.io.input.BOMInputStream

import com.opencsv.ICSVParser
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder

@Slf4j
@Stepwise
abstract class BaseSpec extends HttpSpec {
  // Some helper date shared across specs
  @Shared
  LocalDate today = LocalDate.now()

  @Shared
  int thisYear = today.year

  @Shared
  LocalDate tomorrow = today.plusDays(1)

  @Shared
  LocalDate nextWeek = today.plusWeeks(1)

  private static String baseTIRSPath = "org.olf.dataimport.internal.titleInstanceResolvers"
  static String TITLE_TIRS = "${baseTIRSPath}.TitleFirstTIRSImpl"
  static String ID_TIRS = "${baseTIRSPath}.IdFirstTIRSImpl"
  static String WORK_SOURCE_TIRS = "${baseTIRSPath}.WorkSourceIdentifierTIRSImpl"

  def importService
  def identifierService

  def setupSpec() {
    httpClientConfig = {
      client.clientCustomizer { HttpURLConnection conn ->
        conn.connectTimeout = 120000
        conn.readTimeout = 60000
      }
    }
    addDefaultHeaders(
      (OkapiHeaders.TENANT): "${this.class.simpleName}",
      (OkapiHeaders.USER_ID): "${this.class.simpleName}_user"
    ) 
  }
  
  Map<String, String> getAllHeaders() {
    state.specDefaultHeaders + headersOverride
  }
  
  String getCurrentTenant() {
    allHeaders?.get(OkapiHeaders.TENANT)
  }

  final String getTenantId() {
    currentTenant.toLowerCase()
  }

  @Ignore
  def withTenant(Closure c) {
    Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
      c.call()
    }
  }

  @Ignore
  def withTenantNewTransaction(Closure c) {
    withTenant {
      GormUtils.withNewTransaction {
        c.call()
      }
    }
  }

  void 'Pre purge test tenant'() {
    boolean resp = false
    log.debug("Pre purge tenant");

    when: 'Purge the tenant'
      try {
        resp = doDelete('/_/tenant', null)
        resp = true
      } catch (HttpException ex) { resp = true }
      
    then: 'Response obtained'
      resp == true
  }
  
  void 'Ensure test tenant' () {
		log.debug("Ensure test tenant ${baseUrl}");
    
    when: 'Create the tenant'
      def resp = doPost('/_/tenant', {
      parameters ([["key": "loadReference", "value": true]])
    })

    then: 'Response obtained'
    resp != null

    and: 'Refdata added'

      List list
      // Wait for the refdata to be loaded.
      def conditions = new PollingConditions(timeout: 10)
      conditions.eventually {
        (list = doGet('/erm/refdata')).size() > 0
      }
  }

  // Helper method to nuke tenant as cleanup if necessary
  @Ignore
  void cleanupTenant() {
    def purgeResp;
    try {
      purgeResp = doDelete('/_/tenant', null)
      purgeResp = true
    } catch (HttpException ex) { resp = true }

    def enableResp = doPost('/_/tenant', {
      parameters ([["key": "loadReference", "value": true]])
    })

    // Get a response back from enable call
    def conditions = new PollingConditions(timeout: 20)
    conditions.eventually {
      assert enableResp != null
    }

    // Refdata has been created
    conditions.eventually {
      assert doGet('/erm/refdata').size() > 0
    }
  }

  // Setup shared JsonSlurper
  def jsonSlurper = new JsonSlurper()

  // TIRS gets injected as a spring bean, this can help figure out which is being used
  // Used to work out which TIRS we're using
  TitleInstanceResolverService titleInstanceResolverService
  @Ignore
  def injectedTIRS() {
    titleInstanceResolverService?.class?.name
  }

  // KBManagementBean gets injected as a spring bean
  KBManagementBean kbManagementBean

  // Set up helper methods to import test packages so we don't repeat that code throughout tests
  @Ignore
  def importPackageFromFileViaService(String test_package_file_name, String path = "src/integration-test/resources/packages") {
    String test_package_file = "${path}/${test_package_file_name}";

    def package_data = jsonSlurper.parse(new File(test_package_file))
    Map result = importPackageFromMapViaService(package_data)

    return result;
  }

  @Ignore
  def importPackageFromMapViaService(Map package_data) {
    Map result = [:]
    log.debug("Create new package with tenant ${tenantId}");
    withTenant {
      Pkg.withTransaction { status ->
        result = importService.importFromFile( package_data )
        // This logging causes issues with transactional test methods in WorkSourceIdentifierTIRSSpec -- commenting for now
        //log.debug("Package import complete - num packages: ${Pkg.executeQuery('select count(p.id) from Pkg as p')}");
        //log.debug("                            num titles: ${TitleInstance.executeQuery('select count(t.id) from TitleInstance as t')}");
        /* Pkg.executeQuery('select p.id, p.name from Pkg as p').each { p ->
          log.debug("Package: ${p}");
        } */
      }
    }

    return result;
  }

  @Ignore
  def importKBARTPackageViaService(
      String test_package_file_name,
      String path = "src/integration-test/resources/packages",
      Map packageInfo = [
          packageName: 'testPackage',
          packageSource: 'testSource',
          packageReference: 'testReference',
          packageProvider: 'testProvider',
          trustedSourceTI: true
      ]
  ) {
    boolean result = false
    log.debug("Create new package from KBART with tenant ${tenantId}");
    withTenant {
      Pkg.withTransaction { status ->
        String test_package_file = "${path}/${test_package_file_name}";

        BOMInputStream bis = new BOMInputStream(new FileInputStream(new File(test_package_file)));
        CSVParser parser = new CSVParserBuilder().withSeparator('\t' as char)
            .withQuoteChar(ICSVParser.DEFAULT_QUOTE_CHARACTER)
            .withEscapeChar(ICSVParser.DEFAULT_ESCAPE_CHARACTER)
            .build();

        CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(bis)).withCSVParser(parser).build();
        result = importService.importPackageFromKbart(csvReader, packageInfo)
      }
    }

    return result;
  }
}

package org.olf.General

import org.olf.BaseSpec

import org.olf.kb.Pkg

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration
@Stepwise
class PackageImportSpec extends BaseSpec {

  @Unroll
  void "Test PackageImport via KBART" ( ) {
    when: 'ingest KBART file'

    def result = false
    withTenant {
      result = importKBARTPackageViaService(
          "Testdata_KBART_AnnualReviews.tsv",
          "src/integration-test/resources/packages/KBART",
          [
              packageName: 'KbartImportPackage1',
              packageSource: 'testSource',
              packageReference: 'testReference',
              packageProvider: 'testProvider',
              trustedSourceTI: true
          ]
      )
    }

    then: 'Import succeeded'
    result == true


    when: 'Package subsequently fetched'
    List resp = doGet("/erm/packages", [filters: ['name==KbartImportPackage1']]);
    Map pkg = resp?.getAt(0);

    then: "Single package found and is as expected"
    resp.size() == 1
    pkg.id != null
    // TODO should probably ensure a lot more than just this
    pkg.syncContentsFromSource == true;
  }

  @Unroll
  void "Test PackageImport" ( ) {
    when: 'ingest JSON file'

    def result = [:]
    withTenant {
      result = importPackageFromFileViaService(
          "simple_pkg_1.json"
      )
    }

    then: 'Import succeeded'
    result.packageId != null


    when: 'Package subsequently fetched'
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Test Package 001']]);
    Map pkg = resp?.getAt(0);

    then: "Single package found and is as expected"
    resp.size() == 1
    pkg.id != null
    // TODO should probably ensure a lot more than just this
    pkg.syncContentsFromSource == false;
  }

  @Unroll
  void "Test PackageImport with syncContentsFromSource: false" ( ) {
    when: 'ingest JSON file'

    def result = [:]
    withTenant {
      result = importPackageFromFileViaService(
          "simple_pkg_with_syncContentsFromSource_true.json"
      )
    }

    then: 'Import succeeded'
    result.packageId != null


    when: 'Package subsequently fetched'
    List resp = doGet("/erm/packages", [filters: ['name==K-Int Test Package 002']]);
    Map pkg = resp?.getAt(0);
    then: "Single package found and is as expected"
    resp.size() == 1
    pkg.id != null
    // TODO should probably ensure a lot more than just this
    pkg.syncContentsFromSource == true;
  }
}


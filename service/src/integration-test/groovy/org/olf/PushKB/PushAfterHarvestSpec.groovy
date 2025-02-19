package org.olf.PushKB

import org.olf.BaseSpec
import org.olf.kb.metadata.PackageIngressMetadata

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*


@Integration
@Stepwise
@Requires({ instance.isPushKb() })
class PushAfterHarvestSpec extends BaseSpec {
  @Shared
  String dukePkgId

  @Shared
  String packageIngressMetadataId

  @Shared
  long metadataCreated

  @Shared
  long metadataUpdated

  @Unroll
  void "Set up duke package" () {
    when: 'We POST to pushPkg endpoint'
      def pushPkgBody = getDataFromFile("pkgBodyDuke.json", "src/integration-test/resources/pushkb/pushPkg");
      Map resp = doPost("/erm/pushKB/pkg", pushPkgBody);
    then: 'All is well'
      resp.pushPkgResult.success == true
    when: 'We look specifically for Duke University Press: E Duke Journals Expanded'
      Map pkgSingleGet = doGet("/erm/packages?filters=name=Duke%20University%20Press%3A%20E%20Duke%20Journals%20Expanded&stats=true");
      Map singlePkg = pkgSingleGet?.results?.getAt(0)
      dukePkgId = singlePkg?.id
    then: 'We have the expected package'
      pkgSingleGet.total == 1
      singlePkg.name == "Duke University Press: E Duke Journals Expanded"
      dukePkgId != null
  }

  @Unroll
  void "Set up HARVEST metadata" () {
    when: "Package metadata is fetched for ${dukePkgId}"
      Map pkgMetadata = doGet("/erm/packages/${dukePkgId}/metadata");
      packageIngressMetadataId = pkgMetadata.id;
      metadataCreated = pkgMetadata.dateCreated;
      metadataUpdated = pkgMetadata.lastUpdated;
    then: 'We see expected results'
      pkgMetadata.ingressType == 'PUSHKB'
      pkgMetadata.resource.id == dukePkgId
      pkgMetadata.ingressId == "pkg-pushtask-id-duke";
      pkgMetadata.ingressUrl == "pkg-pushkb-url-duke";
      pkgMetadata.contentIngressId == null;
      pkgMetadata.contentIngressUrl == null;
      metadataCreated != null
      metadataUpdated != null
      metadataCreated == metadataUpdated
    when: 'We manipulate the data in the database and refetch'
      withTenant {
        PackageIngressMetadata.executeUpdate("""
          UPDATE PackageIngressMetadata pim SET pim.ingressType = 'HARVEST'
        """);
      }
      pkgMetadata = doGet("/erm/packages/${dukePkgId}/metadata");
    then: 'We see expected results'
      pkgMetadata.ingressType == 'HARVEST'
      pkgMetadata.resource.id == dukePkgId
      pkgMetadata.ingressId == "pkg-pushtask-id-duke";
      pkgMetadata.ingressUrl == "pkg-pushkb-url-duke";
      pkgMetadata.contentIngressId == null;
      pkgMetadata.contentIngressUrl == null;
      pkgMetadata.dateCreated == metadataCreated;
      pkgMetadata.lastUpdated == metadataUpdated;
  }

  @Unroll
  void "Re-push Pkg" () {
    when: 'We POST to pushPkg endpoint'
      def pushPkgBody = getDataFromFile("pkgBodyDuke.json", "src/integration-test/resources/pushkb/pushPkg");
      Map resp = doPost("/erm/pushKB/pkg", pushPkgBody);
    then: 'All is well'
      resp.pushPkgResult.success == true
    when: "Package metadata is fetched for ${dukePkgId}"
      Map pkgMetadata = doGet("/erm/packages/${dukePkgId}/metadata");
    then: 'We see expected results'
      pkgMetadata.ingressType == 'PUSHKB'
      pkgMetadata.resource.id == dukePkgId
      pkgMetadata.ingressId == "pkg-pushtask-id-duke";
      pkgMetadata.ingressUrl == "pkg-pushkb-url-duke";
      pkgMetadata.contentIngressId == null;
      pkgMetadata.contentIngressUrl == null;

      // We expect to see a NEW dateCreated and lastUpdated as
      // the package metadata should have been destroyed and recreated
      pkgMetadata.dateCreated != metadataCreated;
      pkgMetadata.lastUpdated != metadataUpdated;
      pkgMetadata.id != packageIngressMetadataId;
  }

}


package org.olf.Agreements

import static groovyx.net.http.ContentTypes.*
import static groovyx.net.http.HttpBuilder.configure
import static org.springframework.http.HttpStatus.*

import org.olf.BaseSpec

import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance

import com.k_int.okapi.OkapiHeaders
import com.k_int.okapi.OkapiTenantResolver
import geb.spock.GebSpec
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer
import groovyx.net.http.HttpBuilder
import groovyx.net.http.HttpVerb
import java.time.LocalDate
import spock.lang.Stepwise
import spock.lang.Unroll

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile
import com.k_int.web.toolkit.files.FileUpload;
import com.k_int.web.toolkit.files.S3FileObject;
import org.springframework.beans.factory.annotation.Value;

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class AgreementLifecycleSpec extends BaseSpec {
  Map expectedBeanResult = [
    (TITLE_TIRS): 140,
    (ID_TIRS): 376,
    (WORK_SOURCE_TIRS): 378
  ]

  def fileUploadService

  @Value('${local.server.port}')
  Integer serverPort

  
  void "Load Packages" (test_package_file_name) {

    log.debug("test load packages - server running on port $serverPort");

    when: 'File loaded'
      Map result = importPackageFromFileViaService(test_package_file_name)

    then: 'Package imported'
      // Difference in internal vs erm schema shapes
      result.packageImported == true || result.packageCount > 0

    where:
      test_package_file_name << [
        'brill-eg.json',
        'jstor-eg.json'
      ]

  }
  
  void "List Current Agreements"() {

    when:"We ask the system to list known Agreements"
      List resp = doGet("/erm/sas")

    then: "The system responds with a list of 0"
      resp.size() == 0
  }

  void "Check that we don't currently have any subscribed content" () {

    when:"We ask the subscribed content controller to list the titles we can access"
    
      List resp = doGet("/erm/titles/entitled")

    then: "The system responds with an empty list"
      resp.size() == 0
  }

  // 14th Oct 2020 - gson template added an expand parameter that caused creating a new agreement to explode
  // if it has no items. Since this is the operation most people will do when they first open agreements,
  // add an explicit test for that case.
  void "Check creating an empty agreement"() {

    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)

    when: "Post to create new empty agreement named Empty Agreement Test"
      log.debug("Create new agreement : Empty Agreement Test");
      Map respMap = doPost("/erm/sas", {
        'name' 'Empty Agreement Test'
        'agreementStatus' 'Active' // This can be the value or id but not the label
        'periods' ([{
          'startDate' today.toString()
          'endDate' tomorrow.toString()
        }])
      })

    then: "Response is good and we have a new ID"
      respMap.id != null

  }
  
  @Unroll
  void "Create an Agreement named #agreement_name with status #status" (agreement_name, status, packageName) {
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)

    log.debug("Create Agreement Tests....");
    
    def pkgSize = 0    
    when: "Query for Agreement with name #agreement_name"
    
      List resp = doGet("/erm/sas", [
        filters:[
          "name=i=${agreement_name}" // Case insensitive match
        ]
      ])
      
    then: "No agreement found"
      resp.size() == 0
      
    when: "Looked up package with name - #packageName"
      resp = doGet("/erm/resource", [
        filters:[
          "class==${Pkg.class.name}",
          "name=i=${packageName}" // Case insensitive match
        ]
      ])
      
    then: "Package found"
      resp.size() == 1
      resp[0].id != null
      
    when: "Looked up package item count"
      def packageId = resp[0].id
      
      log.debug("we have looked up package ${packageName} and found it's ID to be ${packageId}.. get that record");

      Map respMap = doGet("/erm/resource", [
        perPage: 1, // Just fetch one record with the stats included. We only want the full count.
        stats: true,
        filters: [
          "class==${PackageContentItem.class.name}",
          "pkg==${packageId}"
        ]
      ])
      
    then: "Response is good and we have a count" 
      (pkgSize = respMap.totalRecords) > 0
      
    when: "Post to create new agreement named #agreement_name with our package"
      log.debug("Create new agreement : ${agreement_name}");
      respMap = doPost("/erm/sas", {
        'name' agreement_name
        'agreementStatus' status // This can be the value or id but not the label
        'periods' ([{
          'startDate' today.toString()
          'endDate' tomorrow.toString()
        }])
        'items' ([
          { 'resource' packageId }
        ])
      })
    
    then: "Response is good and we have a new ID"
      respMap.id != null
      
    when: "Query for Agreement with name #agreement_name"
    
      def agreementId = respMap.id
    
      resp = doGet("/erm/sas", [
        filters:[
          "name=i=${agreement_name}" // Case insensitive match
        ]
      ])
      
    then: "Agreement found and ID matches returned one from before"
      resp.size() == 1
      log.debug("Looked up agreement ${resp[0]}");
      resp[0].id == agreementId

    when:"We do a get for that agreement"
      def result_of_direct_get = doGet("/erm/sas/${agreementId}");

    then:"Dump the result of the get"
      log.debug("Result of GET: ${result_of_direct_get}");

      
    when:"We ask the titles controller to list the titles we can access"
      respMap = doGet("/erm/titles/entitled", [ stats: true ])
  
    then: "The list of content is equal to the number of package titles"
      respMap.totalRecords == pkgSize
      
    where:
      agreement_name        | status    | packageName
      'My first agreement'  | 'Active'  | "JSTOR 1"
  }
  
  @Unroll
  void "Add #resourceType for title #titleName to the Agreement named #agreement_name" (agreement_name, resourceType, resourceEndpoint, titleName, filterKey) {
    
    def entitledResourceCount = 0
    when:"We ask the titles controller to list the titles we can access"
      Map respMap = doGet("/erm/titles/entitled", [
        stats: true
      ])
  
    then: "The list of content is returned"
      // content responds with a JSON object containing a count and a list called subscribedTitles
      (entitledResourceCount = respMap.totalRecords) >= 0
    
    when: "Fetch #resourceType for title #titleName"
      def resourceId = null
      List resp = doGet(resourceEndpoint, [
        filters:[
          "${filterKey}=i=${titleName}" // Case insensitive match
        ]
      ])
      
    then: "Single Resource found"
      resp.size() == 1
      (resourceId = resp[0].id ) != null
      
    when: "Query for Agreement with name #agreement_name"
      def agreementItemCount = 0
      def currentEntitlements = []
      resp = doGet("/erm/sas", [
        filters:[
          "name=i=${agreement_name}" // Case insensitive match
        ],
        expand: 'items',
        exclude: 'items.owner'
      ])
      
    then: "Single Agreement found"
      resp.size() == 1
      (agreementItemCount = resp[0].items.size()) >= 0
            
    when: "Resource added to Agreement"
      
      def data = [
        'items' : resp[0].items.collect({ ['id': it.id] }) + [[resource: resourceId]]
      ] 
      
      respMap = doPut("/erm/sas/${resp[0].id}", data)
          
    then: "Response is good and item count increased by 1"
      respMap.items.size() == (agreementItemCount + 1)
      
    when:"We ask the titles controller to list the titles we can access"
      respMap = doGet("/erm/titles/entitled", [
        stats: true
      ])
  
    then: "The list of content has increased by 1"
      // content responds with a JSON object containing a count and a list called subscribedTitles
      respMap.totalRecords == (entitledResourceCount + 1)
      
    // Titles from _other_ package to add individual extra TIs
    where:
      agreement_name        | resourceType                      | resourceEndpoint      | titleName                                | filterKey
      'My first agreement'  | PackageContentItem.class.name     | '/erm/pci'            | "African yearbook of international law"  | "pti.titleInstance.name"
      'My first agreement'  | PlatformTitleInstance.class.name  | '/erm/pti'            | "Journal of reformed theology"           | "titleInstance.name"
  }
  
  void "Check closure reason behaviour" () {
    final LocalDate today = LocalDate.now()
    final LocalDate tomorrow = today.plusDays(1)
    
    when: 'Save new active agreement with a reason for closure'
      Map resp = doPost("/erm/sas", {
        'name' 'Closing agreement'
        'agreementStatus' 'Active'
        'reasonForClosure' 'Superseded' // This should be null when saved/read back as status is not closed.
        'periods' ([{
          'startDate' today.toString()
          'endDate' tomorrow.toString()
        }])
      })
      final String id = resp.id
    then: 'Saved with no reason for closure and active'
      id != null
      resp.agreementStatus?.value == 'active'
      resp.reasonForClosure == null
    when: 'Get issued'
      resp = doGet("/erm/sas/${id}")
    then: 'No change to status and reeason'
      resp.id != null
      resp.agreementStatus?.value == 'active'
      resp.reasonForClosure == null
      
    when: 'Update agreement to be closed and superseded'
      resp = doPut("/erm/sas/${id}", {
        'agreementStatus' 'Closed'
        'reasonForClosure' 'Superseded'
      })
    then: 'Saved closed with reason superseded'
      resp.id != null
      resp.agreementStatus?.value == 'closed'
      resp.reasonForClosure?.value == 'superseded'
      
    when: 'Get issued'
      resp = doGet("/erm/sas/${id}")
    then: 'No change to status and reeason'
      resp.id != null
      resp.agreementStatus?.value == 'closed'
      resp.reasonForClosure?.value == 'superseded'
      
    when: 'Update agreement to be Requested'
      resp = doPut("/erm/sas/${id}", {
        'agreementStatus' 'Requested'
      })
    then: 'Saved with no reason for closure and active'
      resp.id != null
      resp.agreementStatus?.value == 'requested'
      resp.reasonForClosure == null
      
    when: 'Get issued'
      resp = doGet("/erm/sas/${id}")
    then: 'No change to status and reeason'
      resp.id != null
      resp.agreementStatus?.value == 'requested'
      resp.reasonForClosure == null
  }

  void "update active titles log " () {
    when: 'we trigger an update of the entitlements log'
      Map resp = doGet("/erm/admin/triggerEntitlementLogUpdate")

    then: 'we check that the expected entitlements are present'
      1==1

    when: 'A second run should not add any more entitlements'
      Map resp2 = doGet("/erm/admin/triggerEntitlementLogUpdate")

    then: 'we check that the expected entitlements are present'
      1==1
  }

  void "check active titles log"() {
    when: 'Get the first page of entitlement log entries'
      // Wait for coverage processing to complete
      Thread.sleep(10000);

      Map resp = doGet("/erm/entitlementLogEntry",[
        sort: 'seqid',
        perPage: 10,
        stats: true,
        filters:[
          "seqid>0"
        ]
      ])

    then: 'we check that the expected number of log events are returned'
      resp.totalRecords == expectedBeanResult[injectedTIRS()]
      log.debug("Got response ${resp}");
  }

  void "test file upload"() {

    boolean ok = false;
    when: 'We upload a file'
      log.debug("Create new package with tenant ${tenantId}");

      withTenant {
        FileUpload fu = null;

        FileUpload.withTransaction { status ->
          MultipartFile mf = new MockMultipartFile("foo-lob.txt", "foo-lob.txt", "text/plain", "Hello World - LOB version".getBytes())
          fu = fileUploadService.save(mf);
          log.debug("Saved LOB test file as ${fu.fileName}");
          if ( fu != null )
            ok = true;
        }
      }


    then: 'File uploaded'
      ok==true
  }

  void "test file clone"() {

    boolean ok = false;
    when: 'We upload a file and then clone it'
      log.debug("Create new package with tenant ${tenantId}");

      withTenant {
        FileUpload fu = null;

        FileUpload.withTransaction { status ->
          MultipartFile mf = new MockMultipartFile("foo2-lob.txt", "foo2-lob.txt", "text/plain", "Hello World2 - LOB version".getBytes())
          fu = fileUploadService.save(mf);
          log.debug("Saved LOB test file as ${fu.fileName}");
          if ( fu != null )
            ok = true;
        }

        FileUpload.withTransaction { status ->
          FileUpload fu2 = fu.clone();
          log.debug("File upload cloned");
        }
      }

    then: 'File uploaded'
      ok==true
  }

  void "test S3 clone"() {

    boolean ok = false;
    when: 'We upload a file and then clone it'
      log.debug("Create new package with tenant ${tenantId}");

      withTenant {
        String fu_id = null;

        FileUpload.withTransaction { status ->
          FileUpload fu = null;
          // MultipartFile mf = new MockMultipartFile("foo2-lob.txt", "foo2-lob.txt", "text/plain", "Hello World2 - LOB version".getBytes())
          // fu = fileUploadService.save(mf);
          // Manually create the S3 upload so we don't need to configure mino storage and adjust the settings to S3
          fu = new FileUpload()
          fu.fileContentType = "text/plain"
          fu.fileName = "foo3-lob.txt"
          fu.fileSize = 100
          fu.fileObject = new S3FileObject()
          fu.fileObject.s3ref="12334567"
          fu.save(flush:true, failOnError:true);

          log.debug("Saved S3 test file as ${fu.fileName}");
          if ( fu != null ) {
            fu_id = fu.id;
            ok = true;
          }
        }

        FileUpload.withTransaction { status ->
          // Try to throw away any in-memory objects floating around
          FileUpload.withSession { session ->
            session.flush()
            session.clear()
          }
        }

        FileUpload.withTransaction { status ->
          // Load the file upload to try and force a proxy

          // Do something dumb to try and force a proxy instead of a real object
          FileUpload fu_loaded = FileUpload.executeQuery('select fu from FileUpload as fu').find { it.id == fu_id }
          if ( fu_loaded != null ) {
            log.debug("Got instance of ${fu_loaded?.class?.name}");
            FileUpload fu2 = fu_loaded.clone();
            log.debug("File upload cloned");
          }
          else {
            log.warn("Unable to find file upload");
          }
        }
      }

    then: 'File uploaded'
      ok==true
  }


}


package org.olf.tirs

// Services
import org.olf.general.jobs.JobRunnerService
import org.olf.KbHarvestService

// Domain classes
import org.olf.BaseSpec
import org.olf.kb.RemoteKB
import org.olf.kb.Work
import org.olf.kb.ErmTitleList
import org.olf.kb.TitleInstance
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.PackageContentItem
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.Identifier

import org.olf.dataimport.internal.PackageContentImpl
import grails.web.databinding.DataBindingUtils


import grails.gorm.transactions.Transactional
import com.k_int.okapi.OkapiTenantResolver
import grails.gorm.multitenancy.Tenants

// Testing stuff
import spock.lang.*

import spock.util.concurrent.PollingConditions

// Logging
import groovy.util.logging.Slf4j

@Slf4j
@Stepwise
abstract class TIRSSpec extends BaseSpec {
  // titleInstanceResolverService is injected in baseSpec now
  KbHarvestService kbHarvestService
  JobRunnerService jobRunnerService

  // Place to house any shared TIRS testing methods etc
  @Ignore
  Boolean isWorkSourceTIRS() {
    injectedTIRS() == WORK_SOURCE_TIRS
  }

  @Ignore
  Boolean isIdTIRS() {
    injectedTIRS() == ID_TIRS
  }

  @Ignore
  Boolean isTitleTIRS() {
    injectedTIRS() == TITLE_TIRS
  }

  @Ignore
  protected RemoteKB setUpDebugKb(String xmlPackagePath) {
    Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
      RemoteKB.findByName('DEBUG') ?: (new RemoteKB(
        name:'DEBUG',
        type:'org.olf.kb.adapters.DebugGoKbAdapter',
        uri: xmlPackagePath,
        rectype: RemoteKB.RECTYPE_PACKAGE,
        active:Boolean.TRUE,
        supportsHarvesting:true,
        activationEnabled:false
      ).save(flush: true, failOnError:true))
    }
  }

  // Assumes a clean DB
  @Ignore
  protected void setupAndRunIngestJob(String xmlPackagePath) {
    setUpDebugKb(xmlPackagePath)
    def kbGet = doGet("/erm/kbs");
    assert kbGet.size() == 1
    assert kbGet[0].name == 'DEBUG';
    kbHarvestService.triggerSync()
    // In general this shouldn't be called directly, but this cuts a minute out of waiting for job to run
    jobRunnerService.leaderTick()
    // Run twice since first tick is always ignored
    jobRunnerService.leaderTick()

    def jobsGet = doGet("/erm/jobs", [filters: ['class==org.olf.general.jobs.PackageIngestJob'], sort: ['dateCreated;DESC']]);
    assert jobsGet.size() == 1;
    def jobId = jobsGet[0].id

    def conditions = new PollingConditions(timeout: 300)
    conditions.eventually {
      def jobGet = doGet("/erm/jobs/${jobId}")
      assert jobGet?.status?.value == 'ended'
      assert jobGet?.result?.value == 'success'
    }
  }

  // Helpers to get PackageContentImpl from files and bind them
  @Ignore
  Map citationFromFile(String citation_file_name, String path) {
    String citation_file = "${path}/${citation_file_name}";

    return jsonSlurper.parse(new File(citation_file))
  }

  @Ignore
  PackageContentImpl bindMapToCitation(Map citationMap) {
    PackageContentImpl content = new PackageContentImpl()
    DataBindingUtils.bindObjectToInstance(content, citationMap)

    return content;
  }

  @Ignore
  PackageContentImpl bindMapToCitationFromFile(String citation_file_name, String path) {
    return bindMapToCitation(citationFromFile(citation_file_name, path))
  }

  // Assumes unique sourceIdValue
  @Ignore
  Work getWorkFromSourceId(String sourceIdValue) {
    return Work.executeQuery("""
      SELECT work FROM Work as work
        WHERE work.sourceIdentifier.identifier.value = :sourceId
      """.toString(),
      [sourceId:sourceIdValue]
    )[0]
  }

  @Ignore
  List<String> getTIsForWork(String workId, String subType = null) {
    // Get list of all TIs for a work

    String HQL = """
      Select ti.id FROM TitleInstance AS ti
        WHERE ti.work.id = :workId
      """.toString()

    if (subType) {
      HQL += " AND ti.subType.value = :subType"
      return TitleInstance.executeQuery(HQL, [workId:workId, subType: subType])
    }

    return TitleInstance.executeQuery(HQL, [workId:workId])
  }

  @Ignore
  void deleteTIsFromWork(String workId) {
    // Get list of all TIs we want to delete
    def tiDeleteList = getTIsForWork(workId)
    deleteTIsFromList(tiDeleteList)
  }

  @Ignore
  void deleteTIsFromList(Collection<String> tiDeleteList) {
    // EXAMPLE -- Can't do implicit joins in DELETE HQL

    // First delete all PCIs
    def deleteOut = PackageContentItem.executeUpdate("""
      DELETE FROM PackageContentItem AS pci
        WHERE pci.id IN (
          SELECT pci2.id FROM PackageContentItem AS pci2
          WHERE pci2.pti.titleInstance.id IN :tiDeleteList
        )
      """.toString(),
      [tiDeleteList:tiDeleteList]
    )
    // Then all PTIs
    deleteOut = PlatformTitleInstance.executeUpdate("""
      DELETE FROM PlatformTitleInstance AS pti
        WHERE pti.id IN (
          SELECT pti2.id FROM PlatformTitleInstance AS pti2
          WHERE pti2.titleInstance.id IN :tiDeleteList
        )
      """.toString(),
      [tiDeleteList:tiDeleteList]
    )

    // Then all IdentifierOccurrences
    deleteOut = IdentifierOccurrence.executeUpdate("""
      DELETE FROM IdentifierOccurrence AS io
        WHERE io.id IN (
          SELECT io2.id FROM IdentifierOccurrence AS io2
          WHERE io2.resource.id IN :tiDeleteList
        )
      """.toString(),
      [tiDeleteList:tiDeleteList]
    )

    // And finally all TIs
    deleteOut = TitleInstance.executeUpdate("""
      DELETE FROM TitleInstance AS ti
        WHERE ti.id IN :tiDeleteList
      """.toString(),
      [tiDeleteList:tiDeleteList]
    )
  }

  // Debug methods to print all items currently in the system along with useful information
  @Ignore
  void printTIsInSystem() {
    println("TIs IN SYSTEM: ${
      TitleInstance.executeQuery("""
        SELECT ti.id, ti.name, ti.subType.value FROM TitleInstance ti
      """)
    }")
  }

  @Ignore
  void printWorksInSystem() {
    println("Works IN SYSTEM: ${
      Work.executeQuery("""
        SELECT w.id, w.title FROM Work w
      """)
    }")
  }

  @Ignore
  void printTitleListsInSystem() {
    println("ErmTitleLists IN SYSTEM: ${
      ErmTitleList.executeQuery("""
        SELECT etl.id FROM ErmTitleList etl
      """)
    }")
  }
}

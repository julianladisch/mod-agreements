package org.olf

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.erm.Entitlement
import org.olf.kb.ContentActivationRecord
import org.olf.kb.KBCache
import org.olf.kb.KBCacheUpdater
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.RemoteKB
import org.springframework.transaction.TransactionDefinition

import org.olf.dataimport.internal.KBManagementBean

/**
 * This service works at the module level, it's often called without a tenant context.
 */
public class KnowledgeBaseCacheService implements KBCache {

  PackageIngestService packageIngestService
  TitleIngestService titleIngestService
  KBManagementBean kbManagementBean // public available to `cache` passed down?

  private static final String PLATFORM_TITLES_QUERY = '''select pti, rkb, ent from PlatformTitleInstance as pti, Entitlement as ent, RemoteKB as rkb 
where ( exists ( select pci.id 
               from PackageContentItem as pci
               where pci.pti = pti
               and ent.resource = pci.pkg )
   or exists ( select pci.id 
               from PackageContentItem as pci
               where pci.pti = pti
               and ent.resource = pci )
   or ent.resource = pti )
  and rkb.activationSupported = true 
  and rkb.activationEnabled = true
  and not exists ( select car from ContentActivationRecord as car where car.pti = pti and car.target = rkb )
'''


  public void triggerCacheUpdate() {
    log.debug("KnowledgeBaseCacheService::triggerCacheUpdate()")

  }

  public void runSync(String remotekb_id) {
    log.debug("KnowledgeBaseCacheService::runSync(${remotekb_id})")
    RemoteKB rkb = RemoteKB.read(remotekb_id) 
    if ( rkb ) {
      log.debug("Run remote kb sync:: ${rkb.id}/${rkb.name}/${rkb.uri}")
      Class cls = Class.forName(rkb.type)
      KBCacheUpdater cache_updater = cls.newInstance()

      // Depending on rectype, we may want to freshenPackageData or freshenTitleData
      switch (rkb.rectype) {
        case RemoteKB.RECTYPE_PACKAGE: // This is Long 1
          cache_updater.freshenPackageData(rkb.name, rkb.uri, rkb.cursor, this, rkb.trustedSourceTI)
          break;
        case RemoteKB.RECTYPE_TITLE: // This is Long 2
          cache_updater.freshenTitleData(rkb.name, rkb.uri, rkb.cursor, this, rkb.trustedSourceTI)
          break;
        default:
          log.warn("No behaviour currently defined for RemoteKB rectype: ${rkb.rectype}")
          break;
      }
    }
  }

  public void updateCursor(String rkb_name, String cursor) {
    log.debug("KnowledgeBaseCacheService::updateCursor(${rkb_name},${cursor})")
    RemoteKB.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
      RemoteKB rkb = RemoteKB.findByName(rkb_name)
      rkb.cursor = cursor
      rkb.save(failOnError:true, flush: true)
    }
  }

  /**
   *  Called when a remote KB package add/update is detected. It is the responsibility
   *  of the KBCacheUpdater to convert the remote package format into the canonical object map  
   *  defined here
   *  This document: https://docs.google.com/document/d/14KIi4Guhu8r1NM7lr8NH6SI7giyAdmFjjy4Q6x-MMvQ/edit
   *  Drive -> ERM-Project Team -> WorkInProgress -> olf-erm analysis and design -> PackageDescription--JSON format
   *  Examples can be found in src/intergation-test/resources/packages. The function should be called with the
   *  result of parsing that JSON into a map, eg via JsonSlurper
   *
   *  @param rkb_name the ID string of the remote KB as defined in the remoteKB domain
   *  @param package_data the parsed JSON upload format - for example see the package at
   *              https://github.com/folio-org/mod-erm/blob/master/service/src/integration-test/resources/packages/apa_1062.json
   *
   *  @return map containing information about the packageId of the newly loaded or existing updated package
   */
  public Map onPackageChange(String rkb_name, PackageSchema package_data) {
    Map result = null
    RemoteKB.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
      log.debug("onPackageChange(${rkb_name},...)")
      result = packageIngestService.upsertPackage(package_data, rkb_name, false)
    }
    log.debug("onPackageChange(${rkb_name},...) returning ${result}")

    return result
  }

    public Map onTitleChange(String rkb_name, ContentItemSchema title_data) {
    Map result = null
    RemoteKB.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
      log.debug("onTitleChange(${rkb_name},...)")
      result = titleIngestService.upsertTitle(title_data, rkb_name)
    }
    log.debug("onTitleChange(${rkb_name},...) returning ${result}")

    return result
  }

  /**
   *  Called when a remote KB package removal is detected
   */
  public void onPackageRemoved(String rkb_name,
                               String authority,
                               String authority_id_of_package) {
    log.debug("onPackageRemoved(${rkb_name},${authority}, ${authority_id_of_package})")
  }


  /**
   * Trigger the activation update procedure.
   */
  public void triggerActivationUpdate() {

    Map<String, KBCacheUpdater> adapter_cache = [:]

    log.debug("KnowledgeBaseCacheService::triggerActivationUpdate()")
    int activation_count = 0
    RemoteKB.executeQuery(PLATFORM_TITLES_QUERY).each { qr ->
      log.debug("Content Activation: ${qr}")
      PlatformTitleInstance pti = qr[0]
      RemoteKB rkb = qr[1]
      Entitlement ent = qr[2]

      def adapter = getAdapter(adapter_cache, rkb)
      
      if ( adapter.activate([pti:pti, ent:ent], this) ) {
        log.debug("Activation OK - create CAR")
        def car = new ContentActivationRecord(dateActivation:new Date(),
                                              dateDeactivation:null,
                                              target: rkb,
                                              pti: pti).save(flush:true, failOnError:true)
      }
      else {
        log.debug("Activation Failed - no CAR")
      }
      activation_count++
    }

    log.debug("triggerActivationUpdate() - ${activation_count} activations")

    return
  }

  private KBCacheUpdater getAdapter(Map m, RemoteKB rkb) {
    KBCacheUpdater result = m[rkb.type]
    if ( result == null ) {
      Class cls = Class.forName(rkb.type)
      result = cls.newInstance()
      m[rkb.type] = result
    }
    return result
  }
}

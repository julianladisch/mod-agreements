package org.olf

import org.olf.kb.ErmResource
import org.olf.kb.ErmTitleList
import org.olf.kb.Pkg
import org.olf.kb.TitleInstance
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.PackageContentItem

import static org.springframework.transaction.annotation.Propagation.MANDATORY
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW

import org.olf.erm.Entitlement


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import grails.gorm.transactions.Transactional

/**
 * This service deals with logic that handles updates on content being reflected in entitlements
 */
@Slf4j
@CompileStatic
public class EntitlementService {

  ErmResourceService ermResourceService
  PackageSyncService packageSyncService

  private final static String PCI_HQL = """
    SELECT id FROM PackageContentItem AS pci
    WHERE pci.pti.id = :resId
  """

  private final static String PTI_HQL = """
    SELECT id FROM PlatformTitleInstance AS pti
    WHERE pti.titleInstance.id = :resId
  """

  private final static String ENT_HQL = """
    SELECT ent FROM Entitlement AS ent
    WHERE ent.resource.id = :resId
  """



  @Transactional(propagation = MANDATORY)
  public void handleErmResourceChange(ErmResource res) {
    Date now = new Date();

    List<String> resourcesToQuery = ermResourceService.getFullResourceList(res)

    List<Entitlement> entitlements = [];

    // When ErmResource has changed, update contentUpdated for all entitlements for that resource
//    Entitlement.withNewTransaction {
    resourcesToQuery.each {String resId ->
      entitlements.addAll(
        (List<Entitlement>) Entitlement.executeQuery(ENT_HQL, [resId: resId])
      )
    }

    entitlements.each {
      it.contentUpdated = now
      it.save(failOnError: true)
      }
//    }

  }

  List<Entitlement> findEntitlementsByAuthority(authority=Entitlement.GOKB_RESOURCE_AUTHORITY) {
    return Entitlement.executeQuery("""
          SELECT ent FROM Entitlement AS ent
          WHERE ent.authority = :authorityName""".toString(), [authorityName: authority]) as List<Entitlement>
  }

  Boolean checkExternalReferenceFormat(String reference) {
    if (reference == null) {
      return false
    }

    if (reference.contains(":")) {
      String[] references = reference.split(":");
      try {
        if (references.size() == 2 && references.every(ref -> UUID.fromString(ref))) {
          return true;
        }
      } catch (Exception e) {
        log.error("References not present or not in UUID format: " + e.toString())
      }
    }

    return false;
  }

  @Transactional
  void processExternalEntitlements() {
    findEntitlementsByAuthority(Entitlement.GOKB_RESOURCE_AUTHORITY).forEach { Entitlement entitlement ->
      {
        if (entitlement.getReference() && checkExternalReferenceFormat(entitlement.getReference())) {
          String packageGokbId = entitlement.getReference().split(":")[0]
          String titleGokbId = entitlement.getReference().split(":")[1]
          log.debug("Processing external entitlement for entitlement: {}", entitlement.getId().toString())

          Pkg packageInLocalKb = (Pkg) Pkg.executeQuery(
            """
              SELECT p FROM Pkg p
              WHERE p.id IN (
                SELECT io.resource.id FROM IdentifierOccurrence io
                WHERE io.identifier.value = :resId
                AND io.status.value = 'approved'
                AND io.identifier.ns.value = 'gokb_uuid'
              )
            """.toString(), [resId: packageGokbId])[0] // Should only ever be one...

          TitleInstance titleInstanceInLocalKb = (TitleInstance) TitleInstance.executeQuery(
            """
              SELECT ti FROM TitleInstance ti
              WHERE ti.id IN (
                SELECT io.resource.id FROM IdentifierOccurrence io
                WHERE io.identifier.value = :resId
                AND io.status.value = 'approved'
                AND io.identifier.ns.value = 'gokb_uuid'
              )
            """.toString(), [resId: titleGokbId])[0] // Should only ever be one...

          if (packageInLocalKb) {
            if (packageInLocalKb.getSyncContentsFromSource()) {
              // We have an already synchonrising package in the local KB. Check whether PCI exists in local KB already
              if (titleInstanceInLocalKb) {
                // Now we definitely have TitleInstance, but we need to find the PCI that matches the TI and Package ID
                PackageContentItem pciInLocalKb = PackageContentItem.executeQuery(
                  """
                    SELECT pci FROM PackageContentItem AS pci
                    WHERE pci.pti.titleInstance.id = :tiId
                    AND pci.pkg.id = :pkgId
                  """.toString(),
                  [
                    tiId: titleInstanceInLocalKb.id,
                    pkgId: packageInLocalKb.id
                  ]
                )[0] as PackageContentItem // For now we only expect one result, and if there are more we just take the first.

                if (pciInLocalKb) {
                  log.info("Found internal PCI ({}) for TitleInstance ({}) in Package ({}), updating Entitlement ({}) to be internal.", pciInLocalKb.name, titleInstanceInLocalKb.name, packageInLocalKb.name, entitlement.id)
                  entitlement.setReference(null);
                  entitlement.authority = null;
                  // Internal type is implicit from NULL right now. This is a bit shaky, and potentially needs an
                  // across-the-board rethink, as `type` is a String field. We should look into using an Enum or a
                  // reference data value here, or remodelling the Entitlements wholesale.
                  entitlement.type = null;
                  entitlement.resource = pciInLocalKb;
                  entitlement.resourceName = null;
                  entitlement.save(failOnError: true);
                } else {
                  log.error("No PCI found in local KB for TitleInstance ({}) in Package ({}), leaving Entitlement ({}) as external.", titleInstanceInLocalKb.name, packageInLocalKb.name, entitlement.id)
                }
              } else {
                log.info("Title with GOKB UUID: {} not found in local KB, leaving Entitlement ({}) as external.", titleGokbId, entitlement.id)
              }
            } else {
              log.info("Set package ({}) in local KB matching GOKB Package with UUID: {} to synchronize.", packageInLocalKb.name, packageGokbId)
              // If we find the package in the local KB, but it's not set to sync, set it to sync and wait for next harvest to pull in titles.
              packageSyncService.controlSyncStatus([packageInLocalKb.id], true)
            }
          } else {
            log.info("GOKB Package with UUID {} not found in local KB, leaving Entitlement ({}) as external", packageGokbId, entitlement.id)
          }
        } else {
          log.warn("Could not process Entitlement ({}).", entitlement.id.toString())
        }
      }
    }
  }
}


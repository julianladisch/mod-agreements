package org.olf.general.pushKB

import org.olf.general.IngestException
import org.olf.general.StringUtils

import java.util.concurrent.TimeUnit

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

import org.olf.dataimport.erm.CoverageStatement
import org.olf.dataimport.erm.ErmPackageImpl
import org.olf.dataimport.erm.ContentItem
import org.olf.dataimport.erm.Identifier
import org.olf.dataimport.erm.PackageProvider
import org.olf.dataimport.internal.HeaderImpl
import org.olf.dataimport.internal.InternalPackageImpl
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.KBManagementBean
import org.olf.dataimport.internal.KBManagementBean.KBIngressType

// Have moved to another package to help pull some of this work together, now need to import these beans
import org.olf.UtilityService
import org.olf.PackageIngestService
import org.olf.TitleIngestService
import org.olf.IdentifierService

import org.slf4j.MDC

import org.olf.kb.RemoteKB
import org.olf.kb.Pkg
import org.olf.kb.PackageContentItem
import org.olf.kb.TitleInstance

import com.opencsv.CSVReader

import grails.web.databinding.DataBinder
import static groovy.transform.TypeCheckingMode.SKIP
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.slf4j.MDC


@Slf4j
class PushKBService implements DataBinder {
  UtilityService utilityService
  PackageIngestService packageIngestService
  TitleIngestService titleIngestService
  IdentifierService identifierService

  KBManagementBean kbManagementBean

  // For now this is repeated in packageIngestService
  private static final def countChanges = ['accessStart', 'accessEnd']

  public Map pushPackages(final List<Map> packages) {
    Map result = [
      success: false
    ]
    KBIngressType ingressType = kbManagementBean.ingressType

    if (ingressType == KBIngressType.PushKB) {
      try {
        packages.each { Map record ->
          final PackageSchema package_data = InternalPackageImpl.newInstance();
          bindData(package_data, record)
          if (utilityService.checkValidBinding(package_data)) {

            // Start a transaction -- method in packageIngestService needs this
            Pkg.withSession { currentSess ->
              Pkg.withTransaction {
                Pkg.withNewSession { newSess ->
                  Pkg.withTransaction {
                    // Farm out package lookup and creation to a separate method

                    // These calls mirror what's in upsertPackage but conveniently avoid the
                    // logic which handles TIPPS
                    Pkg pkg = packageIngestService.lookupOrCreatePkg(package_data);
                      // Retain logging information
                      MDC.put('packageSource', pkg.source.toString())
                      MDC.put('packageReference', pkg.reference.toString())

                    // Update identifiers from citation
                    identifierService.updatePackageIdentifiers(pkg, package_data.identifiers)
                  }
                  newSess.clear()
                }
              }
            }
          }
        }
        result.success = true
      } catch (Exception e) {
        log.error("Something went wrong", e);
        result.errorMessage = "Something went wrong: ${e}"
      }
    } else {
      result.errorMessage = "pushPackages not valid when kbManagementBean is configured with type (${ingressType})"
    }

    return result
  }

  public Map pushPCIs(final List<Map> pcis) {
    Map result = [
      success: false,
      startTime: System.currentTimeMillis(),
      titleCount: 0,
      newTitles: 0,
      removedTitles: 0,
      updatedTitles: 0,
      updatedAccessStart: 0,
      updatedAccessEnd: 0,
    ]
    KBIngressType ingressType = kbManagementBean.ingressType
    if (ingressType == KBIngressType.PushKB) {
      try {
        pcis.each { Map record ->

          // Handle MDC directly? Might not be the right approach
          MDC.put('title', StringUtils.truncate(record.title.toString()))

          // Not entirely sure why we would need this and startTime... left for consistency with upsertPackage
          result.updateTime = System.currentTimeMillis()

          final ContentItemSchema pc = PackageContentImpl.newInstance();

          // Ensure electronic
          if (!pc.instanceMedium) {
            pc.instanceMedium = 'Electronic'
          }

          bindData(pc, record)
          if (utilityService.checkValidBinding(pc)) {
            try {
              Pkg pkg = null;
              Pkg.withSession { currentSess ->
                Pkg.withTransaction {
                  Pkg.withNewSession { newSess ->
                    Pkg.withTransaction {
                      // TODO this will allow the PCI data to update the PKG record... do we want this?

                      pkg = packageIngestService.lookupOrCreatePackageFromTitle(pc);
                    }
                    newSess.clear()
                  }
                }
              }

              TitleInstance.withSession { currentSess ->
                TitleInstance.withTransaction {
                  TitleInstance.withNewSession { newSess ->
                    TitleInstance.withTransaction {
                      Map titleIngestResult = titleIngestService.upsertTitleDirect(pc)

                      if ( titleIngestResult.titleInstanceId != null ) {

                        Map hierarchyResult = packageIngestService.lookupOrCreateTitleHierarchy(
                          titleIngestResult.titleInstanceId,
                          pkg.id,
                          true,
                          pc,
                          result.updateTime,
                          result.titleCount // Not totally sure this is valuable here
                        )

                        PackageContentItem pci = PackageContentItem.get(hierarchyResult.pciId)
                        packageIngestService.hierarchyResultMapLogic(hierarchyResult, result, pci)

                        /* TODO figure out if use of removedTimestamp
                         * should be something harvest also needs to do directly
                         * And whether we should be doing it after all the above
                         * or before.
                         */
                        if (pc.removedTimestamp) {
                            try {
                              log.debug("Removal candidate: pci.id #${pci.id} (Last seen ${pci.lastSeenTimestamp}, thisUpdate ${result.updateTime}) -- Set removed")
                              pci.removedTimestamp = pc.removedTimestamp
                              pci.save(failOnError:true)
                            } catch ( Exception e ) {
                              log.error("Problem removing ${pci} in package load", e)
                            }
                          result.removedTitles++
                        }
                      } else {
                        String message = "Skipping \"${pc.title}\". Unable to resolve title from ${pc.title} with identifiers ${pc.instanceIdentifiers}"
                        log.error(message)
                      }
                    }
                    newSess.clear()
                  }
                }
              }

            } catch ( IngestException ie ) {
                // When we've caught an ingest exception, should have helpful error log message
                String message = "Skipping \"${pc.title}\": ${ie.message}"
                log.error(message, ie)
            } catch ( Exception e ) {
              String message = "Skipping \"${pc.title}\". System error: ${e.message}"
              log.error(message,e)
            }

            result.titleCount++
            
          } else {
            // We could log an ending error message here, but the error log messages from checkValidBinding may well suffice
          }

          // Do we really need a running average?
          /* if ( result.titleCount % 100 == 0 ) {
            result.averageTimePerTitle=(System.currentTimeMillis()-result.startTime)/(result.titleCount * 1000)
            log.debug ("(Package in progress) processed ${result.titleCount} titles, average per title: ${result.averageTimePerTitle}s")
          } */
        }

        long finishedTime = (System.currentTimeMillis()-result.startTime) // Don't divide by 1000 here
        result.success = true

        // TODO Logging may need tweaking between pushKB and harvest
        // Currently this is used directly from packageIngestService
        MDC.remove('recordNumber')
        MDC.remove('title')
        packageIngestService.logPackageResults(result, finishedTime);
      } catch (Exception e) {
        log.error("Something went wrong", e);
        result.errorMessage = "Something went wrong: ${e}"
      }
    } else {
      result.errorMessage = "pushPCIs not valid when kbManagementBean is configured with type (${ingressType})"
    }

    return result
  }
}

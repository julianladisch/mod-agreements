package org.olf

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.kb.KBCacheUpdater
import org.olf.kb.RemoteKB
import org.olf.kb.TitleInstance
import org.olf.general.jobs.JobRunnerService

import grails.gorm.transactions.Transactional

class TitleEnricherService {

  public static final ThreadLocal<Set> enrichedIds = new ThreadLocal<Set>()

  public void secondaryEnrichment(RemoteKB kb, String sourceIdentifier, String ermIdentifier) {
    //log.debug("TitleEnricherService::secondaryEnrichment called for title with source identifier: ${sourceIdentifier}, erm identifier: ${ermIdentifier} and RemoteKb: ${kb.name}")
    // Check for existence of sourceIdentifier. LOCAL source imports will pass null here.
    if (sourceIdentifier) {

      // Only bother continuing if the source is trusted for TI metadata and a kb.type exists
      if (kb.trustedSourceTI && kb.type) {
        Class cls = Class.forName(kb.type)
        KBCacheUpdater cache_updater = cls.newInstance()
        // If this KB doesn't require a secondary enrichment call then we can exit here.
        if (cache_updater.requiresSecondaryEnrichmentCall()) {

          TitleInstance ti = TitleInstance.get(ermIdentifier)
          if (ti) {
            Set enrichedIdSet = enrichedIds.get()
            if (!enrichedIdSet) {
              // On first setting we need to initialise to an empty set rather than null
              enrichedIdSet = [];
            }

            if (!enrichedIdSet.contains(ti.id)) {
              // Only perform the enrichment if we've not already stored the id of this TI
              Map titleInstanceEnrichmentValues = cache_updater.getTitleInstance(kb.name, kb.uri, sourceIdentifier, ti?.type?.value, ti?.publicationType?.value, ti?.subType?.value)
              
              // If no enrichment values were returned then break out
              if (titleInstanceEnrichmentValues) {
                if (!saveEnrichmentValues(titleInstanceEnrichmentValues, ti)) {
                  log.info("Secondary enrichment call made for ti with id ${ermIdentifier}, but no updates were made")
                }
                // Store the id of this TI so that it won't run the enrichment call twice for a single TI
                enrichedIdSet.add(ti.id)
                enrichedIds.set(enrichedIdSet)
              }
            }
          } else {
            log.error("Could not find ti with id: ${ermIdentifier}, skipping secondary enrichment call.")
          }
        }
      }
    }
  }


  /*
   * A method to perform an enrichment on a title instance given a map of values of the shape:
   * enrichValues = [
   *   monographEdition: "1st",
   *   monographVolume: "3",
   *   dateMonographPublished: "1976-03-23",
   *   firstAuthor: "Burke",
   *   firstEditor: "Stanley",
   * ]
   *
   * Returns true if updates occur successfully, false otherwise
   */
  public boolean saveEnrichmentValues(Map enrichValues, TitleInstance ti) {
    log.debug("TitleEnricherService::saveEnrichmentValues called for title: ${ti}")
    // Actually perform the enrichment and log any errors with saving

    // Keep track of which fields get updated
    int count = 0;

    if (enrichValues.monographEdition) {
      ti.monographEdition = enrichValues.monographEdition
      count++
    }

    if (enrichValues.monographVolume) {
      ti.monographVolume = enrichValues.monographVolume
      count++
    }

    if (enrichValues.dateMonographPublished) {
      ti.dateMonographPublished = enrichValues.dateMonographPublished
      count++
    }

    if (enrichValues.firstAuthor) {
      ti.firstAuthor = enrichValues.firstAuthor
      count++
    }

    if (enrichValues.firstEditor) {
      ti.firstEditor = enrichValues.firstEditor
      count++
    }
  
    if(! ti.save(flush: true) ) {
      ti.errors.fieldErrors.each {
        log.error("Error saving title. Field ${it.field} rejected value: \"${it.rejectedValue}\".")
      }
      // If the fields didn't update correctly, reset count
      count = 0
    }
    count > 0 ? true : false
  }

}
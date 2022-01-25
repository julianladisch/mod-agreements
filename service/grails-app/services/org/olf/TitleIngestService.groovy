package org.olf

import java.util.concurrent.TimeUnit

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.erm.Identifier

import org.olf.kb.RemoteKB
import org.olf.kb.TitleInstance
import org.slf4j.MDC

import grails.util.GrailsNameUtils
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

import org.olf.dataimport.internal.TitleInstanceResolverService
import org.olf.kb.MatchKey

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
class TitleIngestService implements DataBinder {

  TitleInstanceResolverService titleInstanceResolverService
  TitleEnricherService titleEnricherService


  public Map upsertTitle(ContentItemSchema pc) {
    return upsertTitle(pc, 'LOCAL_TITLE')
  }

  public Map upsertTitle(ContentItemSchema pc, String remotekbname) {
    RemoteKB kb = RemoteKB.findByName(remotekbname)
    TitleInstance.withNewTransaction {
      if (!kb) {
       kb = new RemoteKB( name:remotekbname,
                          rectype: RemoteKB.RECTYPE_TITLE,
                          active: Boolean.TRUE,
                          readonly:readOnly,
                          trustedSourceTI:false).save(flush:true, failOnError:true)
      }

      upsertTitle(pc, kb)
    }
  }

  // Bear in mind the kb's rectype here could be RECTYPE_PACKAGE, if called from packageIngestService
  public Map upsertTitle(ContentItemSchema pc, RemoteKB kb, Boolean trusted = null) {
    log.debug("TitleIngestService::UpsertTitle called")
    def result = [
      startTime: System.currentTimeMillis(),
    ]
    // TODO ERM-1801 Does ContentItemSchema need to be able to say trustedSourceTI or not? eg for manual import where you want it to be able to create but not update TIs
    //Boolean trustedSourceTI = trusted ?: package_data.header?.trustedSourceTI ?: kb.trustedSourceTI

    // If we're not explicitly handed trusted information, default to whatever the remote KB setting is
    Boolean trustedSourceTI = trusted ?: kb.trustedSourceTI
    if (trustedSourceTI == null) {
      // If it somehow remains unset, default to false, but with warning
      log.warn("Could not deduce trustedSourceTI setting for title, defaulting to false")
      trustedSourceTI = false
    }

    result.updateTime = System.currentTimeMillis()

    // resolve may return null, used to throw exception which causes the whole package to be rejected. Needs
    // discussion to work out best way to handle.

    // ERM-1847 Changed assert in TIRS to an explicit exception, which we can catch here. Should stop job from hanging on bad data
    TitleInstance title;
    try {
      title = titleInstanceResolverService.resolve(pc, trustedSourceTI)
    } catch (Exception e){
      log.error("Error resolving title (${pc.title}), skipping ${e.message}")
    }

    if (title != null) {
      /* ERM-1801
        * For now this secondary enrichment step is here rather than the PackageIngestService,
        * as it uses information about electronic vs print which the resolver service might have to separate out first.
        * So even when ingesting a title stream we want to resolve, sort into print vs electronic, then get the TI and enrich based on subType
        */
      String sourceIdentifier = pc?.sourceIdentifier
      titleEnricherService.secondaryEnrichment(kb, sourceIdentifier, title.id);

      // TODO ERM-1799 Do we need to go and find all existing match_key information for this TI and update it here too?

      // Append titleInstanceId to resultList, so we can use it elsewhere to look up titles ingested with this method
      result.titleInstanceId = title.id
      result.finishTime = System.currentTimeMillis()
    } else {
      String message = "Unable to resolve title from ${pc.title} with identifiers ${pc.instanceIdentifiers}"
      log.error(message)
    }

    result
  }
}

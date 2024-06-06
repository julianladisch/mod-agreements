package org.olf.dataimport.internal.titleInstanceResolvers

import org.olf.general.StringUtils

import java.time.temporal.ChronoUnit
import java.time.Instant

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.IdentifierSchema
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.TitleInstance
import org.olf.kb.Work

import grails.gorm.transactions.Transactional
import grails.web.databinding.DataBinder

import groovy.util.logging.Slf4j

import groovy.json.*
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
class WorkSourceIdentifierTIRSImpl extends IdFirstTIRSImpl implements DataBinder {
  boolean fallbackToIdFirstTIRSCache = true;
  Instant fallbackToIdFirstTIRSTimestamp = Instant.EPOCH;

  public boolean fallbackToIdFirstTIRS() {
    if (
      fallbackToIdFirstTIRSTimestamp == Instant.EPOCH ||
      Instant.now() > fallbackToIdFirstTIRSTimestamp.plus(1, ChronoUnit.DAYS)
    ) {
      fallbackToIdFirstTIRSTimestamp = Instant.now();

      boolean isWork = Work.list(max: 1).size() > 0;

      if (!isWork) {
        fallbackToIdFirstTIRSCache = false;
        return false;
      }

      // This assumes that IdentiferOccurrences can only be attached to Works as the sourceIdentifier.
      // If this changes then rework will be needed.
      long noSICount = Work.executeQuery("""
        SELECT COUNT(w) FROM Work w
        LEFT JOIN IdentifierOccurrence io ON io.resource = w.id
        WHERE io.id = null
        """.toString())?.get(0);

/*
      long workCount = Work.executeQuery("""
        SELECT COUNT(w) FROM Work w
      """.toString())?.get(0);
*/

      // For now keep the fallback unless there are NO Works in the system without SI
      if (noSICount == 0) {
        fallbackToIdFirstTIRSCache = false;
        return false;
      }

      fallbackToIdFirstTIRSCache = true;
      return true; 
    }

    return fallbackToIdFirstTIRSCache;
  }

  // We can largely ignore passedTrustedSourceTI, and always assume that passed citations are trusted
  public String resolve(ContentItemSchema citation, boolean passedTrustedSourceTI) {
    log.debug("WorkSourceIdentifierTIRS::resolve(${citation})");
    String result = null;

    // Error out if sourceIdentifier or sourceIdentifierNamespace do not exist
    ensureSourceIdentifierFields(citation);

    List<String> candidate_works = Work.executeQuery("""
      SELECT w.id FROM Work as w
      WHERE
        w.sourceIdentifier.identifier.ns.value = :sourceIdentifierNamespace AND
        w.sourceIdentifier.identifier.value = :sourceIdentifier AND
        w.sourceIdentifier.status.value = '${APPROVED}'
    """.toString(),
    [
      sourceIdentifierNamespace: namespaceMapping(citation.sourceIdentifierNamespace),
      sourceIdentifier: citation.sourceIdentifier
    ])
    boolean fallback = fallbackToIdFirstTIRS();

    switch (candidate_works.size()) {
      case 0:
        // Zero direct matches for work, fall back to baseResolve
        if (fallback) {
          result = fallbackToIdFirstResolve(citation, true);
        } else {
          // Don't fallback in this case
          result = createNewTitleInstanceWithSiblings(citation).id
        }
        break;
      case 1:
        //Work work = candidate_works.get(0);
        result = getTitleInstanceFromWork(citation, candidate_works.get(0))
        break;
      default:
        /*
         * We should NEVER match multiple works with the given
         * sourceIdentifierNamespace and sourceIdentifier
         */ 
        throw new TIRSException(
          "Matched ${candidate_works.size()} with source identifier ${citation.sourceIdentifierNamespace}:${citation.sourceIdentifier}",
          TIRSException.MULTIPLE_WORK_MATCHES
        )
        break;
    }

    return result;
  }

  // We can largely ignore passedTrustedSourceTI, and always assume that passed citations are trusted
  private String fallbackToIdFirstResolve(ContentItemSchema citation, boolean passedTrustedSourceTI) {
    String tiId;
    TitleInstance ti = null;
    /*
     * Could not find a work, fall back to resolve in idFirstTIRS
     * and attempt to set work sourceId on some existing TI.
     *
     * Resolve will either successfully create/match a single TI,
     * in which case we can move forward, or will match multiple and error out
     * We can catch the multiple case, because here we wish to create in that
     * circumstance
     */
    try {
      // If falling back to IdFirstTIRS, do not trust to update TI metadata,
      // as we may match but decide later to create new for this citation
      log.debug("Falling back to IdFirstTIRS")
      tiId = super.resolve(citation, false);
    } catch (TIRSException tirsException) {
      // We treat a multiple title match here as NBD and move onto creation
      if (tirsException.code == TIRSException.MULTIPLE_TITLE_MATCHES) {
        // Logging out where we hit this so we can potentially make decisions from the logs
        log.debug("MULTIPLE_TITLE_MATCHES in IdFirstTIRS. Creating new TitleInstance (${tirsException.message})")
      } else {
        // Any other TIRSExceptions are legitimate concerns and we should rethrow
        throw new TIRSException(tirsException.message, tirsException.code);
      }
    } //Dont catch any other exception, those are legitimate reasons to stop

    /* At this point we should either have a title instance from
     * IdFirstTIRS or still null due to CAUGHT exceptions in TIRS
     */
    if (!tiId) {
      // If we have no TI at this point, create one complete with work etc
      tiId = createNewTitleInstanceWithSiblings(citation).id
    } else {
      /* We _do_ have a TI. Check that the attached work does not have an ID
       * If the attached work _does_ have an id, then we need a whole new work anyway
       */
      Work work = TitleInstance.executeQuery("""
        SELECT ti.work FROM TitleInstance ti
        WHERE ti.id = :tiId
      """, [tiId: tiId]).get(0);

      if (!work) {
        throw new TIRSException(
          "No work found on TI: ${tiId}",
          TIRSException.NO_WORK_MATCH
        )
      }

      switch (work.sourceIdentifier) {
        /* ASSUMPTION has been made here that a Work sourceIdentifier cannot have status
         * error, since we never set it to error through any of our logic. If that changes
         * Then this logic may need tweaks
         */
        case { it == null }:
          /* This is a preexisting TI/Work
           * We got to this TI via IdFirstTIRS resolve, so we know that there
           * is a single electronic TI (If there had been multiple on this
           * path we'd have created a new work after catching the TIRSException
           * above). Hence we know that after setting the Work sourceIdentifier
           * field, the TI we have in hand is the correct one and we can move forwards
           */
          Identifier identifier = Identifier.get(lookupOrCreateIdentifier(citation.sourceIdentifier, citation.sourceIdentifierNamespace));
          IdentifierOccurrence sourceIdentifier = new IdentifierOccurrence([
            identifier: identifier,
            status: IdentifierOccurrence.lookupOrCreateStatus('approved')
          ])
          work.setSourceIdentifier(sourceIdentifier);
          work.save(failOnError: true); // We removed the withNewSession around sibling creation so that unflushed changes here are available to each

          // At this point we are assuming this TI is the right one, allow metadata updates
          checkForEnrichment(tiId, citation, true);

          /*
           * Now we need to do some identifier and sibling wrangling
           * to ensure data is consistent with what's coming in from citation
           */
          updateIdentifiersAndSiblings(citation, work.id)

          break;
        case {
          it != null &&
          (
            work.sourceIdentifier.identifier.value != citation.sourceIdentifier ||
            work.sourceIdentifier.identifier.ns.value != namespaceMapping(citation.sourceIdentifierNamespace)
          )
        }:
          /*
           * At this step we have a work, but it does not match the sourceIdentifier
           * So we need to create a new Work/TI/Siblings set and return that at the end
           */
          tiId = createNewTitleInstanceWithSiblings(citation).id
          break;
        default:
          /*
           * Only case left is sourceIdentifier is not null,
           * and sourceIdentifier matches that of the citation
           *
           * The TI in hand MUST be newly created
           * (Because otherwise we wouldn't be in this path at all,
           * this path begins at not finding a matching work)
           * Since TI is brand new, we can move forward with no wrangling
           */
          break;
      }
    }

    return tiId;
  }

  // TODO We need to check this change to id only works as expected
  private List<String> getTISFromWork(String workId, String subtype = 'electronic') {
    return TitleInstance.executeQuery("""
      SELECT ti.id FROM TitleInstance as ti WHERE
        ti.work.id = :workId AND
        ti.subType.value = '${subtype}'
    """.toString(), [workId: workId]);
  }

  private String getTitleInstanceFromWork(ContentItemSchema citation, String workId) {
    Work work = Work.get(workId);
    String tiId
  
    List<TitleInstance> candidate_tis = getTISFromWork(work.id);
    switch (candidate_tis.size()) {
      case 1:
        tiId = candidate_tis.get(0);
        updateIdentifiersAndSiblings(citation, workId);

        // Also check for enrichment here (always trustedSourceTI within this process)
        checkForEnrichment(candidate_tis.get(0), citation, true);
        break;
      case 0:
        /* There is no electronic TI for this work, create it and siblings
         * I'm not sure this branch will ever get hit
         */
        tiId = createNewTitleInstanceWithSiblings(citation, workId).id

        // This should handle scenario where print siblings already existed
        wrangleSiblings(citation, workId)
        break;
      default:
        // If there are somehow multiple electronic title instances on the work at this stage, error out
        throw new TIRSException(
          "Multiple (${candidate_tis.size()}) electronic title instances found on Work: ${work}, skipping",
          TIRSException.MULTIPLE_TITLE_MATCHES
        )
        break;
    }

    return tiId;
  }

  // Method to wrangle ids and siblings after the fact
  private void updateIdentifiersAndSiblings(ContentItemSchema citation, String workId) {
    Work work = Work.get(workId);

    // First up, wrangle IDs on single electronic title instance.
    // Shouldn't be in a situation where there are multiple, but can't hurt to check again
    List<String> candidate_tis = getTISFromWork(work.id);
    TitleInstance electronicTI;
    switch (candidate_tis.size()) {
      case 1:
        electronicTI = TitleInstance.get(candidate_tis.get(0));
        break;
      case 0:
        throw new TIRSException(
          "No electronic title instances found on Work: ${work}, cannot update identifiers and siblings",
          TIRSException.NO_TITLE_MATCH
        )
        break;
      default:
        // If there are somehow multiple electronic title instances on the work at this stage, error out
        throw new TIRSException(
          "Multiple (${candidate_tis.size()}) electronic title instances found on Work: ${work}, cannot update identifiers and siblings",
          TIRSException.MULTIPLE_TITLE_MATCHES
        )
        break;
    }

    // So, we now have a single electronic TI, make sure all identifiers match those from citation
    updateTIIdentifiers(electronicTI.id, citation.instanceIdentifiers);
    wrangleSiblings(citation, workId)
  }

  private void updateTIIdentifiers(String tiId, Collection<IdentifierSchema> identifiers) {
    TitleInstance ti = TitleInstance.get(tiId)

    // First ensure all identifiers from citation are on TI
    identifiers.each {IdentifierSchema citation_id ->
      IdentifierOccurrence io = ti.identifiers.find { IdentifierOccurrence ti_id ->
        ti_id.identifier.ns.value == namespaceMapping(citation_id.namespace) &&
        ti_id.identifier.value == citation_id.value
      }

      if (!io) {
        // Identifier from citation not on TI, add it
        Identifier id = Identifier.get(lookupOrCreateIdentifier(citation_id.value, citation_id.namespace))
        IdentifierOccurrence newIO = new IdentifierOccurrence([
          identifier: id,
          status: IdentifierOccurrence.lookupOrCreateStatus(APPROVED)
        ])

        ti.addToIdentifiers(newIO);

        // Use saveTitleInstance method
        saveTitleInstance(ti, true, false)
      } else if (io.status.value != APPROVED) {
        io.setStatusFromString(APPROVED)
        io.save(failOnError: true);
      }
    }

    // Next ensure ONLY identifiers from citation are on TI
    ti.identifiers.each { IdentifierOccurrence io ->
      IdentifierSchema ids = identifiers.find { citation_id ->
        io.identifier.ns.value == namespaceMapping(citation_id.namespace) &&
        io.identifier.value == citation_id.value
      }

      if (!ids) {
        // Set status to ERROR
        io.setStatusFromString(ERROR)
        io.save(failOnError: true);
      }
    }
  }

  private void wrangleSiblings(ContentItemSchema citation, String workId) {
    List<PackageContentImpl> siblingCitations = getSiblingCitations(citation);

    /*
     * We are making an ASSUMPTION here that getSiblingCitations
     * will return discrete citations which do not match each other.
     * At the moment it will return one ID per citation. If that changes
     * then we may need to inspect this process.
     */

    // Set up list of siblings, we will remove from this as we match via citations,
    // thus building up a list of unmatchedSiblings we can then remove.
    List<String> unmatchedSiblings = getTISFromWork(workId, 'print');
    
    siblingCitations.each {sibling_citation ->
      // We need previous sibling work to have _taken_
      // in the DB by the time we hit the next sibling citation

      // Match sibling citation to siblings already on the work (ONLY looking at approved identifiers)
      List<String> matchedSiblings = directMatch(sibling_citation.instanceIdentifiers, workId, 'print');

      switch(matchedSiblings.size()) {
        case 0:
          // No sibling found, add it.
          createNewTitleInstance(sibling_citation, workId);
          break;
        case 1:
          // Found single sibling citation, update identifiers and check for enrichment
          String siblingId = matchedSiblings.get(0);
          updateTIIdentifiers(siblingId, sibling_citation.instanceIdentifiers);
          checkForEnrichment(siblingId , sibling_citation, true);
          // We've matched this sibling, remove it from the unmatchedSiblings list.
          unmatchedSiblings.removeIf { it == siblingId }

          // Force save + flush -- necessary
          saveTitleInstance(TitleInstance.get(siblingId));
          break;
        default:
          // Found multiple siblings which would match citation.
          // Remove each from the work and progress
          log.warn("Matched multiple siblings from single citation. Removing from work: ${matchedSiblings}")
          matchedSiblings.each { matchedSibling ->
            TitleInstance matchedSiblingDomainObject = TitleInstance.get(matchedSibling)
            // Mark all identifier occurrences as error
            matchedSiblingDomainObject.identifiers.each {io ->
              io.setStatusFromString(ERROR)
              io.save(failOnError: true);
            }
            // Remove Work
            matchedSiblingDomainObject.work = null;
            saveTitleInstance(matchedSiblingDomainObject);

            // We've matched this sibling, remove it from the unmatchedSiblings list.
            unmatchedSiblings.removeIf { it == matchedSibling }
          }
          break;
      }
    }

    /* Now all sibling_citations exist as expected on the work.
     * Next check whether there are any siblings left on the work
     * that cannot be matched to a citation.
     */
    unmatchedSiblings.each { siblingId ->
      TitleInstance sibling = TitleInstance.get(siblingId);
      // Mark all identifier occurrences as error
      sibling.identifiers.each {io ->
        io.setStatusFromString(ERROR)
        io.save(failOnError: true);
      }
      // Remove Work
      sibling.work = null;
      sibling.save(failOnError: true);
    }
  }
}

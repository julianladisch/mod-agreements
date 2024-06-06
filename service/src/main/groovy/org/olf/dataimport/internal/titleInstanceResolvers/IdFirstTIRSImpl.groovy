package org.olf.dataimport.internal.titleInstanceResolvers

import org.olf.general.StringUtils

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
class IdFirstTIRSImpl extends BaseTIRS implements DataBinder {
  public String resolve(ContentItemSchema citation, boolean trustedSourceTI) {
    log.debug("IdFirstTIRS::resolve(${citation})");
    String result = null;

    List<String> candidate_list = classOneMatch(citation.instanceIdentifiers);
    int num_matches = candidate_list.size()
    int num_class_one_identifiers = countClassOneIDs(citation.instanceIdentifiers);
    
    // Ensure logging messages accurately reflect _which_ matching attempt
    // caused the multiple title match problem or which match led to the exact match
    String multiple_match_message;
    String match_vector = "classOneMatch";

    if ( num_matches > 1 ) {
      multiple_match_message="Class one match found ${num_matches} records::${candidate_list}";
    }

    // We weren't able to match directly on an identifier for this instance - see if we have an identifier
    // for a sibling instance we can use to narrow down the list.
    if ( num_matches == 0 ) {
      match_vector = "siblingMatch"
      candidate_list = siblingMatch(citation)
      num_matches = candidate_list.size()
      if ( num_matches > 1 ) {
        multiple_match_message="Sibling match found ${num_matches} records::${candidate_list}"
      }
    }

    // If we didn't have a class one identifier AND we weren't able to match anything via
    // a sibling match, try to do a fuzzy match as a last resort
    // DO NOT ATTEMPT if there is no title on the citation
    if ( ( num_matches == 0 ) && ( num_class_one_identifiers == 0 ) && citation.title ) {
      match_vector = "fuzzy title match"
      candidate_list = titleMatch(citation.title,MATCH_THRESHOLD);
      num_matches = candidate_list.size()
      if ( num_matches > 1 ) {
        multiple_match_message="Title fuzzy matched ${num_matches} records with a threshold >= ${MATCH_THRESHOLD}::${candidate_list}"
      }
    }

    if ( candidate_list != null ) {
      switch ( num_matches ) {
        case(0):
          log.debug("No title match, create new title ${citation}")
          result = createNewTitleInstanceWithSiblings(citation).id
          break;
        case(1):
          log.debug("Exact match via ${match_vector}.${trustedSourceTI ? ' Enrich title.' : ''}")
          result = candidate_list.get(0);

          // This isn't logically necessary, but will cut down on a call to checkForEnrichment
          // for WorkSourceIdTIRS per title.
          if (trustedSourceTI) {
            checkForEnrichment(candidate_list.get(0), citation, trustedSourceTI);
          }
          break;
        default:
          throw new TIRSException(
            "${multiple_match_message}. Class one identifier count: ${num_class_one_identifiers}",
            TIRSException.MULTIPLE_TITLE_MATCHES,
          );
          break;
      }
    }

    return result;
  }

  /* This method WILL NOT set previously ERRORed sibling identifiers back to APPROVED
   * This won't matter for IdFirstTIRS as this method is only called for new TIs
   * But if called externally, such as by WorkSourceIdentifierTIRS, if that behaviour is
   * expected, it will need to be performed externally too
   */
  protected void upsertSiblings(ContentItemSchema citation, String workId) {
    List<String> candidate_list = []

    // Lets try and match based on sibling identifiers. 
    // Our first "alternate" matching strategy. Often, KBART files contain the ISSN of the print edition of an electronic work.
    // The line is not suggesting that buying an electronic package includes copies of the physical item, its more a way of saying
    // "The electronic item described by this line relates to the print item identified by X".
    // In the bibframe nomenclature, the print and electronic items are two separate instances. Therefore, creating an electronic
    // identifier with the ID of the print item does not seem sensible. HOWEVER, we would still like to be able to be able to match
    // a title if we know that it is a sibling of a print identifier.
    List<PackageContentImpl> siblingCitations = getSiblingCitations(citation);
    if ( siblingCitations.size() != 0 ) {
      // One sibling for each citation
      siblingCitations.each { sibling_citation ->
        // Find ALL siblings on this work who match this identifier (should only be one id because of above code)
        candidate_list = directMatch(sibling_citation.instanceIdentifiers, workId, 'print', false)

        switch ( candidate_list.size() ) {
          case 0:
            log.debug("Create sibling print instance for citation ${sibling_citation}")
            createNewTitleInstance(sibling_citation, workId)
            break
          case 1:
            // Already exists somehow -- should not be possible on IdFirstTIRS as we create sibling along with work
            log.warn("Sibling already exists for identifiers: ${sibling_citation.instanceIdentifiers} on Work ${workId}.")
            break;
          default:
            // Problem -- DEFINITELY should not see this one
            log.warn("Detected multiple records for sibling instance match")
            break;
        }
      }
    }
  }

  protected TitleInstance createNewTitleInstance(final ContentItemSchema citation, String workId = null) {
    TitleInstance result = null;

    result = createNewTitleInstanceWithoutIdentifiers(citation, workId)
    citation.instanceIdentifiers.each { id ->
      Identifier id_lookup = Identifier.get(lookupOrCreateIdentifier(id.value, id.namespace));
      def io_record = new IdentifierOccurrence(
        status: IdentifierOccurrence.lookupOrCreateStatus('approved'),
        identifier: id_lookup
      ).save(failOnError: true)

      result.addToIdentifiers(io_record)
    }

    if (result != null) {
      // Refresh the newly minted title so we have access to all the related objects (eg Identifiers)
      saveTitleInstance(result);
    }
    result
  }

  // Setting to public so we can reuse this in WorkSourceIdentifierTIRS
  protected TitleInstance createNewTitleInstanceWithSiblings(ContentItemSchema citation, String workId = null) {
    TitleInstance result;
    result = createNewTitleInstance(citation, workId)
    if (result != null) {
      // We assume that the incoming citation already has split ids and siblingIds
      upsertSiblings(citation, result.work.id)
    }

    return result
  }

  /* -------- MATCHING METHODS --------*/

  protected static final float MATCH_THRESHOLD = 0.775f
  protected static final String TEXT_MATCH_TITLE_HQL = '''
   SELECT ti.id from TitleInstance as ti
    WHERE 
      trgm_match(ti.name, :qrytitle) = true
      AND similarity(ti.name, :qrytitle) > :threshold
      AND ti.subType.value like :subtype
    ORDER BY similarity(ti.name, :qrytitle) desc
  '''

  protected String getDirectMatchHQL(Collection<IdentifierSchema> identifiers, String workId = null, boolean approvedIdsOnly = true) {
    String identifierHQL = buildIdentifierHQL(identifiers, approvedIdsOnly)

    // TODO Direct match (via identifierHQL) assumes single identfier I think... not sure this is right
    String outputHQL = """
      SELECT ti.id FROM TitleInstance as ti
      JOIN ti.identifiers as io
      WHERE
        ${identifierHQL} AND
        ti.subType.value = :subtype
    """

    if (workId !== null) {
      outputHQL += """ AND
        ti.work.id = '${workId}'
      """
    }
    return outputHQL
  }

  /*
   * Being passed a map of namespace, value pair maps, attempt to locate any title instances with class 1 identifiers (ISSN, ISBN, DOI)
   */
  protected List<String> classOneMatch(final Iterable<IdentifierSchema> identifiers) {
    // We want to build a list of all the title instance records in the system that match the identifiers. Hopefully this will return 0 or 1 records.
    // If it returns more than 1 then we are in a sticky situation, and cleverness is needed.
    final List<String> result = new ArrayList<String>()

    int num_class_one_identifiers = 0;

    identifiers.each { IdentifierSchema id ->
      if ( class_one_namespaces?.contains(id.namespace.toLowerCase()) ) {

        num_class_one_identifiers++;

        // Look up each identifier
        // log.debug("${id} - try class one match");


        /*
         * At this stage we could be trying to match incoming
        {
          namespace: 'eISSN',
          value: 1234-5678
        }
         * with something that in our system looks like:
        {
          namespace: 'issn',
          value: 1234-5678
        }
         * We have to know that eissn == issn etc... Use namespaceMapping function
         */

        /* NOTE: We have the possibility for Kiwi that an existing TI is in place with namespace eissn,
         * since cleanup is a complicated matter.
         * For the time being, allow matching BOTH of `eissn` -> `issn` (As per the story),
         * but also `eissn` -> `eissn` AND `issn` -> `eissn` in our db.
         * To allow for `eissn` -> `eissn` the (ns:id.namespace) case is sufficient.
         * To allow for `issn` -> `eissn` we also need
         */
        final List<Identifier> id_matches = Identifier.executeQuery("""
          SELECT id FROM Identifier AS id
          WHERE
            (
              id.ns.value = :nsm OR
              id.ns.value = :ns OR
              id.ns.value = :ens OR
              id.ns.value = :pns
            ) AND
            id.value = :value""".toString(),
          [
            value:id.value,
            ns:id.namespace.toLowerCase(),
            nsm:namespaceMapping(id.namespace),
            ens:mapNamespaceToElectronic(id.namespace),
            pns:mapNamespaceToPrint(id.namespace)
          ],
          [max:2]
        )

        if (id_matches.size() > 1) {
          throw new TIRSException(
            "Multiple (${id_matches.size()}) matches found for identifier ${id.namespace}::${id.value}",
            TIRSException.MULTIPLE_IDENTIFIER_MATCHES,
          );
        }

        // For each matched (It should only ever be 1)
        id_matches.each { matched_id ->
          // For each occurrence where the STATUS is APPROVED
          matched_id.occurrences.each { io ->
            // Read in titleInstance directly
            TitleInstance foundTI = TitleInstance.read(io.resource.id);

            if (
              io.status?.value == APPROVED && // Ensure APPROVED (as above)
              !result.contains(foundTI.id) && // If we've already seen this title, don't add it again
              foundTI.subType.value == "electronic" // We restrict to electronic, so _all_ of these matching processes will return electronic titles only
            ) {
              // log.debug("Adding title ${io.resource.id} ${io.resource.title} to matches for ${matched_id}");
              result << foundTI.id
            }
          }
        }
      }
      else {
        // log.debug("Identifier ${id} not from a class one namespace");
      }
    }

    // log.debug("At end of classOneMatch, resut contains ${result.size()} titles");
    return result;
  }

  /**
   * Attempt a fuzzy match on the title -- returns IDs now
   */
  protected List<String> titleMatch(String title, float threshold) {
    return titleMatch(title, threshold, 'electronic');
  }

  protected List<String> titleMatch(final String title, final float threshold, final String subtype) {
    String matchTitle = StringUtils.truncate(title);

    List<String> result = new ArrayList<String>()
    TitleInstance.withSession { session ->
      try {
        result = TitleInstance.executeQuery(TEXT_MATCH_TITLE_HQL,[qrytitle: (matchTitle),threshold: (threshold), subtype:subtype], [max:20])
      }
      catch ( Exception e ) {
        log.debug("Problem attempting to run HQL Query ${TEXT_MATCH_TITLE_HQL} on string ${matchTitle} with threshold ${threshold}",e)
      }
    }
 
    return result
  }

  // Direct match will find ALL title instances which match ANY of the instanceIdentifiers passed. Is extremely naive.
  // Allow for non-approved identifiers if we wish
  protected List<String> directMatch(final Iterable<IdentifierSchema> identifiers, String workId = null, String subtype = 'electronic', boolean approvedIdsOnly = true) {
    if (identifiers.size() <= 0) {
      return []
    }
    List<String> titleList = TitleInstance.executeQuery(getDirectMatchHQL(identifiers, workId, approvedIdsOnly),[subtype: subtype]);
    return listDeduplictor(titleList)
  }

  /**
   * Return a list of the siblings for this instance. Sometimes vendors identify a title by citing the issn of the print edition.
   * we model the print and electronic as 2 different title instances, linked by a common work. This method looks up/creates any sibling instances
   * by matching the print instance, then looking for a sibling with type "electronic"
   */
  protected List<String> siblingMatch(ContentItemSchema citation) {
    Collection<IdentifierSchema> classOneIds = citation.siblingInstanceIdentifiers.findAll { class_one_namespaces?.contains(it.namespace.toLowerCase()) };
    // Break out if no sibling instance ids
    if (classOneIds.size() <= 0) {
      return []
    }

    String siblingIdentifierHQL = buildIdentifierHQL(classOneIds);
    String siblingsHQL = """
      SELECT sibling.work.id FROM TitleInstance as sibling 
        JOIN sibling.identifiers as io
      WHERE 
        ${siblingIdentifierHQL}
    """

    List<String> siblingWorks = TitleInstance.executeQuery(siblingsHQL);

    String titlesHQL = """
      SELECT ti.id FROM TitleInstance as ti
      WHERE ti.subType.value = 'electronic' AND
      ti.work.id IN :works
    """
        
    List<String> titleList = TitleInstance.executeQuery(titlesHQL, [works: siblingWorks]);
    return listDeduplictor(titleList)
  }
}

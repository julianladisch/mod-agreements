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

import org.olf.dataimport.internal.TitleInstanceResolverService

import java.util.StringTokenizer


import groovy.json.*

import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
class TitleFirstTIRSImpl extends BaseTIRS implements TitleInstanceResolverService {
  /* normalizedName is created using StringUtils.normaliseWhitespaceAndCase, as below */
  private static final String TEXT_MATCH_TITLE_HQL = '''
      SELECT ti from TitleInstance as ti
        WHERE 
          ti.normalizedName = :queryTitle
          AND ti.subType.value like :subtype
      '''

  private static final String ID_OCCURENCE_MATCH_HQL = '''
    SELECT res.name from IdentifierOccurrence as io
    LEFT JOIN io.resource as res
      WHERE
        io.status.value = :approved
        AND io.identifier.id = :id_id
    '''

  private static final String SIBLING_LOOKUP_HQL = '''
    SELECT ti from TitleInstance as ti
      LEFT JOIN ErmResource as res
        ON ti.id = res.id
      LEFT JOIN RefdataValue as rdv
        ON res.subType = rdv.id
      WHERE
        rdv.value = :subType
          AND
        ti.work.id = :work
          AND
        ti.id != :tiId
      '''

  private List<TitleInstance> titleMatch(final String title, final String subtype) {
    List<TitleInstance> result = new ArrayList<TitleInstance>()
    TitleInstance.withSession { session ->
      try {
        result = TitleInstance.executeQuery(
          TEXT_MATCH_TITLE_HQL,
          [
            queryTitle: StringUtils.normaliseWhitespaceAndCase(title),
            subtype:subtype
          ],
          [
            max:20
          ]
        )
      }
      catch ( Exception e ) {
        log.debug("Problem attempting to run HQL Query ${TEXT_MATCH_TITLE_HQL} on string ${title} with subtype ${subtype}",e)
      }
    }
 
    return result
  }

  private List<TitleInstance> titleMatch(String title) {
    return titleMatch(title, 'electronic');
  }

  /* SubType here is the type of sibling you want to find. So for siblings of TI:
   * [
   *   id: 12345,
   *   work: [
   *     id: abcde
   *   ],
   *   subType: [
   *     value: 'electronic'
   *   ]
   * ]
   * You would call siblingMatch(12345, abcde, 'print')
  */
  private List<TitleInstance> siblingMatch(final String tiId, final String workId, final String subType) {
    List<TitleInstance> result = new ArrayList<TitleInstance>()
    TitleInstance.withSession { session ->
      try {
        // TODO This might return multiple siblings, for now we just grab the first one and treat that as "THE" print sibling
        result = TitleInstance.executeQuery(
          SIBLING_LOOKUP_HQL,
          [
           tiId: tiId,
           work: workId,
           subType: subType
          ],
          [
            max:1
          ]
        )
      }
      catch ( Exception e ) {
        log.debug("Problem attempting to run HQL Query ${SIBLING_LOOKUP_HQL} with tiID ${tiId}, work: ${workId} and subtype ${subtype}",e)
      }
    }
 
    return result
  }

  private List<TitleInstance> siblingMatch(final String tiId, final String workId) {
    return siblingMatch(tiId, workId, 'print');
  }

  private List<TitleInstance> classOneMatch(final Iterable<IdentifierSchema> identifiers, final List<String> titleCandidateIds = []) {
    // If given a list of current title candidates' ids then we will only search for the given identifiers on those TIs
    final List<TitleInstance> result = new ArrayList<TitleInstance>()

    identifiers.each { IdentifierSchema id ->
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
      String IO_MATCH_HQL = """
        SELECT io FROM IdentifierOccurrence AS io
          LEFT JOIN io.identifier AS id
          LEFT JOIN io.resource AS res
          WHERE
            id.value = :value AND
            id.ns.value = :ns AND
            io.status.value = :approved"""

      String IO_MATCH_TITLE_IDS = """ AND
            res.id IN :titleCandidateIds
      """

      if (titleCandidateIds.size() > 0) {
        IO_MATCH_HQL += IO_MATCH_TITLE_IDS
      }

      final List<IdentifierOccurrence> io_matches = IdentifierOccurrence.executeQuery(
        IO_MATCH_HQL.toString(),
        [
          value: id.value,
          ns: namespaceMapping(id.namespace),
          approved: APPROVED,
          titleCandidateIds: titleCandidateIds
        ],
        [max:2]
      )

      // For each matched IdentifierOccurrence, add title if we haven't already
      io_matches.each {io ->
        if ( !result.contains(io.resource) ) {
          result << io.resource
        }
      }
    }

    return result;
  }

  public TitleInstance resolve (ContentItemSchema citation, boolean trustedSourceTI) {
    TitleInstance result = null;

    List<TitleInstance> candidate_list = titleMatch(citation.title, citation.instanceMedium)

    if ( candidate_list != null ) {

      if (candidate_list.size() > 1) {
        // If we initially have multiple matches, then use IDs as a secondary matching tactic.
        candidate_list = classOneMatch(citation.instanceIdentifiers, candidate_list.collect { it.id });
      }

      switch ( candidate_list.size() ) {
        case(0):
          log.debug("No title match, create new title")
          result = createNewTitleInstance(citation)
          if (result != null && (citation.siblingInstanceIdentifiers?.size() ?: 0) > 0) {
            createPrintSibling(citation, result.work)
          }
          break;
        case(1):
          log.debug("Exact match. Enrich title.")
          result = candidate_list.get(0)
          checkForEnrichment(result, citation, trustedSourceTI)

          // Link any new identifiers
          linkIdentifiers(result, citation)
          List<TitleInstance> siblings = siblingMatch(result.id, result.work.id)
          // TODO As above, we for now treat the first result as "THE" print sibling
          linkIdentifiers(siblings[0], citation, true)




          break;
        default:
          // This should be caught by calling code
          throw new RuntimeException("Unable to uniquely match title ${citation.title} with identifiers: ${citation.instanceIdentifiers}. Matched IDs: ${candidate_list.collect { it.id }}.");
          break;
      }
    }

    return result;
  }

  private TitleInstance createPrintSibling(final ContentItemSchema citation, Work work = null) {
    TitleInstance result = null;

    /* We can ignore the instance identifiers for now as we create the TI without them initially */
    PackageContentImpl sibling_citation = new PackageContentImpl([
      "title": citation.title,
      "instanceMedium": "print",
      "instanceMedia": citation.instanceMedia,
      "instancePublicationMedia": citation.instancePublicationMedia
    ])

    TitleInstance.withNewTransaction {
      result = createNewTitleInstanceWithoutIdentifiers(sibling_citation, work)
    }

    // This will assign the siblingInstanceIds to the sibling TI
    linkIdentifiers(result, citation, true)
    
    if (result != null) {
      // Refresh the newly minted title so we have access to all the related objects (eg Identifiers)
      result.refresh()
    }
    result
  }

  private TitleInstance createNewTitleInstance(final ContentItemSchema citation, Work work = null) {
    TitleInstance result = null;

    TitleInstance.withNewTransaction {
      result = createNewTitleInstanceWithoutIdentifiers(citation, work)
    }

    // This will assign the instanceIds to the TI and 'createOrLinkSiblings' will create a sibling for each sibling id
    linkIdentifiers(result, citation)

    if (result != null) {
      // Refresh the newly minted title so we have access to all the related objects (eg Identifiers)
      result.refresh()
    }
    result
  }

  // When method passed with sibling = true, link Sibling identifiers, else link identifiers
  private void linkIdentifiers(TitleInstance title, ContentItemSchema citation, boolean sibling = false) {
    IdentifierOccurrence.withNewTransaction {
      if (sibling) {
        citation.siblingInstanceIdentifiers.each { id -> linkIdentifier(id, title, citation) }
      } else {
        citation.instanceIdentifiers.each { id -> linkIdentifier(id, title, citation) }
      }
    }
  }

  private void linkIdentifier(IdentifierSchema id, TitleInstance title, ContentItemSchema citation) {
    // Lookup or create identifier. If not already on an approved IdentifierOccurrence we'll need to create it anyway
    def id_lookup = lookupOrCreateIdentifier(id.value, id.namespace);

    ArrayList<IdentifierOccurrence> io_lookup = IdentifierOccurrence.executeQuery(
      ID_OCCURENCE_MATCH_HQL,
      [
        id_id: id_lookup?.id,
        approved:APPROVED
      ], [max:20]
    );

    if (io_lookup.size() < 1) {
      // We have no approved IOs linked to TIs with that identifier information. Create one

      def io_record = new IdentifierOccurrence(
        resource: title,
        identifier: id_lookup)
      
      io_record.setStatusFromString(APPROVED)
      io_record.save(flush:true, failOnError:true)

    } else {
      // Log warning allows for multiple TIs to have the same identifier through different occurences, I don't believe this should happen in production though
      log.info("Identifier ${id} not assigned to ${title.name} as it is already assigned to title${io_lookup.size() > 1 ? "s" : ""}: ${io_lookup}")
      // TODO Ethan -- do we want to create an IdentifierOccurrence with status "Error" here rather than ignoring?
    }
  }
}

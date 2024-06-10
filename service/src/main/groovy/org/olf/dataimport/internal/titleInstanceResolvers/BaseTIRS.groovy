package org.olf.dataimport.internal.titleInstanceResolvers

import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.IdentifierService

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.IdentifierSchema

import org.olf.kb.IdentifierOccurrence
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace
import org.olf.kb.TitleInstance
import org.olf.kb.Work

import org.olf.dataimport.internal.TitleInstanceResolverService

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil


/**
 * This is a base TIRS class to give any implementing classes some shared tools to use 
 * IMPORTANT -- This is not a TIRS by itself, as it does not implement the TitleInstanceResolverService interface
 */
@Slf4j
@Transactional
abstract class BaseTIRS implements TitleInstanceResolverService {
  // Resolve should find/create/update a TitleInstance from a citation, then return its ID
  public abstract String resolve(ContentItemSchema citation, boolean trustedSourceTI);

  @Autowired
  IdentifierService identifierService
  protected static final def APPROVED = 'approved'
  protected static final def ERROR = 'error'

  // ERM-1649. This function acts as a way to manually map incoming namespaces onto known namespaces where we believe the extra information is unhelpful.
  // This is also the place to do any normalisation (lowercasing etc).
  protected String namespaceMapping(String namespace) {
    identifierService.namespaceMapping(namespace)
  }

    protected static def class_one_namespaces = [
    'zdb',
    'isbn',
    'issn',  // This really isn't true - we get electronic items identified by the issn of their print sibling.. Needs thought
    'eissn', // We want to accept eissn still as a class-one-namespace, even though internally we flatten to 'issn'. ERM-1649
    'doi'
  ];

  protected ArrayList<String> lookupIdentifier(final String value, final String namespace) {
    return Identifier.executeQuery("""
      SELECT iden.id from Identifier as iden
        where iden.value = :value and iden.ns.value = :ns
      """.toString(),
      [value:value, ns:namespaceMapping(namespace)]
    );
  }

  /*
   * Given an identifier in a citation { value:'1234-5678', namespace:'isbn' } lookup or create an identifier in the DB to represent that info
   */
  protected String lookupOrCreateIdentifier(final String value, final String namespace) {
    String result = null;

    // Ensure we are looking up properly mapped namespace (pisbn -> isbn, etc)
    def identifier_lookup = lookupIdentifier(value, namespace);

    switch(identifier_lookup.size() ) {
      case 0:
        IdentifierNamespace ns = lookupOrCreateIdentifierNamespace(namespace);
        result = new Identifier(ns:ns, value:value).save(failOnError:true, flush: true).id;
        break;
      case 1:
        result = identifier_lookup[0];
        break;
      default:
        throw new TIRSException(
          "Matched multiple identifiers for ${id}",
          TIRSException.MULTIPLE_IDENTIFIER_MATCHES
        );
        break;
    }
    return result;
  }

  /*
   * This is where we can call the namespaceMapping function to ensure consistency in our DB
   */
  protected IdentifierNamespace lookupOrCreateIdentifierNamespace(final String ns) {
    IdentifierNamespace.findOrCreateByValue(namespaceMapping(ns)).save(failOnError:true)
  }


  /*
   * Check to see if the citation has properties that we really want to pull through to
   * the DB. In particular, for the case where we have created a stub title record without
   * an identifier, we will need to add identifiers to that record when we see a record that
   * suggests identifiers for that title match.
   */ 
  protected void checkForEnrichment(String tiId, ContentItemSchema citation, boolean trustedSourceTI) {
    TitleInstance title = TitleInstance.get(tiId)
    
    log.debug("Checking for enrichment of Title Instance: ${title} :: trusted: ${trustedSourceTI}")
    def changes = 0;
    
    if (trustedSourceTI == true) {
      log.debug("Trusted source for TI enrichment--enriching")

      if (title.name != citation.title) {
        title.name = citation.title
        changes++
      }

      /*
      * For some reason whenever a title is updated with just refdata fields it fails to properly mark as dirty.
      * The below solution of '.markDirty()' is not ideal, but it does solve the problem for now.
      *
      * "publication instance media" refers to the remote site's own instructions about what an instance actually is,
      * as opposed to monograph/serial which are our internal controlled ones. Since we are only in this method if we
      * have marked this source as "trusted" for updating title medata (ie they are the 'owners' of this title) we allow
      * the updating of this field with whatever the institution wishes to call these things.
      *
      *
      * TL;DR we don't care what's in this field since we do our logic based on a controlled "instanceMedia" field instead.
      */
      String citationIPM = citation.instancePublicationMedia ? RefdataValue.normValue(citation.instancePublicationMedia) : null;
      if (title.publicationType?.value != citationIPM) {
        if (citationIPM) {
          title.publicationTypeFromString = citationIPM
        } else {
          title.publicationType = null;
        }

        title.markDirty()
        changes++
      }

      if (validateCitationType(citation?.instanceMedia)) {
        if ((title.type == null) || (title.type.value != citation.instanceMedia)) {
          if (citation.instanceMedia) {
            title.typeFromString = citation.instanceMedia
          } else {
            title.type = null;
          }

          title.markDirty()
          changes++
        }
      } else {
        log.error("Type (${citation.instanceMedia}) does not match 'serial' or 'monograph' for title \"${citation.title}\", skipping field enrichment.")
      }

      def enrichmentFields = [
        'dateMonographPublished',
        'firstAuthor',
        'firstEditor',
        'monographEdition',
        'monographVolume'
      ];

      enrichmentFields.each { field -> 
        if (title[field] != citation[field]) {
          title[field] = citation[field] ?: null
          changes++
        }
      }

      // Ensure we only save title on enrich if changes have been made
      if (changes > 0) {
        saveTitleInstance(title)
      }

    } else {
      log.debug("Not a trusted source for TI enrichment--skipping")
    }

		log.debug("Enrichment completed");
    return;
  }

  protected boolean validateCitationType(String tp) {
    return tp != null && ( tp.toLowerCase() == 'monograph' || tp.toLowerCase() == 'serial' )
  }

  protected void saveTitleInstance(TitleInstance title, boolean failOnError = true, boolean flush = true) {
    if (!title.save(failOnError:failOnError, flush: flush)) {
      title.errors.fieldErrors.each {
        log.error("Error saving title. Field ${it.field} rejected value: \"${it.rejectedValue}\".")
      }
    }
  }

  // Different TIRS implementations will have different workflows with identifiers, but the vast majority of the creation will be the same
  // We assume that the incoming citation already has split ids and siblingIds
  protected TitleInstance createNewTitleInstanceWithoutIdentifiers(final ContentItemSchema citation, String workId = null) {
    Work work = workId ? Work.get(workId) as Work : null;
    TitleInstance result = null

    // Ian: adding this - Attempt to make sense of the instanceMedia value we have been passed
    //
    // I'm entirely befuddled by whats going on in this service with the handling of instanceMedia, resource_type and instancePublicationMedia -
    // it's a confused mess. This method is about fuzzily absorbing a citation doing the best we can. To reject an entry out of hand because a value
    // does not match an arbitrarily internally decided upon string leaves callers with no way of resolving what went wrong or what to do about it.
    // I'm adding this to make the integraiton tests pass again, and try to regain some sanity.
    // It would be more sensible to stick with the single instanceMedia field and if the value is not one we expect, stash the value in
    // a memo field here and convert as best we can.

    // Journal or Book etc
    def resource_type = citation.instanceMedia?.trim()

    // This means that publication type can no longer be set directly by passing in instanceMedia - that 
    // cannot be the right thing to do.
    def resource_pub_type = citation.instancePublicationMedia?.trim()


    switch( citation.instanceMedia?.toLowerCase() ) {
      case null: // No value, nothing we can do
        break;
      case 'serial': // One of our approved values
        break;
      case 'monograph': // One of our approved values
        break;
      case 'newspaper':
      case 'journal':
        // If not already set, stash the instanceMedia we are looking at in instancePublicationMedia
        // citation.instanceMedia = 'serial';
        resource_type = 'serial'
        resource_pub_type = citation.instancePublicationMedia ?: citation.instanceMedia ?: 'serial'
        break;
      case 'BKM':
      case 'book':
        // If not already set, stash the instanceMedia we are looking at in instancePublicationMedia
        // citation.instanceMedia = 'monograph';
        resource_type = 'monograph'
        resource_pub_type = citation.instancePublicationMedia ?: citation.instanceMedia ?: 'monograph'
        break;
      default:
        log.warn("Unhandled media type ${citation.instanceMedia}");
        break;
    }

    // With the introduction of fuzzy title matching, we are relaxing this constraint and
    // will expect to enrich titles without identifiers when we next see a record. BUT
    // this needs elaboration and experimentation.

    // Validate
    Map title_is_valid = [
      titleExists: ( citation.title != null ) && ( citation.title.length() > 0 ),
      typeMatchesInternal: validateCitationType(resource_type)
    ]

    if ( title_is_valid.count { k,v -> v == false} == 0 ) {

      if ( work == null ) {
        // This is only necessary because harvest does not seem to validate package schema. We should not hit this issue for pushKB
        // Error out if sourceIdentifier or sourceIdentifierNamespace do not exist
        ensureSourceIdentifierFields(citation);

        Identifier identifier = Identifier.get(lookupOrCreateIdentifier(citation.sourceIdentifier, citation.sourceIdentifierNamespace));
        IdentifierOccurrence sourceIdentifier = new IdentifierOccurrence([
          identifier: identifier,
          status: IdentifierOccurrence.lookupOrCreateStatus('approved')
        ])

        // Can you assign to incoming method param like this??
        work = new Work([
          title:citation.title,
          sourceIdentifier: sourceIdentifier
        ]).save(failOnError:true)
      }

      // Print or Electronic
      def medium = citation.instanceMedium?.trim()

      // FIXME This seems to be unused
      def resource_coverage = citation?.coverage
      result = new TitleInstance(
        name: citation.title,

        dateMonographPublished: citation.dateMonographPublished,
        firstAuthor: citation.firstAuthor,
        firstEditor: citation.firstEditor,
        monographEdition: citation.monographEdition,
        monographVolume: citation.monographVolume,

        work: work
      )

      // We can trust these by the check above for file imports and through logic in the adapters to set pubType and type correctly
      result.typeFromString = resource_type

      if ( ( resource_pub_type != null ) && ( resource_pub_type.length() > 0 ) ) {
        result.publicationTypeFromString = resource_pub_type
      }
      
      if ((medium?.length() ?: 0) > 0) {
        result.subTypeFromString = medium
      }
      
      saveTitleInstance(result)
     // Leave identifier work out, as this may differ between implementations of "resolve" method
    }
    else {

      // Run through the failed validation one by one and throw relavent errors
      if (!title_is_valid.titleExists) {
        log.error("Create title failed validation check - insufficient data to create a title record");
      }

      if (!title_is_valid.typeMatchesInternal) {
        log.error("Create title \"${citation.title}\" failed validation check - type (${citation.instanceMedia.toLowerCase()}) does not match 'serial' or 'monograph'");
      }
      
      // We will return null, which means no title
      // throw new RuntimeException("Insufficient detail to create title instance record");
    }

    result
  }

  protected String buildIdentifierHQL(Collection<IdentifierSchema> identifiers, boolean approvedIdsOnly = true) {
    String identifierHQL = identifiers.collect { id -> 
      // Do we need all the namespace mapping variants?
      String mainHQLBody = """(
          (
            io.identifier.ns.value = '${id.namespace.toLowerCase()}' OR
            io.identifier.ns.value = '${namespaceMapping(id.namespace)}' OR
            io.identifier.ns.value = '${mapNamespaceToElectronic(id.namespace)}' OR
            io.identifier.ns.value = '${mapNamespaceToPrint(id.namespace)}'
          ) AND
          io.identifier.value = '${id.value}'
      """

      if (!approvedIdsOnly) {
        return """${mainHQLBody}
          )
        """
      }

      return """${mainHQLBody} AND
          io.status.value = '${APPROVED}'
        )
      """
      // TODO This doesn't look right
    }.join("""
      AND
    """)

    return identifierHQL
  }

  protected int countClassOneIDs(final Iterable<IdentifierSchema> identifiers) {
    identifiers?.findAll( { IdentifierSchema id -> class_one_namespaces?.contains( id.namespace.toLowerCase() ) })?.size() ?: 0
  }

  // On the rare chance that we have `eissn` in our db (From before Kiwi namespace flattening)
  // We attempt to map an incoming `issn` -> `eissn` in our DB
  protected String mapNamespaceToElectronic(final String incomingNs) {
    String output;
    switch (incomingNs.toLowerCase()) {
      case 'issn':
        output = 'eissn'
        break;
      case 'isbn':
        output = 'eisbn'
      default:
        break;
    }

    output
  }

  // On the rare chance that we have `pissn` in our db (From before Kiwi namespace flattening)
  // We attempt to map an incoming `issn` -> `pissn` in our DB
  protected String mapNamespaceToPrint(final String incomingNs) {
    String output = incomingNs.toLowerCase();
    switch (incomingNs.toLowerCase()) {
      case 'issn':
        output = 'pissn'
        break;
      case 'isbn':
        output = 'pisbn'
      default:
        break;
    }

    output
  }

  // We choose to set up a sibling citation per siblingInstanceIdentifier -- keep consistent between TIRSs
  protected List<PackageContentImpl> getSiblingCitations(final ContentItemSchema citation) {
    Collection<IdentifierSchema> ids = citation.siblingInstanceIdentifiers ?: []

    if ( ids.size() == 0 ) {
      return []
    }

    // Duplication check
    Collection<IdentifierSchema> deduplicatedIds = ids.unique(false) { a,b ->
      namespaceMapping(a.namespace) <=> namespaceMapping(b.namespace) ?:
      a.value <=> b.value
    }

    if (deduplicatedIds.size() !== ids.size()) {
      log.warn("Duplicated sibling identifiers found: ${ids}. Continuing with deduplicated list.")
    }

    return deduplicatedIds.collect { id ->
      PackageContentImpl sibling_citation = new PackageContentImpl()
      bindData (sibling_citation, [
        "title": citation.title,
        "instanceMedium": "print",
        "instanceMedia": (namespaceMapping(id.namespace) == 'issn') ? "serial" : "monograph",
        "instancePublicationMedia": citation.instancePublicationMedia,
        "instanceIdentifiers": [
          [
            // This should be dealt with inside the "createTitleInstance" method, 
            // but for now we can flatten it here too
            "namespace": namespaceMapping(id.namespace),
            "value": id?.value
          ]
        ]
      ])

      // Will ONLY include dateMonographPublished if identifier is an isbn
      if (namespaceMapping(id.namespace) == 'isbn') {
        bindData (sibling_citation, [
          "dateMonographPublished": citation.dateMonographPublishedPrint
        ])
      }

      return sibling_citation
    }
  }

  // Dedeuplicate a list of Ids
  protected List<String> listDeduplictor(List<String> ids) {
    // Need to deduplicate output -- Could probably be neater code than this
    List<String> outputList = [];
    ids.each { obj ->
      if (!outputList.contains(obj)) {
        outputList << obj
      }
    }

    outputList
  }

  protected void ensureSourceIdentifierFields(final ContentItemSchema citation) {
    if (!citation.sourceIdentifier) {
      throw new TIRSException(
        "Missing source identifier",
        TIRSException.MISSING_MANDATORY_FIELD
      )
    } else if (!citation.sourceIdentifierNamespace) {
      throw new TIRSException(
        "Missing source identifier namespace",
        TIRSException.MISSING_MANDATORY_FIELD
      )
    }
  }
}

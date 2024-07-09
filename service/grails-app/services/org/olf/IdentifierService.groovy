package org.olf

import org.olf.kb.IdentifierException

import org.olf.kb.IdentifierNamespace
import org.olf.kb.Identifier
import org.olf.kb.IdentifierOccurrence
import org.olf.kb.Pkg
import org.olf.kb.TitleInstance

import com.k_int.web.toolkit.refdata.RefdataValue

import org.grails.orm.hibernate.cfg.GrailsHibernateUtil

import static groovy.transform.TypeCheckingMode.SKIP
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
// Cannot @CompileStatic while using DomainClass.lookupOrCreate${upperName} static method for RefdataValues
public class IdentifierService {

  private static final String IDENTIFIER_OCCURRENCE_MATCH_HQL = '''
    SELECT io from IdentifierOccurrence as io
    WHERE 
      io.resource.id = :initialTitleInstanceId AND
      io.identifier.ns.value = :identifierNamespace AND
      io.identifier.value = :identifierValue AND
      io.status.value = :status
  '''

  /*
    This method accepts an ArrayList of Maps of the form:
    [
      [
        identifierNamespace: "ISSN",
        identifierValue: "12345",
        targetTitleInstanceId: "abcde-12345-fghij",
        initialTitleInstanceId: "jihgf-54321-edcba"
      ],
      ...
    ]

    It will attempt to "reassign" each IdentifierOccurence in turn to the new TitleInstance
    Reassignation will actually consist of the IdentifierOccurence in question
    being marked as "ERROR", and a new Occurrence being created on the targetTI
  */
  def reassignFromFile (final ArrayList<Map<String, String>> reassignmentQueue) {
    reassignmentQueue.each{reassignmentMap ->
      IdentifierOccurrence.withNewTransaction{
        TitleInstance initialTI = TitleInstance.get(reassignmentMap.initialTitleInstanceId)
        TitleInstance targetTI = TitleInstance.get(reassignmentMap.targetTitleInstanceId)
        
        // Check that we could find the specified titleinstances
        if (targetTI != null & initialTI != null) {
          // Now look up an IdentifierOccurrence for the correct set of information
          List<IdentifierOccurrence> identifierOccurrences = IdentifierOccurrence.executeQuery(
            IDENTIFIER_OCCURRENCE_MATCH_HQL,
            [
              initialTitleInstanceId: reassignmentMap.initialTitleInstanceId,
              identifierNamespace: reassignmentMap.identifierNamespace,
              identifierValue: reassignmentMap.identifierValue.toLowerCase(),
              status: 'approved'
            ]
          )
          // Should only be one of these -- check and error out otherwise
          switch (identifierOccurrences.size()) {
            case 0:
              log.error("IdentifierOccurrence could not be found for (${reassignmentMap.identifierNamespace}:${reassignmentMap.identifierValue}) on initial TitleInstance.")
              break;
            case 1:
              IdentifierOccurrence identifierOccurrence = identifierOccurrences[0];
              // We have identified the single IO we wish to "move" to another TI

              // First we mark the current identifier occurrence as "error"
              identifierOccurrence.status = lookupOrCreateStatus('error');
              identifierOccurrence.save(failOnError: true)

              // Next we create a new IdentifierOccurrence on the targetTI
              IdentifierOccurrence newIdentifierOccurrence = new IdentifierOccurrence(
                identifier: identifierOccurrence.identifier,
                resource: targetTI,
                status: lookupOrCreateStatus('approved')
              ).save(failOnError: true)

              log.info("(${reassignmentMap.identifierNamespace}:${reassignmentMap.identifierValue}) IdentifierOccurrence for TI (${initialTI}) marked as ERROR, new IdentifierOccurrence created on TI (${targetTI})")

              break;
            default:
              log.error("Multiple valid IdentifierOccurrences matched for (${reassignmentMap.identifierNamespace}:${reassignmentMap.identifierValue}) on initial TitleInstance (${initialTI}).")
          }
        } else {
          if (initialTI == null) {
            log.error("TitleInstance could not be found for initialTitleInstanceId (${reassignmentMap.initialTitleInstanceId}).")
          }
          if (targetTI == null) {
            log.error("TitleInstance could not be found for targetTitleInstanceId (${reassignmentMap.targetTitleInstanceId}).")
          }
        }
      }
    }
  }

  // ERM-1649. This function acts as a way to manually map incoming namespaces onto known namespaces where we believe the extra information is unhelpful.
  // This is also the place to do any normalisation (lowercasing etc).
  public String namespaceMapping(String namespace) {

    String lowerCaseNamespace = namespace.toLowerCase()
    String result = lowerCaseNamespace
    switch (lowerCaseNamespace) {
      case 'eissn':
      case 'pissn':
      case 'eisbn':
      case 'pisbn':
        // This will remove the first character from the namespace
        result = lowerCaseNamespace.substring(1)
        break;
      default:
        break;
    }

    result
  }

  @CompileStatic(SKIP)
  RefdataValue lookupOrCreateStatus(String status) {
    return IdentifierOccurrence.lookupOrCreateStatus(status);
  }

  @CompileStatic(SKIP)
  public void updatePackageIdentifiers(Pkg pkg, List<org.olf.dataimport.erm.Identifier> identifiers) {
    // Assume any package identifier information is the truth, and upsert/delete as necessary
    IdentifierOccurrence.withTransaction {
      // Firstly add any new identifiers from the identifiers list,
      // and keep a track of the relevant ids in the database of all the identifiers passed in by the process
      def identifiers_to_keep = [];

      identifiers.each {ident ->

        if ( ( ident.namespace != null ) && ( ident.value != null ) ) {
          IdentifierOccurrence existingIo = IdentifierOccurrence.executeQuery("""
            SELECT io FROM IdentifierOccurrence as io
            WHERE io.resource.id = :pkgId AND
              io.identifier.ns.value = :ns AND
              io.identifier.value = :value
          """.toString(), [pkgId: pkg.id, ns: ident.namespace, value: ident.value])[0]
  
          if (!existingIo || existingIo.id == null) {
            IdentifierNamespace ns = IdentifierNamespace.findByValue(ident.namespace) ?: new IdentifierNamespace([value: ident.namespace]).save(flush: true, failOnError: true)
            org.olf.kb.Identifier identifier = org.olf.kb.Identifier.findByNsAndValue(ns, ident.value) ?: new org.olf.kb.Identifier([
              ns: ns,
              value: ident.value
            ]).save(flush: true, failOnError: true)
  
            IdentifierOccurrence newIo = new IdentifierOccurrence([
              identifier: identifier,
              status: lookupOrCreateStatus('approved')
            ])

            pkg.addToIdentifiers(newIo)
            // Need to save the package in order to get the id of the just created IdentifierOccurrence
            pkg.save(flush:true, failOnError: true)

            identifiers_to_keep << newIo.id
          } else if (existingIo) {
            identifiers_to_keep << existingIo.id
            if (existingIo.status.value == 'error') {
              // This Identifier Occurrence exists as ERROR, reset to APPROVED
              existingIo.status = lookupOrCreateStatus('approved')
            }
          }
        } else {
          log.warn("Identifier with null namespace or value - skipping - package ID is ${pkg.id}");
        }
      }

      // Next we "delete" (set as error) any identifiers on the package not present in the identifiers list.
      List<IdentifierOccurrence> identsToRemove = IdentifierOccurrence.executeQuery("""
        SELECT io FROM IdentifierOccurrence AS io
        WHERE resource.id = :pkgId AND
          io.id NOT IN :keepList AND
          io.status.value = :approved
      """.toString(), [
        pkgId: pkg.id,
        keepList: identifiers_to_keep,
        approved: 'approved'
      ]);

      identsToRemove.each { ident -> 
        ident.status = lookupOrCreateStatus('error')
      }

      // Finally save the package
      pkg.save(failOnError: true)
    }
  }

  public ArrayList<String> lookupIdentifier(final String value, final String namespace) {
    return Identifier.executeQuery("""
      SELECT iden.id from Identifier as iden
        where iden.value = :value and iden.ns.value = :ns
      """.toString(),
      [value:value, ns:namespaceMapping(namespace)]
    ) as ArrayList<String>; // Doing this to keep the return type the same as it was in BaseTIRS, not sure this needs to be an ArrayList
  }

  /*
   * TODO Should probably integration test these methods
   *
   *
   * ASSUMPTION -- Assumes context from calling code
   * This is where we can call the namespaceMapping function to ensure consistency in our DB
   */
  @CompileStatic(SKIP)
  public IdentifierNamespace lookupOrCreateIdentifierNamespace(final String ns, final boolean flush = false) {
    IdentifierNamespace.findOrCreateByValue(namespaceMapping(ns)).save(failOnError:true, flush: flush)
  }

  /*
   * ASSUMPTION -- Assumes context from calling code
   * Given an identifier { value:'1234-5678', namespace:'isbn' }
   * lookup or create an identifier in the DB to represent that info.
   */
  public String lookupOrCreateIdentifier(final String value, final String namespace, final boolean flush = false) {
    String result = null;

    // Ensure we are looking up properly mapped namespace (pisbn -> isbn, etc)
    ArrayList<String> identifier_lookup = lookupIdentifier(value, namespace);

    switch(identifier_lookup.size() ) {
      case 0:
        IdentifierNamespace ns = lookupOrCreateIdentifierNamespace(namespace);
        result = new Identifier(ns:ns, value:value).save(failOnError:true, flush: flush).id;
        break;
      case 1:
        result = identifier_lookup[0];
        break;
      default:
        throw new IdentifierException(
          "Matched multiple identifiers for ${namespace}:${value}",
          IdentifierException.MULTIPLE_IDENTIFIER_MATCHES
        );
        break;
    }
    return result;
  }

  /* ASSUMPTION -- This method assumes that Identifiers are ONLY attached to IdentifierOccurrences.
   * If this ever changes, this method will need to be updated
   * ASSUMPTION -- Assumes context from calling code
   * 
   * fixEquivalent<namespace>s will work by assuming a "prime" namespace,
   * and that ALL ids passed in should be merged to that one "prime" if it exists
   *
   * Throw exception if any of the values are not equivalent with strictValueEquivalence
   * Returns the id of the single "true" identifier for the calling context to re-lookup.
   */
  public String fixEquivalentIds(
    final Collection<String> equivalentIdentifierIds,
    final String primeNamespace,
    final String primeValue = null, // This is only _necessary_ when strictValueEquivalence is false
    final boolean strictValueEquivalence = true
  ) {
    // Fetch this at the top so we aren't lookup-or-creating in a loop below.
    RefdataValue approvedStatus = lookupOrCreateStatus('approved');
    // Without primeNamespace, throw
    if (primeNamespace == null) {
      throw new IdentifierException(
        "fixEquivalentIds was called without a primeNamespace",
        IdentifierException.FIX_IDENTIFIER_ERROR
      )
    }

    // If there are no equivalentIds, throw
    if (equivalentIdentifierIds.size() == 0) {
      throw new IdentifierException(
        "fixEquivalentIds was called without any equivalent ids",
        IdentifierException.FIX_IDENTIFIER_ERROR
      )
    }

    // We have a list of Identifier ids. Fetch them all
    List<Identifier> equivalentIds = Identifier.executeQuery("""
      SELECT id FROM Identifier AS id
      WHERE id.id IN :equivalentIdentifierIds
    """.toString(), [equivalentIdentifierIds: equivalentIdentifierIds])

    // Logging here so we can get information about which ids are being equated as well as their ids in the DB
    log.debug("fixEquivalentIds::(${equivalentIds.collect { "${it} (${it.id})" }}, ${primeNamespace}, ${primeValue}, ${strictValueEquivalence})")

    if (strictValueEquivalence) {
      // If we're protecting
      Map<String, List<Identifier>> valuesMap = equivalentIds.groupBy{ it.value }
      if (valuesMap.keySet().size() > 1) {
        throw new IdentifierException(
          "fixEquivalentIds was passed a set of ids with differing values and is operating in strictValueEquivalence mode.",
          IdentifierException.FIX_IDENTIFIER_ERROR
        )
      }
    } else if (primeValue == null) {
      throw new IdentifierException(
        "fixEquivalentIds was called with strictValueEquivalence=false, but no primeValue was provided",
        IdentifierException.FIX_IDENTIFIER_ERROR
      )
    }

    /*
     * At this point we should know exactly what the intended "prime" identifier should be...
     * primeNamespace:primeValue OR primeNamespace:equivalentIds[0].value
     *
     * We allow primeValue to take precedence so it can act as an override
     * BE CAREFUL here though, this will blindly merge ALL passed identifiers into this new prime identifier
     */
    final String finalPrimeValue = primeValue ?: equivalentIds[0].value;
    Identifier primeIdentifier = Identifier.get(lookupOrCreateIdentifier(finalPrimeValue, primeNamespace));
    // At this stage we _definitely_ have a primeIdentifier. It may be in the list of equivalentIds or not

    equivalentIds.each { eid ->
      // Don't do anything to the prime identifier
      if (eid.id != primeIdentifier.id) {
        // Change all IdentifierOccurrences over to the new prime identifier
        List<IdentifierOccurrence> occurrences = IdentifierOccurrence.executeQuery("""
          SELECT io FROM IdentifierOccurrence AS io
          WHERE io.identifier.id = :iid
        """.toString(), [iid: eid.id]);
        ListIterator<IdentifierOccurrence> occurrenceIterator = occurrences.listIterator();
        while (occurrenceIterator.hasNext()) {
          IdentifierOccurrence oc = occurrenceIterator.next();
          // Check whether there is already a prime identifier occurrence.
          // If there is, we delete the one in hand (need to use an iterator here)
          List<IdentifierOccurrence> prime_occurrence_candidates = IdentifierOccurrence.executeQuery("""
            SELECT io FROM IdentifierOccurrence AS io
            WHERE io.resource.id = :rid AND
                  io.identifier.id = :iid
          """.toString(), [iid: primeIdentifier.id, rid: oc.resource.id]);

          switch (prime_occurrence_candidates.size()) {
            case 0:
              // There is no preexisting prime occurrence, swap over
              oc.identifier = primeIdentifier;
              oc.save(failOnError: true)
              break;
            case 1:
              // There's already an IdentifierOccurrence for this resource/primeIdentifier combo
              // Handle status and delete the one in hand
              IdentifierOccurrence primeOccurrence = prime_occurrence_candidates[0];

              // Only change status if primeOccurrence is error and we have an approved one.
              // Else leave as is. I don't think this will be hit v often.
              if (
                primeOccurrence.status.value != 'approved' &&
                oc.status.value == 'approved'
              ) {
                primeOccurrence.status = approvedStatus;
              }
              // Now remove the occurrence at hand
              oc.delete();
              break;
            default:
              // This shouldn't happen, give up
              throw new IdentifierException(
                "fixEquivalentIds found multiple identifier occurrences for the same identifier/resource combination: ${prime_occurrence_candidates.collect { it.id }}",
                IdentifierException.FIX_IDENTIFIER_ERROR
              )
              break;
          }
          
        }

        // Then delete the old identifier
        eid.delete();
      }
    }

    return primeIdentifier.id;
  }
}

package org.olf

import java.time.Instant

import javax.persistence.Query

import org.hibernate.Session
import org.hibernate.metamodel.spi.MetamodelImplementor
import org.hibernate.persister.entity.EntityPersister
import org.hibernate.persister.walking.spi.AttributeDefinition
import org.olf.erm.SubscriptionAgreement
import org.olf.general.DocumentAttachment
import org.olf.general.jobs.SupplementaryDocumentsCleaningJob
import org.springframework.transaction.TransactionStatus

import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.k_int.okapi.OkapiTenantResolver

import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import groovy.util.logging.Slf4j

@Slf4j
public class DocumentAttachmentService {

  private static final Version SUPP_DOCS_DUPLICATES_VERSION = Version.forIntegers(3) // Version trigger.

  @Subscriber('okapi:tenant_enabled')
  public void onTenantEnabled (final String tenantId, final boolean existing_tenant, final boolean upgrading, final String toVersion, final String fromVersion) {
    if (upgrading && fromVersion) {
      try {
        if (Version.valueOf(fromVersion).compareTo(SUPP_DOCS_DUPLICATES_VERSION) <= 0) {
          // We are upgrading from a version prior to when the supplementary document duplication was fixed,
          // lets schedule a job to retrospectively separate those duplicates out
          log.debug "Clean supplementary document duplicates based on tenant upgrade prior to fix being present"
          triggerCleanSuppDocsForTenant(tenantId)
        }
      } catch(ParseException pex) {
        // From version couldn't be parsed as semver we should ignore.
        log.debug "${fromVersion} could not be parsed as semver, not running supplementary document clean."
      }
    }
  }

  @Subscriber('okapi:tenant_clean_supplementary_docs')
  public void onTenantCleanSupplementaryDocs(final String tenantId, final String value, final String existing_tenant, final String upgrading, final String toVersion, final String fromVersion) {
    // We want to explicitly schedule a job to retrospectively separate duplicate supplementary documents out
    log.debug "Clean supplementary document duplicates based on explicit request during tenant activation"
    triggerCleanSuppDocsForTenant(tenantId)
  }

  private void triggerCleanSuppDocsForTenant(final String tenantId) {
    final String tenant_schema_id = OkapiTenantResolver.getTenantSchemaName(tenantId)
    Tenants.withId(tenant_schema_id) {
      SupplementaryDocumentsCleaningJob.withTransaction {

        SupplementaryDocumentsCleaningJob job = SupplementaryDocumentsCleaningJob.findByStatusInList([
          SupplementaryDocumentsCleaningJob.lookupStatus('Queued'),
          SupplementaryDocumentsCleaningJob.lookupStatus('In progress')
        ])

        if (!job) {
          job = new SupplementaryDocumentsCleaningJob(name: "Supplementary Document Cleanup ${Instant.now()}")
          job.setStatusFromString('Queued')
          job.save(failOnError: true)
        } else {
          log.debug('Supplementary document cleaning job already running or scheduled. Ignore.')
        }
      }
    }
  }

  @Transactional
  private void splitDocuments(
      final String docAttId, final List<String> saIds, final String associationParam,
      final String tName, final String saCol, final String daCol) {
      
    if (saIds.size() < 2) {
      log.error('Attempted to saparate doc attached to single agreement')
      return
    }
      
    // Grab the first attachment.
    log.debug "Leaving link from DocAtt ${docAttId} to Agreement with ${saIds[0]} in tact"

    // Each SA needs a clone of the doc adding and the original removing.
    // Perform update to each extra in brand new transaction.
    for (int i=1; i<saIds.size(); i++) {
      
      final saId = saIds[i]
      final SubscriptionAgreement duplicatedAgreement = SubscriptionAgreement.read(saId)
      final Set<DocumentAttachment> toRemove = duplicatedAgreement.getAt(associationParam).findAll {
        it.id == docAttId
      }
      
      // Each dupe should be cloned. We should do this in isolated transactions.
      final String methodName = "addTo${GrailsNameUtils.getClassName(associationParam)}"
      for (int d=0; d<toRemove.size(); d++) {
        DocumentAttachment.withNewTransaction { TransactionStatus ts ->
          final SubscriptionAgreement dupeAgg = SubscriptionAgreement.get(saId)
          final DocumentAttachment suppDoc = DocumentAttachment.read(docAttId)
          final DocumentAttachment suppDocClone = suppDoc.clone()
          suppDocClone.save(failOnError: true)            // Preliminary save of object.
          
          dupeAgg."${methodName}" ( suppDocClone )        // Associate with SA
          dupeAgg.save( failOnError: true, flush: true )  // Save 
        }
      }
      
      // Directly alter the link table with SQL to avoid any of the cascade behaviour.
      // I'm not entirely happy with this, but seeing as the delete operation is standard
      // this represents minimal tie-in.
      // Grab the current session and then grab the datastore from that.
      // Should be correct then for the current tenant context.      
      DocumentAttachment.withSession { Session sess ->
        
        log.debug ('Remove associations for document attachment {}', docAttId)
        Query q = sess.createNativeQuery("DELETE FROM ${tName} WHERE ${daCol} = :daID AND ${saCol} = :saID")
          .setParameter( 'daID', docAttId )
          .setParameter( 'saID', saId )
          
        q.executeUpdate()
      }
      log.info "Replaced shared link for ${associationParam} with ID ${docAttId} on Agreement ${saId}"
    }
  }

  @Transactional
  private triggerCleanDocsByClassAndProp(String domainClass, String prop) {
    String tName, saCol, daCol
      
    // SO: Gorm creates in-flight metadata based on our classes to create hibernate domain objects.
    // We should be able to get the metadata from the session. We use hibernate directly here...
    // I'm not overly pleased with this but it beats hard-coding table and column names for
    // references properties.
    DocumentAttachment.withSession { Session sess ->
      MetamodelImplementor hibMM = sess.getSessionFactory().metamodel
      EntityPersister ep = hibMM.locateEntityPersister(SubscriptionAgreement)
      AttributeDefinition ad = ep.getAttributes().find { AttributeDefinition theDef ->
        theDef.name == prop
      }
      
      tName = ad?.getAt('joinable')?.getAt('qualifiedTableName')
      
      String[] cols = ad?.getAt('joinable')?.getAt('elementColumnNames')
      (cols?.length ?: 0) > 0 && (daCol = cols[0])
      
      cols = ad?.getAt('joinable')?.getAt('keyColumnNames')
      (cols?.length ?: 0) > 0 && (acCol = cols[0])
    }
    
    if (tName && acCol && daCol) {
  
      log.debug "Using relational join table ${tName} with columns ${acCol} and ${daCol}"
  
      final Map<String, List<String>> dupeMapping = [:]
        DocumentAttachment.executeQuery(
        """
          SELECT da.id, ac.id FROM ${domainClass} AS ac INNER JOIN sa.${prop} AS da
            WHERE da.id IN (
              SELECT da.id FROM ${domainClass} AS ac INNER JOIN ac.${prop} AS da GROUP BY da.id HAVING COUNT(*) > 1
            )
            ORDER BY da.id, ac.id
        """.toString()
        
      ).each { final Object[] resultsArr ->
        final String[] tuple = resultsArr as String[]
        if (!dupeMapping.containsKey(tuple[0])) {
          // add List
          dupeMapping[tuple[0]] = []
        }
        dupeMapping[tuple[0]] << tuple[1]
      }
  
      log.debug "dupeMapping = ${dupeMapping}"
      
      dupeMapping.each { final String docId, final List<String> saIds ->
        splitDocuments(docId, saIds, prop, tName, acCol, daCol)
      }
    } else {
      log.debug "Could not get join table for property ${prop} on domain class ${domainClass}"
    }
  }

  // Does this even actually get called?
  private void triggerCleanSuppDocs() {
    // On Agreements
    ['supplementaryDocs', 'docs', 'externalLicenseDocs'].each { final String prop ->
      triggerCleanDocsByClassAndProp('SubscriptionAgreement', prop)
    }

    // On Entitlements
    ['docs'].each { final String prop ->
      triggerCleanDocsByClassAndProp('Entitlement', prop)
    }

  }
}

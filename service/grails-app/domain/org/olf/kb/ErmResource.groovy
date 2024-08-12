package org.olf.kb

import org.olf.CoverageService
import org.olf.erm.Entitlement
import org.olf.general.StringUtils
import org.olf.general.TemplatedUrl

import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.tags.Tag

import grails.gorm.MultiTenant

/**
 * an ErmResource - Superclass
 * Represents a selectable resource - a package, a title in a package, a title on a platform, etc
 */
public class ErmResource extends ErmTitleList implements MultiTenant<ErmResource> {
 
  String name
  String normalizedName
  String description
  
  RefdataValue type
  RefdataValue publicationType
  RefdataValue subType

  Date dateCreated
  Date lastUpdated

  boolean suppressFromDiscovery = false
  
  Set<AlternateResourceName> alternateResourceNames
  
  Set<TemplatedUrl> templatedUrls = []

  // Do not store this value, only used to check whether or not beforeValidate should calculate coverage
  // We wish to avoid this during ingest process, for example, where we do this manually based on
  // external information
  boolean doNotCalculateCoverage
  
  static hasMany = [
    coverage: CoverageStatement,
    entitlements: Entitlement,
    tags: Tag,
    templatedUrls: TemplatedUrl,
    alternateResourceNames: AlternateResourceName,
    identifiers: IdentifierOccurrence,
  ]

  static mappedBy = [
    coverage: 'resource',
    entitlements: 'resource',
    templatedUrls: 'resource',
    alternateResourceNames:  'owner',
    identifiers: 'resource',
  ]

  static mapping = {
              tablePerHierarchy false
                   name column: 'res_name'
         normalizedName column: 'res_normalized_name'
            description column: 'res_description', type:'text'
                   type column: 'res_type_fk'
        publicationType column: 'res_publication_type_fk'
            dateCreated column: 'res_date_created'
            lastUpdated column: 'res_last_updated'
                subType column: 'res_sub_type_fk'
  suppressFromDiscovery column: 'res_suppress_discovery'
              coverage cascade: 'all-delete-orphan'
         templatedUrls cascade: 'all-delete-orphan'
                  tags cascade: 'save-update'
alternateResourceNames cascade: 'all-delete-orphan'
           identifiers cascade: 'all-delete-orphan'
  }

  static constraints = {
                   name (nullable:true, blank:false)
         normalizedName (nullable:true, blank:false, bindable: false)
            description (nullable:true, blank:false)
                   type (nullable:true, blank:false)
        publicationType (nullable:true, blank:false)
                subType (nullable:true, blank:false)
            dateCreated (nullable:true, blank:false)
            lastUpdated (nullable:true, blank:false)
  suppressFromDiscovery (nullable:false, blank:false)
               coverage (validator: CoverageStatement.STATEMENT_COLLECTION_VALIDATOR, sort:'startDate')
          templatedUrls (bindable: false)
  }

  private validating = false  
  def beforeValidate() {
    this.name = StringUtils.truncate(name)

    if (!validating && !doNotCalculateCoverage) {
      validating = true
      // Attempt to avoid session locking
      ErmResource.withSession {
        CoverageService.changeListener(this)
      }

      normalizedName = StringUtils.normaliseWhitespaceAndCase(name)
      validating = false
    }

    this.doNotCalculateCoverage = false // Unset afterwards in case of a string of saves
  }
  
  String toString() {
    name
  }

  static transients = ['approvedIdentifierOccurrences', 'doNotCalculateCoverage']

  public Set<IdentifierOccurrence> getApprovedIdentifierOccurrences() {
    identifiers
      .findAll { it.status.value == 'approved' }
      .sort { a,b ->
        a.identifier.ns.value <=> b.identifier.ns.value ?:
        a.identifier.value <=> b.identifier.value
      }
  }
}

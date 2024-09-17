package org.olf.erm

import java.time.LocalDate

import javax.persistence.Transient

import org.hibernate.Hibernate

import org.olf.general.DocumentAttachment

import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance

import com.k_int.okapi.remote_resources.OkapiLookup
import com.k_int.web.toolkit.domain.traits.Clonable
import com.k_int.web.toolkit.tags.Tag
import grails.databinding.BindInitializer
import grails.gorm.MultiTenant
import groovy.util.logging.Slf4j


/**
 * Entitlement (A description of a right to access a specific digital resource, which can be an 
 * title on a platform (But not listed in a package), a title named in a package, a full package of resources
 *
 * OFTEN attached to an agreement, but it's possible we know we have the right to access a resource
 * without perhaps knowing which agreement controls that right.
 *
 */
@Slf4j
public class Entitlement implements MultiTenant<Entitlement>, Clonable<Entitlement> {
  public static final Class<? extends ErmResource>[] ALLOWED_RESOURCES = [Pkg, PackageContentItem, PlatformTitleInstance] as Class[]
  
  static copyByCloning = ['docs'];

  /**
   * Need to resolve the conflict manually and add the call to the clonable method here.
   */
  @Override
  public Entitlement clone () {
    Clonable.super.clone()
  }
  
  
  String id
  Date dateCreated
  Date lastUpdated

  ErmResource resource

  // The date ranges on which this line item is active. These date ranges allow the system to determine
  // what content is "Live" in an agreement. Content can be "Live" without being switched on, and 
  // vice versa. The dates indicate that we believe the agreement is in force for the items specified.
  // For Trials, these dates will indicate the dates of the trial, for live agreements the agreement item dates
  LocalDate activeFrom 
  LocalDate activeTo

  Date contentUpdated

  // Type - must be set to external for externally defined packages, null or local for things defined in the local DB, or detached if added without resource, just description
  String type

  String note

  // These three properties allow us to create an entitlement which is externally defined. An externally defined
  // entitlement does not link to a resource in the tenant database, but instead will use API calls to define its contents
  String authority

  boolean suppressFromDiscovery = false 
  
  String description
  
  @OkapiLookup(
    value = '${obj.authority?.toLowerCase() == "ekb-package" ? "/eholdings/packages" : "/eholdings/resources" }/${obj.reference}${obj.authority?.toLowerCase() == "ekb-package" ? "" : "?include=package" }',
    converter = {
      // delegate, owner and thisObject should be the instance of Entitlement
      final Entitlement outerEntitlement = delegate
      
      log.debug "Converter called with delegate: ${outerEntitlement} and it: ${it}"
      
      final String theType = it.data?.attributes?.publicationType ?:
        it.data?.type?.replaceAll(/^\s*([\S])(.*?)s?\s*$/, {match, String firstChar, String nonePlural -> "${firstChar.toUpperCase()}${nonePlural}"})
      
      def map = [
        label: it.data?.attributes?.name,
        type: (theType),
        provider: it.data?.attributes?.providerName
      ]

      if (it.data?.type == "packages") {
        // We're dealing with a package

        def titleCount = it.data?.attributes?.titleCount
        // Groovy truth evaluates 0 to false
        if (titleCount != null) {
          map.titleCount = titleCount
        }

        def selectedCount = it.data?.attributes?.selectedCount
        // Groovy truth evaluates 0 to false
        if (selectedCount != null) {
          map.selectedCount = selectedCount
        }

        def contentType = it.data?.attributes?.contentType
        if (contentType) {
          map.contentType = contentType
        }
      } else {
        // We're dealing with a title
        def publicationType = it.data?.attributes?.publicationType
        if (publicationType) {
          map.publicationType = publicationType
        }

        def edition = it.data?.attributes?.edition
        if (edition) {
          map.edition = edition
        }

        def url = it.data?.attributes?.url
        if (url) {
          map.url = url
        }

        def identifiers = it.data?.attributes?.identifiers
        if (identifiers) {
          def combinedIdentifiers = [];
          
          identifiers.each {
            def typeString = it.type.toLowerCase();
            def subtypeString = it.subtype.toLowerCase();
            if (typeString.matches("isbn|issn")) {
              if (subtypeString == 'online') {
                typeString = 'e' + typeString
              } else if (subtypeString == 'print') {
                typeString = 'p' + typeString
              }
            }
            def identifier = [identifier: [value: it.id, ns: [value: typeString]]]
            combinedIdentifiers << identifier
          }
          map.identifiers = combinedIdentifiers
        }

        def contributors = it.data?.attributes?.contributors
        if (contributors) {
          def authors = []
          def editors = []
          contributors.each {
            if (it.type == "author") {
              authors << it.contributor
            } else if (it.type == "editor") {
              editors << it.contributor
            }
          }
          if (authors.size() > 0) {
            map.authors = authors
          }
           if (editors.size() > 0) {
            map.editors = editors
          }
        }
        
        Map packageData = [:]

        packageData.authority = "EKB-PACKAGE"
        def packageId = it.data?.attributes?.packageId
        if (packageId) {
          packageData.reference = packageId
        }
        
        def includedPackage = it?.included.find { it.id == packageId && it.type == "packages"  }
        if (includedPackage) {
          def name = includedPackage.attributes?.name
          if (name) {
            packageData.name = name
          }

          def titleCount = includedPackage.attributes?.titleCount
          // Groovy truth evaluates 0 to false
          if (titleCount != null) {
            packageData.titleCount = titleCount
          }

          def selectedCount = includedPackage.attributes?.selectedCount
          // Groovy truth evaluates 0 to false
          if (selectedCount != null) {
            packageData.selectedCount = selectedCount
          }

          def contentType = includedPackage.attributes?.contentType
          if (contentType) {
            packageData.contentType = contentType
          }

          def providerName = includedPackage.attributes?.providerName
          if (providerName) {
            packageData.providerName = providerName
          }

          def isSelected = includedPackage.attributes?.isSelected
          if (isSelected) {
            packageData.isSelected = isSelected
          }
        }
        if (packageData) {
          map.packageData = packageData
        }
      }

      // These need to be added to the map whether the type is resource OR package
      def providerName = it.data?.attributes?.providerName
      if (providerName) {
        map.providerName = providerName
      }

      def isSelected = it.data?.attributes?.isSelected
      if (isSelected) {
        map.isSelected = isSelected
      }

      def relationshipsAccessTypeDataId = it.data?.relationships?.accessType?.data?.id;
      def accessStatusType;
      if (relationshipsAccessTypeDataId) {

        def includesMatchingId = it?.included.find { it.id == relationshipsAccessTypeDataId && it.type == "accessTypes"  }
        accessStatusType = includesMatchingId?.attributes?.name

        if (accessStatusType) {
          map.accessStatusType = accessStatusType
        }
      }
      
      // Merge external coverages.
      final boolean isPackage = theType?.toLowerCase() == 'package'
      
      log.debug "${isPackage ? 'Is' : 'Is not'} Package"
      outerEntitlement.metaClass.external_customCoverage = false
      
      def custCoverage = it.data?.attributes?.getAt("customCoverage${isPackage ? '' : 's'}")
      log.debug "Custom Coverage: ${custCoverage}"

      // Set coverage as empty array initially
      def coverageToApply = []
      if (custCoverage) {
        log.debug "Found custom coverage."
        // Simply ensure a collection.
        if (!(custCoverage instanceof Collection)) {
          log.debug "Found single custom coverage entry turn into a collection."
          custCoverage = [custCoverage]
          log.debug "...${custCoverage}"
        }

        custCoverage.each { Map <String, String> coverageEntry ->
          if (coverageEntry.beginCoverage) {
            coverageToApply << new HoldingsCoverage (startDate: LocalDate.parse(coverageEntry.beginCoverage), endDate: coverageEntry.endCoverage ? LocalDate.parse(coverageEntry.endCoverage): null)
            outerEntitlement.external_customCoverage = true
          }
        }

        // Apply all coverages to metaClass at the end
        outerEntitlement.metaClass.coverage = coverageToApply
        
      } else if (!isPackage) {
        log.debug "Adding managed title coverages."
        it.data?.attributes?.managedCoverages?.each { Map <String, String> coverageEntry ->
          if (coverageEntry.beginCoverage) {
            coverageToApply << new HoldingsCoverage (startDate: LocalDate.parse(coverageEntry.beginCoverage), endDate: coverageEntry.endCoverage ? LocalDate.parse(coverageEntry.endCoverage): null)
          }
        }

        // Apply all coverages to metaClass at the end
        outerEntitlement.metaClass.coverage = coverageToApply
      }
      
      map
    }
  )
  String reference
  Set<Tag> tags = []

  static belongsTo = [
    owner:SubscriptionAgreement
  ]

  static hasMany = [
    coverage: HoldingsCoverage,
     poLines: OrderLine,
        tags: Tag,
        docs: DocumentAttachment
  ]

  Set<HoldingsCoverage> coverage = []
  
  static mappedBy = [
    coverage: 'entitlement',
    poLines: 'owner'
  ]
  
  // We should use a beforeValidate handler to set related values.
  def beforeValidate() {
    this.type = this.type?.toLowerCase()
    this.authority = this.authority?.toUpperCase()
    
    /* 
     * Type 'internal' can be explicitly sent, or implicitly defined as no type being sent.
     * Either way we want to remove any coverage before attempting to validate.
     */
    if (this.type != 'internal' && this.type != null) {
      // Clear the coverage.
      this.coverage?.clear()
    }
  }
  

  // Allow users to individually switch on or off this content item. If null, should default to the agreement
  // enabled setting. The activeFrom and activeTo dates determine if a content item is "live" or not. This flag
  // determines if we wish live content to be visible to patrons or not. Content can be "Live" but not enabled,
  // although that would be unusual.
  @BindInitializer({
    Boolean.TRUE // Default this value to true when binding.
  })
  Boolean enabled

  static mapping = {
                   id column: 'ent_id', generator: 'uuid2', length:36
              version column: 'ent_version'
                owner column: 'ent_owner_fk'
             resource column: 'ent_resource_fk'
                 type column: 'ent_type'
                 note column: 'ent_note', type: 'text'
              enabled column: 'ent_enabled'
suppressFromDiscovery column: 'ent_suppress_discovery'
       contentUpdated column: 'ent_content_updated'
           activeFrom column: 'ent_active_from'
             activeTo column: 'ent_active_to'
            authority column: 'ent_authority'
            reference column: 'ent_reference'
          description column: 'ent_description'
          dateCreated column: 'ent_date_created'
          lastUpdated column: 'ent_last_updated'
             poLines cascade: 'all-delete-orphan'
            coverage cascade: 'all-delete-orphan'
                tags cascade: 'save-update'
                docs cascade: 'all-delete-orphan', joinTable: [name: 'entitlement_document_attachment', key: 'entitlement_docs_id', column: 'document_attachment_id']
  }

  static constraints = {
          owner(nullable:true,  blank:false)
    dateCreated(nullable:true, blank: true)
    lastUpdated(nullable:true, blank: true)


          // Now that resources can be internally or externally defined, the internal resource link CAN be null,
          // but if it is, there should be authorty, and reference properties.
          resource (nullable:true, validator: { val, inst ->
            switch (inst.type?.toLowerCase()) {
              case 'external':
                // External resource should have null internal resource reference.
                return val != null ? ['externalEntitlement.resource.not.null'] : true
                break;
              case 'detached':
                // Detached resource should have null internal resource reference.
                return val != null ? ['detachedEntitlement.resource.not.null'] : true
                break;
              default:
                if ( val ) {
                  Class c = Hibernate.getClass(val)
                  if (!Entitlement.ALLOWED_RESOURCES.contains(c)) {
                    ['allowedTypes', "${c.name}", "entitlement", "resource"]
                  }
                } else {
                  // Resource is null but type is internal.
                  return ['entitlement.resource.is.null']
                }
                break;
            }
          })
          
          coverage (validator: HoldingsCoverage.STATEMENT_COLLECTION_VALIDATOR, sort:'startDate')

                     type(nullable:true, blank:false)
                     note(nullable:true, blank:false)
                  enabled(nullable:true, blank:false)
    suppressFromDiscovery(nullable:false, blank:false)
              description(nullable:true, blank:false, validator: { val, inst ->
                        if (inst.type?.toLowerCase() == 'detached') {
                          return val ? true: ['detachedEntitlement.description.is.null']
                        }
                     })
           contentUpdated(nullable:true, blank:false)
               activeFrom(nullable:true, blank:false)
                 activeTo(nullable:true, blank:false)
          
         authority(nullable:true, blank:false, validator: { val, inst ->
            switch (inst.type?.toLowerCase()) {
              case 'external':
                // External resource should have authority.
                return val == null ? ['externalEntitlement.authority.is.null'] : true
                break;
              case 'detached':
                // Authority is null but type is detached.
                return val != null ? ['detachedEntitlement.authority.not.null'] : true
                break;
              default:
                // Authority is null but type is internal.
                return val != null ? ['externalEntitlement.authority.not.null'] : true
                break;
            }
          })
         
         reference (nullable:true, blank:false, validator: { val, inst ->
            switch (inst.type?.toLowerCase()) {
              case 'external':
                // External resource should have reference.
                return val == null ? ['externalEntitlement.reference.is.null'] : true
                break;
              case 'detached':
                // Reference is null but type is detached.
                return val != null ? ['detachedEntitlement.reference.not.null'] : true
                break;
              default:
                // Reference is null but type is internal.
                return val != null ?  ['externalEntitlement.reference.not.null'] : true
                break;
            }
          })
  }
  
  @Transient
  public String getExplanation() {
    
    String result = null
    
    if (resource) {
      // Get the class using the hibernate helper so we can
      // be sure we have the target class and not a proxy wrapper.
      Class c = Hibernate.getClass(resource)
      switch (c) {
        case Pkg:
          result = 'Agreement includes a package containing this item'
          break
        case PlatformTitleInstance:
          result = 'Agremment includes this title directly'
          break
        case PackageContentItem:
          result = 'Agreement includes this item from a package specifically'
          break
      }
    }
    result
  }

  @Transient
  public boolean getHaveAccess() {
    return haveAccessAsAt(LocalDate.now());
  }

  /**
   * If activeFrom <= date <= activeTo 
   */
  public boolean haveAccessAsAt(LocalDate point_in_time) {
    boolean result = false;
    if ( ( activeFrom != null ) && ( activeTo != null ) ) {
      result = ( ( activeFrom <= point_in_time ) && ( point_in_time <= activeTo ) )
    }
    else if ( activeFrom != null ) {
      result = ( activeFrom <= point_in_time )
    }
    else if ( activeTo != null ) {
      result = ( point_in_time <= activeTo )
    }
    else {
      // activeFrom and activeTo are both null - we assume this is perpetual then, so true
      return true;
    }
    return result;
  }
 
}

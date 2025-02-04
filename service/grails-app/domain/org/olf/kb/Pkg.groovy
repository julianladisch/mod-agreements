package org.olf.kb

import javax.persistence.Transient

import org.olf.general.Org

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.multitenancy.Tenants
import grails.gorm.MultiTenant

/**
 * mod-erm representation of a package
 */
public class Pkg extends ErmResource implements MultiTenant<Pkg> {
  String source
  Date sourceDataCreated
  Date sourceDataUpdated
  Integer sourceTitleCount

  // A boolean to track whether we are choosing to sync the contents from the source or not
  Boolean syncContentsFromSource = false

  String reference  // Reference contains the KBs authoritative ID for this package - Reference should be unique within KB

  Platform nominalPlatform
  Org vendor

  @Defaults(['Current', 'Retired', 'Expected', 'Deleted'])
  RefdataValue lifecycleStatus
  @Defaults(['Global'])
  RefdataValue availabilityScope

  Set<PackageDescriptionUrl> packageDescriptionUrls
  Set<ContentType> contentTypes
  Set<AvailabilityConstraint> availabilityConstraints
  
  // Declaring this here will provide defaults for the type defined in ErmResource but not create
  // a subclass specific column
  @Defaults(['Aggregated Full Text', 'Abstracts and Index', 'Package'])
  RefdataValue type

  static hasMany = [
              contentItems: PackageContentItem,
    packageDescriptionUrls: PackageDescriptionUrl,
              contentTypes: ContentType,
    availabilityConstraints: AvailabilityConstraint
    // tags: KIWTTag
  ]

  static mappedBy = [ 
              contentItems: 'pkg',
    packageDescriptionUrls: 'owner',
              contentTypes: 'owner'
  ]

  static mapping = {
                        table 'package'
                       source column:'pkg_source'
       syncContentsFromSource column:'pkg_sync_contents_from_source'
                    reference column:'pkg_reference'
              nominalPlatform column:'pkg_nominal_platform_fk'
                       vendor column:'pkg_vendor_fk'
            sourceDataCreated column:'pkg_source_data_created'
            sourceDataUpdated column:'pkg_source_data_updated'
             sourceTitleCount column:'pkg_source_title_count'
              lifecycleStatus column:'pkg_lifecycle_status_fk'
            availabilityScope column:'pkg_availability_scope_fk'
       packageDescriptionUrls cascade: 'all-delete-orphan'
                 contentTypes cascade: 'all-delete-orphan'
      availabilityConstraints cascade: 'all-delete-orphan'
  }

  static constraints = {
                         name(nullable:false, blank:false)
                       source(nullable:false, blank:false)
       syncContentsFromSource(nullable:true, blank:false) // Allow this to be null for now... will treat `null` as `false`)
                    reference(nullable:false, blank:false)
              nominalPlatform(nullable:true, blank:false)
             sourceTitleCount(nullable:true, blank:false)
                       vendor(nullable:true, blank:false)
            sourceDataCreated(nullable:true, blank:false)
            sourceDataUpdated(nullable:true, blank:false)
              lifecycleStatus(nullable:true, blank:false)
            availabilityScope(nullable:true, blank:false)
  }

  /*
   * TODO
   * This is an example of where we don't want to load all resources
   * into memory and count them, so we're forced to query within the
   * domain class for now.
   * This behaviour should be looked at at some point.
   */
  @Transient
  public long getResourceCount() {
    Tenants.withCurrent {
      long num_items = (PackageContentItem.executeQuery("""
        SELECT count(*) FROM PackageContentItem pci
        WHERE pci.pkg.id = :id
        AND pci.removedTimestamp = NULL
        """,
        [id: id]
      ) ?: [])[0];
      
      return num_items;
    }
  }
}

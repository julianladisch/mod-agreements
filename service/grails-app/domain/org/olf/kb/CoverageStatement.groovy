package org.olf.kb

import java.time.LocalDate

import org.hibernate.Hibernate

import grails.gorm.MultiTenant
import grails.validation.Validateable


/**
 * A coverage statement - can apply to a PackageContentItem OR a TitlePlatform OR a title
 * but that should be an exclusive link
 */
public class CoverageStatement extends AbstractCoverageStatement implements Validateable, MultiTenant<CoverageStatement> {
  public static final Class<? extends ErmResource>[] ALLOWED_RESOURCES = [PackageContentItem, PlatformTitleInstance, TitleInstance] as Class[]

  String id
  
  // pci, pti or ti - See validator below.
  ErmResource resource
  
  LocalDate startDate
  LocalDate endDate
  
  String startVolume
  String startIssue
  String endVolume
  String endIssue
  
  static constraints = {
    startDate(nullable:false, validator: AbstractCoverageStatement.STATEMENT_START_VALIDATOR)
    endDate(nullable:true)
    startVolume(nullable:true, blank:false)
    startIssue(nullable:true, blank:false)
    endVolume(nullable:true, blank:false)
    endIssue(nullable:true, blank:false)
    
    resource(nullable:false, validator: { val, inst ->
      if ( val ) {
        Class c = Hibernate.getClass(val)
        if (!CoverageStatement.ALLOWED_RESOURCES.contains(c)) {
          println("resource custom validation failure");
          ['allowedTypes', "resource", "CoverageStatement", "${c.name}"]
        }
      }
    })
  }

  static mapping = {
                   id column:'cs_id', generator: 'uuid2', length:36
              version column:'cs_version'
             resource column:'cs_resource_fk'
            startDate column:'cs_start_date'
              endDate column:'cs_end_date'
          startVolume column:'cs_start_volume'
           startIssue column:'cs_start_issue'
            endVolume column:'cs_end_volume'
             endIssue column:'cs_end_issue'
  }

  // TODO is this a good enough toString to be official rather than "toGoKBString"?
  String toString() {
    "v${startVolume ?: '*'}/i${startIssue ?: '*'}/${startDate ?: '*'} - v${endVolume ?: '*'}/i${endIssue ?: '*'}/${endDate ?: '*'}"
  }
}

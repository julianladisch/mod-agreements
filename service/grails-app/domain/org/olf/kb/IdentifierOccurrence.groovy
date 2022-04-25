package org.olf.kb

import com.k_int.web.toolkit.refdata.CategoryId
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.MultiTenant


/**
 * This table links an identifier with an identified item such as a title, package, work or other item.
 * This modelling decusion is taken over foregn keys directly on the identifier as it is often the case
 * in KBs that identifiers are misused. This table allows us to model identifiers as mistakenly applied to
 * a title. So - an identifier may be linked to many titles for example, with only 1 being value, and the others
 * recording the fact that the identifier is mis-labelled in some packages using that identifier. EG
 *
 * Title "Brain Of the Firm"   <-> IO status:ERROR <-> Identifier ISBN:1234-5676
 * Title "Brain Of the Firm"   <-> IO status:APPROVED <-> Identifier ISBN:1234-5677
 * Title "Some other book"     <-> IO status:APPROVED <-> Identifier ISBN:1234-5676
 *
 * So 1234-5676 is listed against 2 titles, but only one is approved (And should be used for lookup purposes)
 */
public class IdentifierOccurrence implements MultiTenant<IdentifierOccurrence> {

  String id
  Identifier identifier
  ErmResource resource
  @CategoryId(defaultInternal=true)
  RefdataValue status

  Date dateCreated
  Date lastUpdated


  static mapping = {
                   id column:'io_id', generator: 'uuid2', length:36
              version column:'io_version'
           identifier column:'io_identifier_fk'
             resource column:'io_res_fk'
               status column:'io_status_fk'
          dateCreated column:'io_date_created'
          lastUpdated column:'io_last_updated'
  }

  static constraints = {
      identifier(nullable:false, blank:false)
        resource(nullable:true, blank:false)
          status(nullable:false, blank:false)
     dateCreated(nullable:true, blank:false)
     lastUpdated(nullable:true, blank:false)
  }


}

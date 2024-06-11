package org.olf.kb

import org.olf.general.StringUtils
import grails.gorm.MultiTenant

/**
 * mod-agreements representation of a BIBFRAME Work

 * The extension of ErmTitleList is a little misrepresentative, since
 * Work hangs off of TitleInstance instead of the other way around
 * But it allows a neat extension of IdentifierOccurrence and any logic down the line
 * can use Work to find a title list, so it's not completely insane (?)
 */
public class Work extends ErmTitleList implements MultiTenant<Work> {
  String id
  String title

  // We are making a choice here that new sourceIdentifier == new Work
  // Use hasOne here to allow mapping to flow in same direction as on ErmResource
  static hasOne = [
    sourceIdentifier: IdentifierOccurrence
  ]

  static mappedBy = [
    sourceIdentifier: 'resource',
  ]

  static mapping = {
                   id column:'w_id', generator: 'uuid2', length:36
              version column:'w_version'
                title column:'w_title'
  }

  static constraints = {
               title(nullable:false, blank:false)
    // Should not be nullable, but often we have to debug issues from a time before we were guaranteed this field
    // sourceIdentifier(nullable: true)
  }

  def beforeValidate() {
    this.title = StringUtils.truncate(title)
  }
}

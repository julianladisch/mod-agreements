package org.olf.dataimport.internal
import java.time.LocalDate

import org.olf.dataimport.erm.PackageProvider
import org.olf.dataimport.erm.ContentType
import org.olf.dataimport.erm.AlternateResourceName
import org.olf.dataimport.erm.AlternateSlug
import org.olf.dataimport.erm.AvailabilityConstraint
import org.olf.dataimport.erm.PackageDescriptionUrl

import org.olf.dataimport.internal.PackageSchema.PackageHeaderSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString

@ToString(includePackage=false)
@GrailsCompileStatic
class HeaderImpl implements PackageHeaderSchema, Validateable {
  PackageProvider packageProvider
  String packageSource
  String status
  String packageName
  Boolean trustedSourceTI
  Boolean syncContentsFromSource = false
  LocalDate startDate
  LocalDate endDate
  String packageSlug
  String description
  Date sourceDataCreated
  Date sourceDataUpdated
  Integer sourceTitleCount
  String availabilityScope
  List<ContentType> contentTypes
  List<AlternateResourceName> alternateResourceNames
  List<AlternateSlug> alternateSlugs
  List<AvailabilityConstraint> availabilityConstraints
  List<PackageDescriptionUrl> packageDescriptionUrls
  String lifecycleStatus
  String _intenalId

  static constraints = {
    packageSource   nullable: false, blank: false
    packageSlug     nullable: false, blank: false
    packageName     nullable: false, blank: false
  }
}

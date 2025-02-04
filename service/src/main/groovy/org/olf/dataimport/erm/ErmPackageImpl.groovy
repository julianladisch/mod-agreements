package org.olf.dataimport.erm

import java.time.LocalDate

import org.olf.dataimport.erm.Identifier
import org.olf.dataimport.erm.AlternateResourceName
import org.olf.dataimport.erm.AlternateSlug
import org.olf.dataimport.erm.ContentType
import org.olf.dataimport.erm.AvailabilityConstraint
import org.olf.dataimport.erm.PackageDescriptionUrl

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.PackageHeaderSchema

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable

@GrailsCompileStatic
class ErmPackageImpl implements PackageHeaderSchema, PackageSchema, Validateable {

  String source
  String reference
  Boolean syncContentsFromSource
  String name
  PackageProvider packageProvider
  Boolean trustedSourceTI
  Date sourceDataCreated
  Date sourceDataUpdated
  Integer sourceTitleCount
  String availabilityScope
  String lifecycleStatus
  String description
  List<ContentType> contentTypes
  List<AlternateResourceName> alternateResourceNames
  List<AlternateSlug> alternateSlugs
  List<AvailabilityConstraint> availabilityConstraints
  List<PackageDescriptionUrl> packageDescriptionUrls
  List<Identifier> identifiers

  Set<ContentItem> contentItems = []

  // Defaults for internal schema so we can make them optional in the constraints.
  final LocalDate startDate = null
  final LocalDate endDate = null
  final String _intenalId = null
  final String status = null

  static hasMany = [
    contentItems: ContentItem
  ]

  static constraints = {
    startDate nullable: true
    endDate nullable: true
    _intenalId nullable: true, blank: false
    status nullable: true, blank: false
    trustedSourceTI nullable: true
    syncContentsFromSource nullable: true
    description nullable: true

    source             nullable: false, blank: false
    reference          nullable: false, blank: false
    name               nullable: false, blank: false
    packageProvider    nullable: true
    availabilityScope  nullable: true, blank: false
    sourceDataCreated  nullable: true, blank: false
    sourceDataUpdated  nullable: true, blank: false
    sourceTitleCount   nullable: true, blank: false
    lifecycleStatus    nullable: true, blank: false
  }

  @Override
  public PackageHeaderSchema getHeader() {
    // This object also implements the header.
    this
  }

  @Override
  public String getPackageSource() {
    source
  }

  @Override
  public String getPackageName() {
    name
  }

  @Override
  public String getPackageSlug() {
    reference
  }

  @Override
  public Collection<ContentItem> getPackageContents() {
    contentItems
  }

  @Override
  public Boolean getTrustedSourceTI() {
    trustedSourceTI
  }

  @Override
  public Date getSourceDataCreated() {
    sourceDataCreated
  }

  @Override
  public Date getSourceDataUpdated() {
    sourceDataUpdated
  }

  @Override
  public Integer getSourceTitleCount() {
    sourceTitleCount
  }


  @Override
  public String getAvailabilityScope() {
    availabilityScope
  }

  @Override
  public String getLifecycleStatus() {
    lifecycleStatus
  }

  @Override
  public String getDescription() {
    description
  }

  @Override
  public List<ContentType> getContentTypes() {
    contentTypes
  }

  @Override
  public List<AlternateResourceName> getAlternateResourceNames() {
    alternateResourceNames
  }

  @Override
  public List<AlternateSlug> getAlternateSlugs() {
    alternateSlugs
  }

  @Override
  public List<AvailabilityConstraint> getAvailabilityConstraints() {
    availabilityConstraints
  }

  @Override
  public List<PackageDescriptionUrl> getPackageDescriptionUrls() {
    packageDescriptionUrls
  }


  @Override
  public List<Identifier> getIdentifiers() {
    identifiers
  }

  String toString() {
    "${name} from ${packageProvider}"
  }
}

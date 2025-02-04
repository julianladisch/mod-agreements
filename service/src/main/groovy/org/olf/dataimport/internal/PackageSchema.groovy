package org.olf.dataimport.internal
import java.time.LocalDate

import org.springframework.validation.Errors

import grails.validation.Validateable
import groovy.transform.CompileStatic

@CompileStatic
interface PackageSchema extends Validateable {

  PackageHeaderSchema getHeader()
  Collection<ContentItemSchema> getPackageContents()
  Collection<IdentifierSchema> getIdentifiers()
  Errors getErrors()
  boolean validate()

  @CompileStatic
  public interface PackageHeaderSchema extends Validateable {
    /*
     "availability": {
      "type": "general"
    },
    "packageProvider": {
      "name": "Knowledge Integration Ltd"
    },
    "packageSource": "k-int",
    "packageName": "K-Int Test Package 001",
    "startDate": "2015-01-01T00:00:00Z",
    "endDate": "2020-12-31T00:00:00Z",
    "packageSlug": "kint_test_001",
    "trustedSourceTI": true,
    "sourceDataCreated": "2020-12-31T00:00:00Z",
    "sourceDataUpdated": "2020-12-31T00:00:00Z",
    "availabilityScope": "Global",
    "description": "A package",
    "alternateResourceNames": [
      { "name": "Name 1" },
      { "name": "Name 2" }
    ],
    "alternateSlugs": [
      { "slug": "Slug 1" },
      { "slug": "Slug 2" }
    ],
    "contentTypes": [
      { "contentType": "Database" }
    ],
    "availabilityConstraints": [
      { "body": "Body 1" },
      { "body": "Body 2" },
      { "body": "Body 3" }
    ],
    "packageDescriptionUrls": [
      { "url": "url 1" },
      { "url": "url 2" }
    ],
    "_intenalId": 276432871386
    ""
    */

    PackageProviderSchema getPackageProvider()
    String getPackageSource()
    String getPackageName()
    LocalDate getStartDate()
    LocalDate getEndDate()
    String getDescription()
    String getPackageSlug()
    String getStatus()
    String get_intenalId()
    Boolean getTrustedSourceTI()
    Boolean getSyncContentsFromSource()
    Date getSourceDataCreated()
    Date getSourceDataUpdated()
    Integer getSourceTitleCount()
    String getAvailabilityScope()
    String getLifecycleStatus()
    Collection<ContentTypeSchema> getContentTypes()
    Collection<AlternateResourceNameSchema> getAlternateResourceNames()
    Collection<AlternateSlugSchema> getAlternateSlugs()
    Collection<AvailabilityConstraintSchema> getAvailabilityConstraints()
    Collection<PackageDescriptionUrlSchema> getPackageDescriptionUrls()
  }

  @CompileStatic
  public interface PackageProviderSchema extends Validateable {
    String getName()
    String getReference()
  }

  public interface ContentItemSchema extends Validateable {
    /*
     {
      "title": "Clinical Cancer Drugs",
      "instanceMedium": "electronic",
      "instanceMedia": "journal",
      "instanceIdentifiers": [{
        "namespace": "eissn",
        "value": "2212-6988"
      }],
      "siblingInstanceIdentifiers": [{
        "namespace": "issn",
        "value": "2212-697X"
      }],
      "coverage": [
        {
        "startVolume": "1",
        "startIssue": "1",
        "startDate": "2014-01-01",
        "endVolume": "1",
        "endIssue": "4",
        "endDate": "2014-12-31"
        },
        {
        "startVolume": "2",
        "startIssue": "1",
        "startDate": "2015-01-01",
        "endVolume": null,
        "endIssue": null,
        "endDate": null
        }
      ],
      "embargo": null,
      "coverageDepth": "fulltext",
      "coverageNote": "New for 2014. This record is a test case for split coverage",
      "platformUrl": "http://benthamscience.com/journal/index.php?journalID=ccand",
      "platformName": "Bentham Science",
      "_platformId": 627
    }
    */

    /* 
     * In the case of a standalone title being ingested,
     * allow for PackageSchema to exist within ContentItemSchema
     * rather than the other way around.
     *
     * IMPORTANT -- Any contentItems held within _this_ package schema MUST be ignored
     */
    PackageSchema getContentItemPackage()

    String getTitle()
    String getInstanceMedium()
    String getInstanceMedia()
    String getInstancePublicationMedia()
    Collection<IdentifierSchema> getInstanceIdentifiers()
    Collection<IdentifierSchema> getSiblingInstanceIdentifiers()
    Collection<CoverageStatementSchema> getCoverage()

    String getDateMonographPublished()
    
    String getSourceIdentifier() // We treat this as a work level property, so UUID from source of the work NOT a PCI
    String getSourceIdentifierNamespace() // We treat this as a work level property, adapter should set this -- can default in JSON/KBART case

    String getFirstAuthor()
    String getFirstEditor()
    String getMonographEdition()
    String getMonographVolume()

    String getEmbargo()
    String getCoverageDepth()
    String getCoverageNote()
    String getPlatformUrl()
    String getUrl()
    String getPlatformName()
    String get_platformId()
    LocalDate getAccessStart()
    LocalDate getAccessEnd()

    /*
     * This is a *new* field utilised by pushKB to
     * allow an external service to mark a particular
     * PCI as removed from a package directly, instead
     * of relying on a lookup of all non-touched PCIs per
     * package at the end of a package ingest. This field is
     * NOT also currently used by harvest process, but
     * perhaps should be.
     *
     * TODO look into using this field for harvest process as well
     */
    Long getRemovedTimestamp()
  }

  @CompileStatic
  public interface IdentifierSchema extends Validateable {
    /*
     {
       "namespace": "issn",
       "value": "2212-697X"
     }
     */
    String getNamespace()
    String getValue()
  }

  /*
   * These three (ContentType/AlternateResourceName/AvailabilityConstraint)
   * could be just a list of strings, but should we want to store more data against
   * them in future this gives us the flexibility to do so
   */

  @CompileStatic
  public interface ContentTypeSchema extends Validateable {
    /*
     {
       "contentType": "Database",
     }
     */
    String getContentType()
  }

  @CompileStatic
  public interface AlternateResourceNameSchema extends Validateable {
    /*
     {
       "name": "An alternate Name",
     }
     */
    String getAlternateResourceName()
  }

  @CompileStatic
  public interface AlternateSlugSchema extends Validateable {
    /*
     {
       "slug": "An alternate slug",
     }
     */
    String getAlternateSlug()
  }

  @CompileStatic
  public interface AvailabilityConstraintSchema extends Validateable {
    /*
     {
       "body": "Body 1",
     }
     */
    String getBody()
  }

  @CompileStatic
  public interface PackageDescriptionUrlSchema extends Validateable {
    /*
     {
       "url": "https://a.package.description.url",
     }
     */
    String getUrl()
  }

  @CompileStatic
  public interface CoverageStatementSchema extends Validateable {
    /*
    {
      "startVolume": "1",
      "startIssue": "1",
      "startDate": "2014-01-01",
      "endVolume": "1",
      "endIssue": "4",
      "endDate": "2014-12-31"
    }
    */
    LocalDate getStartDate()
    String getStartVolume()
    String getStartIssue()

    LocalDate getEndDate()
    String getEndVolume()
    String getEndIssue()
  }
}

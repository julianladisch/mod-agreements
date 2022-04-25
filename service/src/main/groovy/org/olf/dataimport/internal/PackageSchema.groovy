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
    "_intenalId": 276432871386
    */
    
    PackageProviderSchema getPackageProvider()
    String getPackageSource()
    String getPackageName()
    LocalDate getStartDate()
    LocalDate getEndDate()
    String getPackageSlug()
    String getStatus()
    String get_intenalId()
    Boolean getTrustedSourceTI()
  }
  
  @CompileStatic
  public interface PackageProviderSchema extends Validateable {
    String getName()
    String getReference()
  }
  
  @CompileStatic
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
    
    String getTitle()
    String getInstanceMedium()
    String getInstanceMedia()
    String getInstancePublicationMedia()
    Collection<IdentifierSchema> getInstanceIdentifiers()
    Collection<IdentifierSchema> getSiblingInstanceIdentifiers()
    Collection<CoverageStatementSchema> getCoverage()

    String getDateMonographPublished()
    String getSourceIdentifier()
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

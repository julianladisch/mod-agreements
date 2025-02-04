package org.olf

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

import org.olf.general.StringUtils

import org.olf.dataimport.erm.CoverageStatement
import org.olf.dataimport.erm.ErmPackageImplWithContentItems
import org.olf.dataimport.erm.Identifier
import org.olf.dataimport.erm.PackageProvider
import org.olf.dataimport.internal.HeaderImpl
import org.olf.dataimport.internal.InternalPackageImplWithPackageContents
import org.olf.dataimport.internal.PackageContentImpl
import org.olf.dataimport.internal.PackageSchema
import org.slf4j.MDC

import com.opencsv.CSVReader

import grails.web.databinding.DataBinder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class ImportService implements DataBinder {
  UtilityService utilityService
  PackageIngestService packageIngestService
  
  Map importFromFile (final Map envelope) {
    Map result = [:];
    
    final def header = envelope.header
    final def dataSchemaName = header?.getAt('dataSchema')?.getAt('name')
    final def dataSchemaVersion = header?.getAt('dataSchema')?.getAt('version')

    if (dataSchemaName) {
      
      log.info "dataSchema specified"
      
      // we can use the dataSchema object to lookup the type.
      switch (dataSchemaName) {
        case 'mod-agreements-package':
          log.debug "ERM schema"          
          
          // Now do version check
          // You can use closures in switch statements... TIL
          switch (dataSchemaVersion) {
            case {
              it instanceof String &&
              utilityService.compatibleVersion(dataSchemaVersion as String, "1.0")
            }:
              log.error "As of mod-agreements v6.0.0, mod-agreements-package schema v1 is no longer supported"
              break;
            case {
              it instanceof String &&
              utilityService.compatibleVersion(dataSchemaVersion as String, "2.0")
            }:
              result = importPackageUsingErmSchema (envelope)
              log.info "${result.packageCount} package(s) imported successfully"
              break;
            // Error cases
            case {!(it instanceof String)}:
              log.error "As of mod-agreements v6.0.0, mod-agreements-package schema version must be a string of the form \"x.y\""
              break;
            default:
              log.error "mod-agreements-package version ${dataSchemaVersion} is invalid."
              break;
          }
          break;
          
          // Successfully
        default: 
          log.error "Unknown dataSchema ${dataSchemaName}, ignoring import."
      }
    } else {
      // No dataSchemaName. Examine the rest of the root properties
      if (header && envelope.packageContents) {
        // Looks like it might be the internal schema.
        
        log.debug "Possibly internal schema"
        result = importPackageUsingInternalSchema (envelope)
      }
    }

    return result
  }
  
  Map importPackageUsingErmSchema (final Map envelope) {
    
    log.debug "Called importPackageUsingErmSchema with data ${envelope}"
    int packageCount = 0
    List<String> packageIds = []
    // Erm schema supports multiple packages per document. We should lazily parse 1 by 1.
    envelope.records?.each { Map record ->
      // Ingest 1 package at a time.
      Map importResult = importPackage (record, ErmPackageImplWithContentItems)
                  
      if (importResult.packageImported) {
        packageCount ++
        String packageId = importResult.packageId
        packageIds << packageId
      }
    }
    
    Map result = [packageCount: packageCount, packageIds: packageIds]
    result
  }
  
  Map importPackageUsingInternalSchema (final Map envelope) {
    // The whole envelope is a single package in this format.
    return importPackage (envelope, InternalPackageImplWithPackageContents)
  }
  
  private Map importPackage (final Map record, final Class<? extends PackageSchema> schemaClass) {
    boolean packageImported = false
    String packageId = ""

    final PackageSchema pkg = schemaClass.newInstance()
    bindData(pkg, record)
    if (utilityService.checkValidBinding(pkg)) {
      Map result = packageIngestService.upsertPackage(pkg)
      String upsertPackagePackageId = result.packageId
      
      packageImported = true
      packageId = upsertPackagePackageId
    }

    Map results = [packageImported: packageImported, packageId: packageId]
    results
  }

  boolean importPackageFromKbart (CSVReader file, Map packageInfo) {
    
    boolean packageImported = false
    log.debug("Attempting to import package from KBART file")

    String packageName
    String packageSource
    String packageReference
    String packageProvider
    Boolean trustedSourceTI = packageInfo.trustedSourceTI

    if ( packageInfo.packageName == null ||
         packageInfo.packageSource == null ||
         packageInfo.packageReference == null ) {
        log.error("Import is missing key package information")
        return packageImported
      } else {
        packageName = packageInfo.packageName
        packageSource = packageInfo.packageSource
        packageReference = packageInfo.packageReference
    }
    
    MDC.put('packageSource', packageSource.toString())
    MDC.put('packageReference', packageReference.toString())

    if (packageInfo.packageProvider != null) {
      packageProvider = packageInfo.packageProvider
    }

    // peek gets line without removing from iterator
    // readNext gets line and removes it from the csvReader object
    String[] header = file.readNext()

    // Create an object containing fields we can accept and their mappings in our domain structure, as well as indices in the imported file, with -1 if not found
    // The actual field name does not appear to matter, it is only used in this file to help us find the correct field later
    // TODO perhaps refactor to avoid mapping the KBART field names to this in between value?
    Map<String, LinkedHashMap<String, ?>> acceptedFields = [
      publication_title: [field: 'title', index: -1],
      print_identifier: [field: 'siblingInstanceIdentifiers', index: -1],
      online_identifier: [field: 'instanceIdentifiers', index: -1],
      date_first_issue_online: [field: 'CoverageStatement.startDate', index: -1],
      num_first_vol_online: [field: 'CoverageStatement.startVolume', index: -1],
      num_first_issue_online: [field: 'CoverageStatement.startIssue', index: -1],
      date_last_issue_online: [field: 'CoverageStatement.endDate', index: -1],
      num_last_vol_online: [field: 'CoverageStatement.endVolume', index: -1],
      num_last_issue_online: [field: 'CoverageStatement.endIssue', index: -1],
      title_url: [field: 'url', index: -1],
      first_author: [field: 'firstAuthor', index: -1],
      title_id: [field: 'titleId', index: -1],
      embargo_info: [field: 'embargo', index: -1],
      coverage_depth: [field: 'coverageDepth', index: -1],
      notes: [field: 'coverageNote', index: -1],
      publisher_name: [field: null, index: -1],
      publication_type: [field: 'instanceMedia', index: -1],
      date_monograph_published_print: [field: 'dateMonographPublishedPrint', index: -1],
      date_monograph_published_online: [field: 'dateMonographPublished', index: -1],
      monograph_volume: [field: 'monographVolume', index: -1],
      monograph_edition: [field: 'monographEdition', index: -1],
      first_editor: [field: 'firstEditor', index: -1],
      parent_publication_title_id: [field: null, index: -1],
      preceding_publication_title_id: [field: null, index: -1],
      access_type : [field: null, index: -1],
      source_identifier : [field: 'sourceIdentifier', index: -1], // NOT standard KBART
      source_identifier_namespace : [field: 'sourceIdentifierNamespace', index: -1] // NOT standard KBART
    ]

    // Map each key to its location in the header
    for (int i=0; i<header.length; i++) {
      final String key = header[i]
      if (acceptedFields.containsKey(key)) {
        Map data = acceptedFields[key] as Map
        data['index'] = i
      }
    }

    // At this point we have a mapping of internal fields to KBART fields and their indexes in the imported file
    // Mandatory fields' existence should be checked
    List mandatoryFields = ['title', 'instanceIdentifiers', 'url', 'instanceMedia']

    def missingFields = mandatoryFields.findAll {field ->
      !shouldExist(acceptedFields, field)[0]
    }.collect { f ->
      shouldExist(acceptedFields, f)[1]
    }
    if (missingFields.size() != 0) {
      log.error("The import file is missing the mandatory fields: ${missingFields}")
      throw new RuntimeException("The import file is missing the mandatory fields: ${missingFields}")
      return (false);
    }
    
    final InternalPackageImplWithPackageContents pkg = new InternalPackageImplWithPackageContents()
    final PackageProvider pkgPrv = new PackageProvider()
    pkgPrv.name = packageProvider

    pkg.header = new HeaderImpl(
      packageName: packageName,
      packageSource: packageSource,
      packageSlug: packageReference,
      packageProvider: pkgPrv,
      trustedSourceTI: trustedSourceTI,
      syncContentsFromSource: true // This is defaulted right now, for more control the JSON import should be used
    )

    String[] record = file.readNext()
    int rownumber = 0
    
    while (record != null) {
      rownumber++
      MDC.put('rowNumber', rownumber.toString())

      Identifier siblingInstanceIdentifier = new Identifier()
      Identifier instanceIdentifier = new Identifier()

      // Instance/Sibling instance identifiers AND coverage rely on the media type, monograph vs serial 
      String instanceMedia = getFieldFromLine(record, acceptedFields, 'instanceMedia')
      boolean addCoverage = true
      if (!instanceMedia) {
        // Skip the import 
        log.error "Missing publication_type for title: ${getFieldFromLine(record, acceptedFields, 'title')}, skipping line."
        record = file.readNext()
        continue
      } else {
        if ( instanceMedia.toLowerCase() == 'monograph' ) {
            siblingInstanceIdentifier.namespace = 'ISBN'
            instanceIdentifier.namespace = 'ISBN'
            String coverageStartDate = getFieldFromLine(record, acceptedFields, 'CoverageStatement.startDate')?.trim();
            if (coverageStartDate != null && coverageStartDate != '' ) {
              log.error("Unexpected coverage information for title: ${getFieldFromLine(record, acceptedFields, 'title')} of type: ${instanceMedia}")
            }
            addCoverage = false
        } else if ( instanceMedia.toLowerCase() == 'serial' ) {
            siblingInstanceIdentifier.namespace = 'ISSN'
            instanceIdentifier.namespace = 'ISSN'
        } else { // only serial or monograph publication_type allowed for kbart import
            log.error "Invalid publication_type \"${instanceMedia}\" for title: ${getFieldFromLine(record, acceptedFields, 'title')}, skipping line."
            record = file.readNext()
            continue
        }

        siblingInstanceIdentifier.value = getFieldFromLine(record, acceptedFields, 'siblingInstanceIdentifiers')
        instanceIdentifier.value = getFieldFromLine(record, acceptedFields, 'instanceIdentifiers')

        // Check that these aren't invalid identifiers, if they are, return an empty list
        List instanceIdentifiers = identifierValidator(instanceIdentifier)
        List siblingInstanceIdentifiers = identifierValidator(siblingInstanceIdentifier)
        
        // Examine the next record to see if it's a repeat. If so we should just add the extra coverage.
        final String[] currentRecord = record
        final List<CoverageStatement> coverage = []
        if (addCoverage) {
          while (addCoverage) {
            CoverageStatement cs = buildKBARTCoverage(record, acceptedFields)
            if (cs) { 
              coverage << cs 
            }
            record = file.readNext()
            addCoverage = record && sameTitle(acceptedFields, currentRecord, record)
          }
        } else {
          // Just read next...
          record = file.readNext()
        }
        
        PackageContentImpl pkgLine = new PackageContentImpl(
          title: getFieldFromLine(currentRecord, acceptedFields, 'title'),
          siblingInstanceIdentifiers: siblingInstanceIdentifiers,
          instanceIdentifiers: instanceIdentifiers,
          coverage: (coverage),
          url: getFieldFromLine(currentRecord, acceptedFields, 'url'),
          firstAuthor: getFieldFromLine(currentRecord, acceptedFields, 'firstAuthor'),
          embargo: getFieldFromLine(currentRecord, acceptedFields, 'embargo'),
          coverageDepth: getFieldFromLine(currentRecord, acceptedFields, 'coverageDepth'),
          coverageNote: getFieldFromLine(currentRecord, acceptedFields, 'coverageNote'),
          instanceMedia: getFieldFromLine(currentRecord, acceptedFields, 'instanceMedia'),

          // Pub Type should be populated with same data as Type for KBART
          instancePublicationMedia: getFieldFromLine(currentRecord, acceptedFields, 'instanceMedia'),
          
          instanceMedium: "electronic",

          dateMonographPublished: getFieldFromLine(currentRecord, acceptedFields, 'dateMonographPublished'),
          dateMonographPublishedPrint: getFieldFromLine(currentRecord, acceptedFields, 'dateMonographPublishedPrint'),

          monographVolume: getFieldFromLine(currentRecord, acceptedFields, 'monographVolume'),
          monographEdition: getFieldFromLine(currentRecord, acceptedFields, 'monographEdition'),
          firstEditor: getFieldFromLine(currentRecord, acceptedFields, 'firstEditor'),
          sourceIdentifier: getFieldFromLine(currentRecord, acceptedFields, 'sourceIdentifier') ?: getFieldFromLine(currentRecord, acceptedFields, 'titleId'),
          sourceIdentifierNamespace: getFieldFromLine(currentRecord, acceptedFields, 'sourceIdentifierNamespace') ?: packageSource
        )
        MDC.put('title', StringUtils.truncate(pkgLine.title))

        pkg.packageContents << pkgLine
      }
    }

    if (pkg.packageContents.size() > 0) {
      def result = packageIngestService.upsertPackage(pkg)
      packageImported = true
    } else {
      log.error("Package contents empty, skipping package creation")
    }
    
//    MDC.clear()
    return (packageImported)
  }
  
  private String getRawColumnvalue(Map<String, ? extends Map> acceptedFields, final String[] strArray, final String colName) {
    final int idx = (acceptedFields[colName]?.get('index') ?: -1) as int
    (idx >= 0 ? strArray[idx] : null)
  }
  
  private boolean sameTitle(final Map<String, ? extends Map> acceptedFields, final String[] currentRecord, final String[] record) {
    /* Ensure the following are equal.
    
      publication_title 
      print_identifier
      online_identifier
      title_url
      title_id
     */
    ['publication_title','print_identifier','online_identifier','title_url','title_id'].every { final String col ->
      getRawColumnvalue(acceptedFields, currentRecord, col) == getRawColumnvalue(acceptedFields, record, col)
    }
  }

  private String getFieldFromLine(String[] lineAsArray, Map acceptedFields, String fieldName) {
    //ToDo potentially work out how to make this slightly less icky, it worked a lot nicer without @CompileStatic
    final int idx = getIndexFromFieldName(acceptedFields, fieldName).toInteger()
    final value = (idx >= 0 ? lineAsArray[idx]?.trim() : null)
    value ? value : null // Emtpy strings are nulled.
  }

  private String getIndexFromFieldName(Map acceptedFields, String fieldName) {
    String index = (acceptedFields.values().find { it['field']?.equals(fieldName) })['index']
    return index;
  }

  private List shouldExist(Map acceptedFields, String fieldName) {
    boolean result = false
    String importField = acceptedFields.find { it.value['field']?.equals(fieldName) }?.key

    if (getIndexFromFieldName(acceptedFields, fieldName) != '-1') {
      result = true
    }
    return [result, importField];
  }

  private LocalDate parseDate(String date) {
    // We know that data coming in here matches yyyy, yyyy-mm or yyyy-mm-dd
    if (!date?.trim()) {
      return null;
    }

    LocalDate outputDate

    DateTimeFormatter yearFormat = new DateTimeFormatterBuilder()
    .appendPattern("yyyy")
    .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
    .toFormatter();

    DateTimeFormatter monthYearFormat = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM")
    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
    .toFormatter();
    try {
      switch(date) {
        case ~ '^\\d{4}\$':
          outputDate = LocalDate.parse(date, yearFormat);
          break;
        case ~ '^\\d{4}(-(\\d{2}))\$':
          outputDate = LocalDate.parse(date, monthYearFormat);
          break;
        default:
          outputDate = LocalDate.parse(date);
          break;
      }
    } catch (Exception e) {
      log.error("Could not parse date ${date}")
      outputDate = null
    }

    return outputDate;
  }

  private CoverageStatement buildKBARTCoverage(String[] lineAsArray, Map acceptedFields) {
    final String startDate = getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.startDate')
    final String endDate = getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.endDate')
    
    final LocalDate startDateLocalDate = parseDate(startDate)
    final LocalDate endDateLocalDate = parseDate(endDate)

    final CoverageStatement cs = new CoverageStatement(
      startDate: startDateLocalDate,
      startVolume: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.startVolume'),
      startIssue: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.startIssue'),
      endDate: endDateLocalDate,
      endVolume: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.endVolume'),
      endIssue: getFieldFromLine(lineAsArray, acceptedFields, 'CoverageStatement.endIssue')
    )

    if (!utilityService.checkValidBinding(cs)) {
      return null
    }
    
    cs
  }

  private List identifierValidator(Identifier identifier) {
    List identifiers = []
    if (utilityService.checkValidBinding(identifier)) {
      identifiers = [identifier]
    }

    return identifiers;
  }
}

package org.olf

import org.codehaus.groovy.syntax.RuntimeParserException
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg

import org.olf.kb.http.request.body.PackageSynchronisationBody

import com.k_int.okapi.OkapiTenantAwareController
import grails.converters.JSON
import grails.gorm.multitenancy.CurrentTenant
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.time.LocalDate

import org.springframework.web.multipart.MultipartFile
import org.apache.commons.io.input.BOMInputStream

import com.opencsv.ICSVParser
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder


@Slf4j
@CurrentTenant
class PackageController extends OkapiTenantAwareController<Pkg> {

  ImportService importService
  PackageSyncService packageSyncService

  PackageController() {
    super(Pkg)
  }


  // Set up endpoints to pause synchronisation and start synchronisation of multiple packages at once
  // Accepts a Body of the shape PackageSynchronisationBody
  /*
    {
      "packageIds": [],
      "syncState": "PAUSED" vs "SYNCHRONISING"
    }
   */
  def controlPackageSynchronization(PackageSynchronisationBody syncBody) {
    // Control mismatched request body
    if(!syncBody.validate()) {
      def errors = []
      syncBody.errors.allErrors.each {
        Map err = [
            field: it.getField(),
            message:it.getDefaultMessage()
        ]

        if (it.getField() == "syncState") {
          err["options"] = PackageSynchronisationBody.SyncState.values().collect { it.toString() }
        }

        errors << err
      }
      respond ([ errors: errors ], status: 400 )
      return;
    }

    // Now actually do the work

    Boolean syncState
    switch (syncBody.syncState.toString()) {
      case "SYNCHRONIZING":
        syncState = true;
        break;
      case "PAUSED":
        syncState = false;
        break;
      default:
        // This shouldn't be possible because of the validation above, here only for last second safety
        throw new RuntimeException("syncState somehow ended up as ${syncBody.syncState.toString()} and we don't know how to handle that.")
        break;
    }

    Map returnObj = packageSyncService.controlSyncStatus(syncBody.packageIds, syncState)

    respond(returnObj)
  }

  @Override
  def update() {
    Pkg instance = queryForResource(params.id);
    Object object_to_bind = getObjectToBind();

    // Check if Package has syncContentsFromSource set. If so,
    // and we attempt to CHANGE it, refuse
    if (
        object_to_bind.syncContentsFromSource != null &&
            instance.syncContentsFromSource != object_to_bind.syncContentsFromSource
    ) {
      response.sendError(422, "Directly editing syncContentsFromSource is not allowed");
    } else {
      super.update();
    }
  }

  def 'import' () {
    final bindObj = this.getObjectToBind()
    log.debug("Importing package: ${bindObj}")
    def importResult = importService.importPackageUsingErmSchema(bindObj as Map)

    log.debug("Import complete, attempting to find package in ERM")
    String packageId;
    switch(importResult.packageIds.size()) {
      case 0:
        log.error("Package import failed, no valid id returned");
        break;
      case 1:
        packageId = importResult.packageIds[0]
        break;
      default:
        log.warn("More than one package imported, can't return id")
        break;
    }

    Map result = [packageId: packageId]
    render (result as JSON)
    return;
  }

  def 'tsvParse' () {
    MultipartFile file = request.getFile('upload')

    Map packageInfo = [
      packageName: request.getParameter("packageName"),
      packageSource: request.getParameter("packageSource"),
      packageReference: request.getParameter("packageReference"),
      trustedSourceTI: request.getParameter("trustedSourceTI"),
      packageProvider: request.getParameter("packageProvider")
    ]

    BOMInputStream bis = new BOMInputStream(file.getInputStream());
    Reader fr = new InputStreamReader(bis);
    CSVParser parser = new CSVParserBuilder().withSeparator('\t' as char)
        .withQuoteChar(ICSVParser.DEFAULT_QUOTE_CHARACTER)
        .withEscapeChar(ICSVParser.DEFAULT_ESCAPE_CHARACTER)
      .build();

    CSVReader csvReader = new CSVReaderBuilder(fr).withCSVParser(parser).build();

    def completed = importService.importPackageFromKbart(csvReader, packageInfo)

    if (completed) {
      log.debug("KBART import success")
    } else {
      log.debug("KBART import failed")
    }
    return render ([:] as JSON)
  }

  def content () {
    respond doTheLookup(PackageContentItem) {
      eq 'pkg.id', params.'packageId'
      isNull 'removedTimestamp'
    }
  }

  def currentContent () {
    final LocalDate today = LocalDate.now()
    respond doTheLookup(PackageContentItem) {
      eq 'pkg.id', params.'packageId'
      and {
        or {
          isNull 'accessEnd'
          gte 'accessEnd', today
        }
        or {
          isNull 'accessStart'
          lte 'accessStart', today
        }
      }
      isNull 'removedTimestamp'
    }
  }

  def futureContent () {
    final LocalDate today = LocalDate.now()
    respond doTheLookup(PackageContentItem) {
      eq 'pkg.id', params.'packageId'
      gt 'accessStart', today
      isNull 'removedTimestamp'
    }
  }

  def droppedContent () {
    final LocalDate today = LocalDate.now()
    respond doTheLookup(PackageContentItem) {
      eq 'pkg.id', params.'packageId'
      lt 'accessEnd', today
      isNull 'removedTimestamp'
    }
  }

  List<String> fetchSources() {
    List<String> sources = Pkg.createCriteria().list {
      isNotNull('source')

      projections {
        distinct 'source'
      }
    }
    respond sources
  }
}


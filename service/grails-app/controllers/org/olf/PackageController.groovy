package org.olf

import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg

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

  PackageController() {
    super(Pkg)
  }

  // We need to protect syncContentsFromSource on PUT.
  //  We don't want this to be edited directly, instead it'll go via an action.
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


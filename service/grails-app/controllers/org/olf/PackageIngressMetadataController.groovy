package org.olf

import org.codehaus.groovy.syntax.RuntimeParserException
import org.olf.kb.PackageContentItem
import org.olf.kb.metadata.PackageIngressMetadata

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
class PackageIngressMetadataController extends OkapiTenantAwareController<PackageIngressMetadata> {
  PackageIngressMetadataController() {
    super(PackageIngressMetadata)
  }

  PackageIngressMetadata getMetadataForPackage() {
    List<PackageIngressMetadata> pimList = PackageIngressMetadata.executeQuery("""
      SELECT pim FROM PackageIngressMetadata pim WHERE pim.resource.id = :pkgId
    """.toString(), [pkgId: params.'packageId']);

    respond pimList[0]
  }
}


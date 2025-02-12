package org.olf

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW

import java.util.concurrent.TimeUnit

import org.olf.general.Org

import org.olf.general.StringUtils
import org.olf.general.IngestException

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.kb.Embargo
import org.olf.kb.PackageContentItem
import org.olf.kb.AlternateResourceName
import org.olf.kb.ContentType
import org.olf.kb.AvailabilityConstraint
import org.olf.kb.PackageDescriptionUrl
import org.olf.kb.Pkg
import org.olf.kb.Platform
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.RemoteKB
import org.olf.kb.TitleInstance
import org.slf4j.MDC

import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@CurrentTenant
class PackageSyncService {

  public Map controlSyncStatus(List<String> packageIds, boolean syncStatusBool) {
    // Convert each package in packageId to syncStatus
    boolean returnVal = false;
    Map returnObj = [
        packagesUpdated:0,
        packagesSkipped: 0,
        success: false
    ];

    Pkg.withNewTransaction {
      packageIds.each { String pkgId ->
        Pkg pkg = Pkg.get(pkgId);
        if (pkg.syncContentsFromSource != syncStatusBool) {
          pkg.syncContentsFromSource = syncStatusBool;
          pkg.save(failOnError: true);
          returnObj.packagesUpdated ++
        } else {
          returnObj.packagesSkipped ++
        }
      }
      // We ALSO need to queue up the reverse query to PushKB -- Maybe set those up as jobs so they can be async?
      returnObj.success = true;
    }

    return returnObj
  }
}

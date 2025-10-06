package org.olf

import com.k_int.okapi.OkapiTenantAdminService
import com.k_int.web.toolkit.refdata.RefdataValue
import org.olf.dataimport.internal.KBManagementBean
import org.olf.erm.Entitlement
import org.olf.general.jobs.ExternalEntitlementSyncJob
import org.olf.general.jobs.PackageIngestJob
import org.olf.general.jobs.PersistentJob
import org.olf.general.jobs.TitleIngestJob
import org.olf.kb.metadata.ResourceIngressType

import java.time.Instant

import static groovy.transform.TypeCheckingMode.SKIP
import org.springframework.scheduling.annotation.Scheduled
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * See http://guides.grails.org/grails-scheduled/guide/index.html for info on this way of
 * scheduling tasks
 */
@Slf4j
@CompileStatic
class KbManagementService {
  // This service used to hold MatchKey related methods, but is now empty.
  KBManagementBean kbManagementBean
  OkapiTenantAdminService okapiTenantAdminService
  EntitlementService entitlementService

  // Runs every hour, starting one minute after app startup
  @Scheduled(fixedDelay = 3600000L, initialDelay = 60000L)
  @CompileStatic(SKIP)
  triggerEntitlementJob() {
    ResourceIngressType ingressType = kbManagementBean.ingressType

    okapiTenantAdminService.allConfiguredTenantSchemaNames().each { tenant_schema_id ->
      log.debug "Create gokb resource job for tenant schema ${tenant_schema_id}"
      try {
        Tenants.withId(tenant_schema_id) {
          List<Entitlement> entitlements = entitlementService.findEntitlementsByAuthority(Entitlement.GOKB_RESOURCE_AUTHORITY)
          if (entitlements == null || entitlements?.size() == 0) {
            // If we can't find any entitlements for external resources, we can skip job creation.
            return;
          }

          RefdataValue inProgress = PersistentJob.lookupStatus('in_progress')
          RefdataValue queued = PersistentJob.lookupStatus('queued')
          ExternalEntitlementSyncJob runningOrQueuedEntitlementSyncJob = ExternalEntitlementSyncJob.findByStatusInList([
            inProgress,
            queued
          ])

          if (!runningOrQueuedEntitlementSyncJob) {
            // It doesn't matter if ingest jobs are queued, since only one job can run for a tenant at once rn
            log.info("Queuing ExternalEntitlementSyncJob for tenant ${tenant_schema_id} with ${entitlements.size()} entitlements to process");
            ExternalEntitlementSyncJob job = new ExternalEntitlementSyncJob(['name': "ExternalEntitlementSyncJob: ${Instant.now()}"])
            job.setStatusFromString('Queued')
            job.save(failOnError: true, flush: true)
          } else {
            log.info("Not creating ExternalEntitlementSyncJob for tenant ${tenant_schema_id} as one is already running or queued");
          }
        }
      } catch (Exception e) {
        log.error("Unexpected error in triggerEntitlementJob for tenant ${tenant_schema_id}", e);
      }
    }
  }
}

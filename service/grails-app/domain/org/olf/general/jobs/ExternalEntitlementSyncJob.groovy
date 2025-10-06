package org.olf.general.jobs

import grails.gorm.MultiTenant

class ExternalEntitlementSyncJob extends PersistentJob implements MultiTenant<ExternalEntitlementSyncJob>{

  final Closure getWork() {
    final Closure theWork = { final String jobId, final String tenantId ->
      log.info "Attempt to process external gokb entitlements"
      entitlementService.processExternalEntitlements()
    }.curry( this.id )
    theWork
  }


  static mapping = {
    table 'external_entitlement_sync_job'
    version false
  }
}

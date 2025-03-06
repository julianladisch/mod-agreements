package org.olf.general.jobs

import grails.gorm.MultiTenant

class PackageTriggerResyncJob extends PersistentJob implements MultiTenant<PackageTriggerResyncJob>{
  String packageId

  final Closure getWork() {
    final Closure theWork = { final String jobId, final String tenantId ->
      log.info "Attempt to retrigger package title sync"
      packageSyncService.resyncPackage(packageId)
    }.curry( this.id )
    theWork
  }

  static constraints = {
    packageId     nullable:false
  }

  static mapping = {
    table 'package_trigger_resync_job'
    version false
    packageId     column: 'package_id'
  }
}

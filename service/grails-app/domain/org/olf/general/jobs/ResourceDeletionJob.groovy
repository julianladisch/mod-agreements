package org.olf.general.jobs

import grails.gorm.MultiTenant
import groovy.json.JsonSlurper
import org.olf.general.ResourceDeletionJobType
import org.olf.kb.PackageContentItem

class ResourceDeletionJob extends PersistentJob implements MultiTenant<ResourceDeletionJob>{
  String resourceInputs // This comes in as a JSON string (List of string ids)
  ResourceDeletionJobType deletionJobType; // Types PackageDeletionJob, PciDeletionJob

  final Closure getWork() {
    final Closure theWork = { final String jobId, final String tenantId ->
      log.info "Attempting to run job type: ${deletionJobType}"
      List<String> idList // Currently, we assume the parsed JSON is always a List of String IDs.

      def parsedJson = new JsonSlurper().parseText(resourceInputs)
      if (!(parsedJson instanceof List)) {
        throw new IllegalArgumentException("resourceInputs JSON is not a list. Found type: ${parsedJson.getClass().name}")
      }
      idList = parsedJson as List<String>


      // Only PackageDeletionJob has an endpoint available, but could extend to PCI deletion jobs if needed.
      switch (deletionJobType) {

        case ResourceDeletionJobType.PackageDeletionJob:
          log.info "Executing PackageDeletionJob for IDs: ${idList}"
          ermResourceService.deleteResourcesPkg(idList)
          break

        // Unused currently, but creates a job that deletes PCIs as normal.
        case ResourceDeletionJobType.PciDeletionJob:
          log.info "Executing PciDeletionJob for IDs: ${idList}"
          ermResourceService.deleteResources(idList, PackageContentItem)
          break

        default:
          log.error "Unknown or unhandled ResourceDeletionJobType: ${deletionJobType}. Aborting job."
          throw new IllegalArgumentException("Unknown or unhandled ResourceDeletionJobType: ${deletionJobType}. Aborting job.")
          break
      }
    }.curry( this.id )
    theWork
  }

  static constraints = {
    resourceInputs     nullable:false
    deletionJobType nullable: false
  }

  static mapping = {
    table 'resource_deletion_job'
    version false
    resourceInputs    column: 'resource_inputs', type: 'text' // Store Id list as text
    deletionJobType column: 'deletion_job_type'
  }
}

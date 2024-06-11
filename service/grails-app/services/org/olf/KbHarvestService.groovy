package org.olf

import static groovy.transform.TypeCheckingMode.SKIP

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.olf.dataimport.internal.KBManagementBean
import org.olf.dataimport.internal.KBManagementBean.KBIngressType

import org.olf.general.jobs.PackageIngestJob
import org.olf.general.jobs.TitleIngestJob
import org.olf.kb.RemoteKB
import org.springframework.scheduling.annotation.Scheduled

import com.k_int.okapi.OkapiTenantAdminService
import com.k_int.okapi.OkapiTenantResolver

import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.regex.Matcher


/**
 * See http://guides.grails.org/grails-scheduled/guide/index.html for info on this way of
 * scheduling tasks
 */
@Slf4j
@CompileStatic
class KbHarvestService {

  private static final Long ONE_HOUR = new Long(1*60*60*1000)
  private static final Long ZERO = new Long(0)

  private Long getBufferDelay() {
    String buffer = System.getenv("KB_HARVEST_BUFFER");

    if (buffer) {
      switch (buffer) {
        case ~/([0-9]+)/:
          return "${Matcher.lastMatcher.group(1)}".toLong()
        case 'ZERO':
          return ZERO
        default:
          return ONE_HOUR
      }
    }

    return ONE_HOUR;
  }

  // Without this, the service will be lazy initialised, and the tasks won't be scheduled until an external
  // tries to access the instance.
  boolean lazyInit = false

  OkapiTenantAdminService okapiTenantAdminService
  KnowledgeBaseCacheService knowledgeBaseCacheService

  KBManagementBean kbManagementBean

  // All remote KBs not currently syncing and which have not been synced in the last 1 hour
  private static final PENDING_JOBS_HQL = '''select rkb.id
from RemoteKB as rkb
where rkb.type is not null
  and rkb.active = :true
  and rkb.rectype = :rectype
  and ( ( rkb.lastCheck is null ) OR ( ( :current_time - rkb.lastCheck ) > :lastCheckBuffer ) )
  and ( ( rkb.syncStatus is null ) OR ( rkb.syncStatus <> :inprocess ) )
  and rkb.name <> :local
'''

  @Subscriber('okapi:dataload:sample')
  public void onDataloadSample (final String tenantId, final String value, final String existing_tenant, final String upgrading, final String toVersion, final String fromVersion) {
    log.debug "Perform trigger sync for new tenant ${tenantId} via data load event"
    final String schemaName = OkapiTenantResolver.getTenantSchemaName(tenantId)
    try {
      triggerUpdateForTenant(schemaName)
    }
    catch ( Exception e ) {
      log.error("Unexpected error when responding to okapi:dataload:sample event for tenant ${schemaName}", e);
    }
  }

  @CompileStatic(SKIP)
  private void triggerUpdateForTenant(final String tenant_schema_id) {
    KBIngressType ingressType = kbManagementBean.ingressType

    if (ingressType == KBIngressType.Harvest) {
      /* ERM-1801.
       * We need to ensure TitleIngest happens BEFORE PackageIngest.
       * To avoid syncronisation errors, we trigger both daily and hourly tasks from the same method,
       * but add an extra condition to daily task query to find any that are fresher than a day old
       */
      Tenants.withId(tenant_schema_id) {

        // Look for jobs queued, in progress or created more recently than a day ago (Grabbing the first will suffice)
        TitleIngestJob titleJob = TitleIngestJob.executeQuery("""
          SELECT tj FROM TitleIngestJob AS tj
            WHERE (
              (tj.status.value = 'queued' OR tj.status.value = 'in_progress') OR
              (tj.dateCreated > :created)
            )
        """.toString(), [created: Instant.now().minus(1L, ChronoUnit.DAYS)])[0]

        PackageIngestJob packageJob = PackageIngestJob.findByStatusInList([
          PackageIngestJob.lookupStatus('Queued'),
          PackageIngestJob.lookupStatus('In progress')
        ])

        // ERM-1801 Ensure titleIngestJob gets created, saved and flushed first, so that the job runner can set that up in the queue ahead of packageIngestJob
        if (!titleJob) {
          titleJob = new TitleIngestJob(name: "Scheduled Title Ingest Job ${Instant.now()}")
          titleJob.setStatusFromString('Queued')
          titleJob.save(failOnError: true, flush: true)
        } else {
          log.info("Title harvester already running or scheduled. Ignore. tenant=${tenant_schema_id}, job=${titleJob}")
        }

        if (!packageJob) {
          packageJob = new PackageIngestJob(name: "Scheduled Package Ingest Job ${Instant.now()}")
          packageJob.setStatusFromString('Queued')
          packageJob.save(failOnError: true, flush: true)
        } else {
          log.info("Package harvester already running or scheduled. Ignore. tenant=${tenant_schema_id}, job=${packageJob}")
        }
      }
    } else {
      log.info("triggerUpdateForTenant is not valid when KBIngressType is (${ingressType})")
    }
  }

	// This task is only used directly through a call to the AdminController
  // Default 1hr/1min -- leave commented out the do not run option
  @Scheduled(fixedDelay = 3600000L, initialDelay = 60000L) // Run task every hour, wait 1 minute.
  //@Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = Long.MAX_VALUE) // Do not run on schedule
  void triggerSync() {
    log.debug "Running scheduled KB sync for all tenants :{}", Instant.now()

    okapiTenantAdminService.allConfiguredTenantSchemaNames().each { tenant_schema_id ->
      log.debug "Perform trigger sync for tenant schema ${tenant_schema_id}"
      try {
        triggerUpdateForTenant(tenant_schema_id as String)
      }
      catch ( Exception e ) {
        log.error("Unexpected error in triggerSync for tenant ${tenant_schema_id}", e);
      }
    }
  }
	

  // Want this closure to be accessible by each of the triggerCacheUpdate methods below, but compileStatic can't seem to SKIP a Closure declaration, so declare as return from a method instead
  @CompileStatic(SKIP)
  private Closure getClosure() {
    // Run the actual procession once we have a remotekb_id
    return { remotekb_id ->
      try {
        // We will check each candidate job to see if it has been picked up by some other thread or load balanced
        // instance of mod-agreements. We assume it has
        boolean continue_processing = false

        // Lock the actual RemoteKB record so that nobody else can grab it for processing
        RemoteKB.withNewSession {

          // Get hold of the actual job, lock it, and if it's still not in process, set it's status to in-process
          RemoteKB rkb = RemoteKB.lock(remotekb_id)

          // Now that we hold the lock, we can checm again to see if it's in-process
          if ( rkb.syncStatus != 'in-process' ) {
            // Set it to in-process, and continue
            rkb.syncStatus = 'in-process'
            continue_processing = true
          }

          // Save and close the transaction, removing the lock
          rkb.save(flush:true, failOnError:true)
        }

        // If we managed to grab a remote kb and update it to in-process, we had better process it
        if ( continue_processing ) {
          long gokb_sync_start_time = System.currentTimeMillis();
          log.debug("Run sync on ${remotekb_id} at ${gokb_sync_start_time}")
          try {
            // Even though we just need a read-only connection, we still need to wrap this block
            // with withNewTransaction because of https://hibernate.atlassian.net/browse/HHH-7421
            knowledgeBaseCacheService.runSync((String)remotekb_id)
          }
          catch ( Exception e ) {
            log.warn("Unexpected exception encountered during runSync for ${remotekb_id}", e)
          }
          finally {
            log.info("KbHarvestService.closure completed - ${System.currentTimeMillis()-gokb_sync_start_time}ms elapsed. Release sync status");

            // Finally, set the state to idle
            RemoteKB.withNewSession {
              RemoteKB rkb = RemoteKB.lock(remotekb_id)
              rkb.syncStatus = 'idle'
              rkb.lastCheck = System.currentTimeMillis()
              rkb.save(flush:true, failOnError:true)
            }
          }
        }
        else {
          log.info("Skipping remote kb ${remotekb_id} as it is currently in progress.");
        }
      }
      catch ( Exception e ) {
        log.error("Unexpected problem in RemoteKB Update",e);
      }
      finally {
        log.info("KB Harvest job closure exiting");
      }
    }
  } 
  

  // ERM-1801 We split the cache updating into package vs title, so that we can set up title ingest and package ingest jobs separately
  @CompileStatic(SKIP)
  public void triggerPackageCacheUpdate() {

    log.info("KBHarvestService::triggerPackageCacheUpdate()")
    Closure remoteKBProcessing = getClosure()
    // List all pending jobs that are eligible for processing - That is everything enabled and not currently in-process and has not been processed in the last hour

    RemoteKB.executeQuery(PENDING_JOBS_HQL,['true':true,
                                            'inprocess':'in-process',
                                            'rectype': RemoteKB.RECTYPE_PACKAGE,
                                            'current_time':System.currentTimeMillis(),
                                            'local':'LOCAL',
                                            'lastCheckBuffer': getBufferDelay()
                                            ],[lock:false]).each(remoteKBProcessing)

    log.info("KbHarvestService::triggerPackageCacheUpdate() completed")
  }

  @CompileStatic(SKIP)
  public void triggerTitleCacheUpdate() {
    log.debug("KBHarvestService::triggerTitleCacheUpdate()")
    Closure remoteKBProcessing = getClosure()
    // List all pending jobs that are eligible for processing - That is everything enabled and not currently in-process and has not been processed in the last hour

    RemoteKB.executeQuery(PENDING_JOBS_HQL,['true':true,
                                            'inprocess':'in-process',
                                            'rectype': RemoteKB.RECTYPE_TITLE,
                                            'current_time':System.currentTimeMillis(),
                                            'local':'LOCAL',
                                            'lastCheckBuffer': getBufferDelay()
                                            ],[lock:false]).each(remoteKBProcessing)

    log.debug("KbHarvestService::triggerTitleCacheUpdate() completed")
  }


  // ERM-1801 For those times where a manual cache update is required, this unified method will run through title ingest first and package ingest second
  @CompileStatic(SKIP)
  public void triggerCacheUpdate() {
    log.debug("KBHarvestService::triggerCacheUpdate()")
    Closure remoteKBProcessing = getClosure()
    // List all pending jobs that are eligible for processing - That is everything enabled and not currently in-process and has not been processed in the last hour

    // Run through remote KBs of rectype TITLE first
    RemoteKB.executeQuery(PENDING_JOBS_HQL,['true':true,
                                            'inprocess':'in-process',
                                            'rectype': RemoteKB.RECTYPE_TITLE,
                                            'current_time':System.currentTimeMillis(),
                                            'local':'LOCAL',
                                            'lastCheckBuffer': getBufferDelay()
                                            ],[lock:false]).each(remoteKBProcessing)
    
    // Run through remote KBs of rectype PACKAGE second
    RemoteKB.executeQuery(PENDING_JOBS_HQL,['true':true,
                                            'inprocess':'in-process',
                                            'rectype': RemoteKB.RECTYPE_PACKAGE,
                                            'current_time':System.currentTimeMillis(),
                                            'local':'LOCAL',
                                            'lastCheckBuffer': getBufferDelay()
                                            ],[lock:false]).each(remoteKBProcessing)

    log.debug("KbHarvestService::triggerCacheUpdate() completed")
  }

	public void handleInterruptedJob() {
		log.debug("KBHarvestService::handleInterruptedJob() called. Setting all remote KBs to idle");
		try {
		  RemoteKB.executeUpdate('update RemoteKB set syncStatus = :idle',[idle:'idle']);
		}
		catch ( Exception e ) {
			e.printStackTrace();
		}
		finally {
      // This might ought to be in debug to bring in line with other methods in here, 
      // although it might be more useful to know it happened since the call log above is "debug" level
      // Default logback config has this service at "DEBUG" level anyway so it might not matter.
			log.info("KBHarvestService::handleInterruptedJob() completed");
		}
	}
}

package org.olf.general.jobs

import services.k_int.core.SystemDataService

import static org.springframework.transaction.annotation.Propagation.MANDATORY
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import javax.annotation.PostConstruct

import org.hibernate.SessionFactory
import org.olf.ComparisonService
import org.olf.CoverageService
import org.olf.DocumentAttachmentService
import org.olf.ImportService
import org.olf.IdentifierService
import org.olf.KbHarvestService
import org.olf.KbManagementService
import org.olf.general.jobs.PersistentJob.Type
import org.springframework.jdbc.support.JdbcUtils

import com.k_int.okapi.OkapiTenantAdminService
import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.utils.GormUtils

import grails.events.EventPublisher
import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.Tenants
import grails.gorm.multitenancy.Tenant
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import services.k_int.core.AppFederationService
import services.k_int.core.FederationLockDataService
import services.k_int.core.FolioLockService

import com.k_int.web.toolkit.async.WithPromises
import com.k_int.web.toolkit.files.FileUploadService
import grails.core.GrailsApplication


@Slf4j
class JobRunnerService implements EventPublisher {
  
  // Any auto injected beans here can be accessed within the `work` runnable
  // of the job itself.
  OkapiTenantAdminService okapiTenantAdminService
  KbHarvestService kbHarvestService
  KbManagementService kbManagementService
  CoverageService coverageService
  DocumentAttachmentService documentAttachmentService
  ImportService importService
  ComparisonService comparisonService
  IdentifierService identifierService
  SessionFactory sessionFactory
  GrailsApplication grailsApplication

  // Access to the inputStream of FileObjects is now via this service instead of directly
  // to the LOB attached to the FileObject. Inject this here so it is available to the work
  // closure in ./grails-app/domain/org/olf/general/jobs/KbartImportJob.groovy and ./grails-app/domain/org/olf/general/jobs/PackageImportJob.groovy
  FileUploadService fileUploadService

  private static final ZOMBIE_JOB_QUERY='''select pj
from PersistentJob as pj
where pj.status <> :ended
and pj.runnerId not in ( :runners )
order by pj.dateCreated
'''
  
  private int CONCURRENT_JOBS_GLOBAL = 2 // We need to be careful to not completely tie up all our resource
	private int taskConcurrency
	
  private ThreadPoolExecutor executorSvc
	
	private ThreadPoolExecutor taskExecutorService
  
  @PostConstruct
  void init() {
    // Set up the Executor
    try {
      def concurrentJobsGlobalConfig = grailsApplication.config.getProperty('concurrentJobsGlobal', int);
      if (concurrentJobsGlobalConfig > 0) {
        CONCURRENT_JOBS_GLOBAL = concurrentJobsGlobalConfig;
      }
    } catch (Exception e) {
      log.error("Failed to read concurrentJobsGlobal from config: ${e}")
    }
    
    log.info("Configured jobConcurrency: ${CONCURRENT_JOBS_GLOBAL}")
		// Base the number of small jobs executable on the limit imposed on the default runner.
		taskConcurrency = CONCURRENT_JOBS_GLOBAL * 2
		log.info("Configured taskConcurrency: ${taskConcurrency}")

    // SO: This is not ideal. We don't want to limit jobs globally to 1 ideally. It should be
    // 1 per tenant, but that will involve implementing custom handling for the queue and executor.
    // While we only have 1 tenant, this will suffice.
    executorSvc = new ThreadPoolExecutor(
      CONCURRENT_JOBS_GLOBAL, // Core pool Idle threads.
      CONCURRENT_JOBS_GLOBAL, // Threads max.
      1000, // Millisecond wait.
      TimeUnit.MILLISECONDS, // Makes the above wait time in 'seconds'
      new LinkedBlockingQueue<Runnable>() // Blocking queue
    )
		
		taskExecutorService = new ThreadPoolExecutor(
      1, // Core pool Idle threads.
      taskConcurrency, // Threads max.
      5,
      TimeUnit.SECONDS, // Makes the above wait time in 'seconds'
      new LinkedBlockingQueue<Runnable>() // Blocking queue
    )

    // Raise an event to say we are ready.
    notify('jobs:job_runner_ready')
    notify('jobs:task_runner_ready')
  }
  
  private void jobEnded(final String tid, final String jid) {
    log.info "Finished task with jobId ${jid} and tenantId ${tid}"
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void beginJob(final String jid = null) {
    PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
    final String statusCat = pj.getStatusCategory()
    
    pj.started = Instant.now()
    pj.status = RefdataValue.lookupOrCreate(statusCat, 'In progress')
    pj.save(failOnError: true, flush:true)
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void endJob(final String jid = null) {
    PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
    final String statusCat = pj.getStatusCategory()
    
    pj.ended = Instant.now()
    pj.status = RefdataValue.lookupOrCreate(statusCat, 'Ended')
    
    if (pj.result == null) {
      final String resultCat = pj.getResultCategory()
      
      // If errors then set to partial.
      pj.result = RefdataValue.lookupOrCreate(resultCat, (pj.getErrorLog() ? 'Partial success' : 'Success'))
    }
    pj.save( failOnError: true, flush:true )
  }
  
  @Transactional(propagation=REQUIRES_NEW)
  public void failJob(final String jid = null) {
    PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
    final String resultCat = pj.getResultCategory()
    
    // If errors then set to partial.
    pj.result = RefdataValue.lookupOrCreate(resultCat, 'Failure')
    final String statusCat = pj.getStatusCategory()
    
    pj.ended = Instant.now()
    pj.status = RefdataValue.lookupOrCreate(statusCat, 'Ended')
    pj.save( failOnError: true, flush:true )
  }

  @Transactional(propagation=REQUIRES_NEW)
  public void clearJobAllocation(final String tId, final String jid) {
    Tenants.withId(tId) {
      PersistentJob job = PersistentJob.get(jid ?: JobContext.current.get().jobId)

      // If this job was queued, then we can just clear the runner ID
      job.runnerId = null
      job.save(flush: true, failOnError: true)
    }
  }

  //@Tenant({ SystemDataService.DATASOURCE_SYSTEM })
  @Transactional(propagation=REQUIRES_NEW)
  public void interruptJob(final String tId, final String jid) {
    
    Tenants.withId(tId) {
      PersistentJob pj = PersistentJob.get(jid ?: JobContext.current.get().jobId)
      final String resultCat = pj.getResultCategory()
      
      // If errors then set to partial.
      pj.result = RefdataValue.lookupOrCreate(resultCat, 'Interrupted')
      final String statusCat = pj.getStatusCategory()
      
      pj.ended = Instant.now()
      pj.status = RefdataValue.lookupOrCreate(statusCat, 'Ended')
      
      
      Runnable onInterrupted = pj.getOnInterrupted();

      // Call any job-specific handling
      if (Closure.isAssignableFrom(onInterrupted.class)) {
        // Change the delegate to this class so we can control access to beans.
        Closure onInterruptedC = onInterrupted as Closure
  //      final JobRunnerService me = this
        onInterruptedC.setDelegate(this)
        onInterruptedC.setResolveStrategy(Closure.DELEGATE_FIRST)

        // Also pass in the current tenant id and job id
        onInterrupted = onInterruptedC.curry(tId, jid)
      }

      onInterrupted.run()
      // I don't think we want to use the special thread pool here
      //executorSvc.execute(onInterrupted)
      pj.save(flush: true, failOnError: true)
    }
  }
  
  //@Tenant({ SystemDataService.DATASOURCE_SYSTEM })
  @Transactional(propagation=REQUIRES_NEW)
  public void allocateJob( final String tId, final String jid) {

      Tenants.withId(tId) {
        PersistentJob pj = PersistentJob.get( jid )
        final String _myId = appFederationService.getInstanceId()
        pj.runnerId = _myId

        // Set the runner id.
        pj.save(flush: true, failOnError:true)
      }
  }

  public void shutdown() {
    log.info("JobRunnerService::shutdown()");
  }
  
  
	//@Transactional(propagation = MANDATORY)
  protected Collection<String> getViableRunners() {
    appFederationService.allHealthyInstanceIds()
  }
	
  // Protected methods @Transactional gets ignored
	//@Transactional(propagation = MANDATORY)
  protected void cleanupAfterDeadRunners() {
    Collection<String> viableRunnerIds = getViableRunners()
    
    // Find all jobs in all registered tenants where the runner was the
    // instance being cleaned up and the status was in progress or queued
    // set progress to interrupted and reschedule job
    log.debug("JobRunnerService::cleanupAfterDeadRunner")
    okapiTenantAdminService.allConfiguredTenantSchemaNames().each { final String tenant_schema_id ->
      Tenants.withId(tenant_schema_id) {
        // Find all none edned jobs with the dead runner assigned
        final RefdataValue ended = PersistentJob.lookupStatus('Ended')

        // Find every job that was allocated to the runner.
				List <PersistentJob> zombie_jobs = PersistentJob.executeQuery(ZOMBIE_JOB_QUERY,[ended:ended, runners:viableRunnerIds]);
        zombie_jobs.each { job ->
          log.info "Found job ${job.id} that was allocated to a runner that has died in tenant ${tenant_schema_id}"
          if (job.status.value == 'queued') {
          
            log.info "Job ${job.id} was only queued, clear the allocation"
            clearJobAllocation( tenant_schema_id, job.id )
            
          } else {
            // This job was interrupted.
            log.info "Setting job status to interrupted for ${job.id}"
            interruptJob( tenant_schema_id, job.id )
          }
        }
        
      }
    }
  }
	
	private volatile boolean firstTick = true;
  
  @Subscriber('federation:tick:leader')
  void leaderTick(final String instanceId) {
		
		if (firstTick == true) {
			log.info("Skipping first tick in job runner to ensure datasources are present.")
			firstTick = false
			return
		}
		
		WithPromises.task { 
			Tenants.withId(SystemDataService.DATASOURCE_SYSTEM) {
        GormUtils.withTransaction {
          log.debug("JobRunnerService::leaderTick")
          cleanupAfterDeadRunners()
          findAndRunNextJob()
          log.debug("JobRunnerService::leaderTick:done")
        }
      }
		}
  }

  @Subscriber('federation:tick:drone')
  void droneTick(final String instanceId) {
		
		if (firstTick == true) {
			log.info("Skipping first tick in job runner to ensure datasources are present.")
			firstTick = false
			return
		}
		
		WithPromises.task {
			Tenants.withId(SystemDataService.DATASOURCE_SYSTEM) {
        GormUtils.withTransaction {
          log.debug("JobRunnerService::droneTick")
          findAndRunNextJob()
          log.debug("JobRunnerService::droneTick:done")
        }
      }
		}
  }
  
  FolioLockService folioLockService
  AppFederationService appFederationService
  
  private final Map<Instant, JobRunnerEntry> potentialJobs = new TreeMap<>()
  
	private boolean shouldCheckForNewJobs() {
		
		// Check scheduled task size...
		int queuedJobSize = 0
		int queuedTaskSize = 0
		
		for (JobRunnerEntry entry : potentialJobs.values()) {
			if (entry.type == Type.TASK) {
				queuedTaskSize ++
				continue
			}
			queuedJobSize ++
			
			if ( queuedJobSize >= CONCURRENT_JOBS_GLOBAL || queuedTaskSize >= taskConcurrency ) {
				return false
			}
		}
		
		true
		
	}
	
  // @Transactional on protected methods get ignored
	//@Transactional(propagation = MANDATORY)
  protected synchronized void findAndRunNextJob() {
    log.debug("JobRunnerService::findAndRunNextJob")
    
		final int jobCapacity = CONCURRENT_JOBS_GLOBAL - executorSvc.getActiveCount()
		final int taskCapacity = taskConcurrency - taskExecutorService.getActiveCount()
		final int activeCount = taskExecutorService.getActiveCount() + executorSvc.getActiveCount()
		
		if ( jobCapacity < 1 && taskCapacity < 1 ) {
			log.info "No free runners for jobs or tasks, active workers currently at ${activeCount}. Skipping"
			return
		}
    
    folioLockService.federatedLockAndDo("agreements:job:queue") {

      if (shouldCheckForNewJobs()) {
        log.debug "We have capacity to run jobs / tasks lets check the queue."
          
        okapiTenantAdminService.allConfiguredTenantSchemaNames().each { final String tenant_schema_id ->
          
          log.debug "Finding next jobs for tenant ${tenant_schema_id}"
          Tenants.withId(tenant_schema_id) {
            // Find a queued job with no runner assigned.
						
						final int unallocatedJobMaxCount = 3
						
						int unallocatedJobCount = 0
						int unallocatedTaskCount = 0
						
            final RefdataValue queued = PersistentJob.lookupStatus('Queued')
						for (PersistentJob j : PersistentJob.findAllByStatusAndRunnerIdIsNull(queued, [ sort: 'dateCreated', order: 'asc'] )) {
							final Type type = j.type
							
							if ( type == Type.JOB ) {
								if (unallocatedJobCount < unallocatedJobMaxCount) {
									unallocatedJobCount ++
								} else {
									if (unallocatedTaskCount >= taskCapacity) {
										break // Stop looking if we have all we need.
									}
									continue // Skip.
								}
							}

							// Else TASK
							else if ( type == Type.TASK ) {
								if (unallocatedTaskCount < taskCapacity) {
									unallocatedTaskCount ++
								} else {
									if (unallocatedJobCount >= unallocatedJobMaxCount) {
										break // Stop looking if we have all we need.
									}
									continue // Skip.
								}
							}
							
							// If we get this far we can add the potential job
							log.info("Scheduling potential ${j.type} [${j}] for tenant: [${tenant_schema_id}]")
							potentialJobs.put(j.dateCreated, JobRunnerEntry.of( j.type, j.id, tenant_schema_id) )
						}
          }
        }
      }
      
      log.debug "Potential jobs queue size currently ${potentialJobs.size()}"
      
      // Go through each one and check if each job is runnable.
      // Runnnable is no other tenant job with a RUNNING status
      final List<Instant> jobStamps = potentialJobs.keySet() as List
      
      final Set<String> busyTenants = []
      int totalSpace = jobCapacity + taskCapacity
      for (int i=0; (totalSpace > 0) && i<jobStamps.size(); i++) {
        final Instant key = jobStamps.get(i)
        final JobRunnerEntry entry = potentialJobs.get( key )
        final String tId = entry.tenantId
        final Type type = entry.type

        try {
          if (type == Type.JOB && busyTenants.contains(tId)) {
            log.debug "Already determined tenant ${tId} was busy. Cannot schedule job type, try the next entry."
						continue
          }
          
          Tenants.withId(tId) {
						if (type == Type.JOB) {
							// Jobs should be limited to 1 per tenant.
							
							// Check if we already have a "job" running.
	            final RefdataValue inProgress = PersistentJob.lookupStatus('In progress')
							final boolean tenantRunningJob = PersistentJob.findAllByStatus(inProgress)?.find { PersistentJob j ->
								j.type == Type.JOB
							}
							
							if (tenantRunningJob) {
								log.debug "Tenant ${tId} has at least one JOB type already in progess. Next entry"
								busyTenants << tId
								
								return // From the closure.
							}
						}

						// Either tenant not running job already, or this is a task. 
            
            // Attempt to schedule and run
            final String jobId = entry.jobId
						
            PersistentJob job = PersistentJob.read(jobId)
            
            // Safeguard against jobs that were removed for whatever reason.
            if (job == null) { 
              log.warn "Job ${jobId} has been deleted. Simply remove from queue"
              
              // Remove from the queue.
              potentialJobs.remove( key )
            }
		  
						final RefdataValue queued = PersistentJob.lookupStatus('Queued')
            if (job.status.id != queued.id || job.runnerId != null) {
              log.info "Job ${jobId} is either no longer queued or already allocated to a different runner"
              
              // Remove from the queue.
              potentialJobs.remove( key )
            } else {
              allocateJob(tId, job.id)
              boolean added = false;
              // Transaction needs to be explicitly wrapping this call now,
              // since the @Transactional on that protected method is now ignored
              GormUtils.withNewReadOnlyTransaction {
                added = executeJob(type, tId, job.id, key)
              }

              if ( added ) {
                totalSpace --
                potentialJobs.remove( key )
              }
            }
          }
        } catch ( Exception ex ) {
          // Make sure we remove the queue item on error. If this was an intermittent 
          // failure it will be re-added to a queue in a subsequent execution
          log.error("Exception when attempting to run job ID: '${entry.jobId}' for tenant: '${entry.tenantId}'. Removing from queue for now", ex)
          potentialJobs.remove( key )
        }
      }
    }
    log.debug("exiting JobRunnerService::findAndRunNextJob")
  }
  
/*   @Transactional(propagation=REQUIRES_NEW, readOnly=true)
  private Runnable getJobWork(final String tId, final String jobId) {
    Tenants.withId(tId) { PersistentJob.read( jobId )?.getWork() }
  } */
  
  // @Transactional ignored on private methods
  //@Transactional(propagation=REQUIRES_NEW, readOnly=true)
  private boolean executeJob ( final Type type, final String tId, final String jobId, final Instant key) {
    boolean added = false
    
    // Read the work.
    Runnable work;
    Tenants.withId(tId) { work = PersistentJob.read( jobId )?.getWork() }
    //Runnable work = getJobWork(tId, jobId)

    if (work == null) {
      log.error("Couldn't fetch workload for job ${jobId}")
    }
    
    if (Closure.isAssignableFrom(work.class)) {
      // Change the delegate to this class so we can control access to beans.
      Closure workC = work as Closure
//      final JobRunnerService me = this
      workC.setDelegate(this)
      workC.setResolveStrategy(Closure.DELEGATE_FIRST)

      // Also pass in the current tenant id.
      work = workC.curry(tId)
    }

    // We should wrap the work in a closure so we can ensure tenant id is set
    // as well as setting the job status on execution
    final Runnable currentWork = work
    work = { final String tid, final String jid, final Runnable wrk ->
      final String tenantName = OkapiTenantResolver.schemaNameToTenantId(tid)
      try {
        log.debug("Starting job execution");
        org.slf4j.MDC.clear()
        org.slf4j.MDC.setContextMap( jobId: '' + jid, tenantId: '' + tid, 'tenant': '' + tenantName)
        JobContext.current.set(new JobContext( jobId: jid, tenantId: tid ))
        Tenants.withId(tid) {
          beginJob(jid)
        }
        Tenants.withId(tid) {
          wrk.run()
          log.debug("Cleanly terminating job execution")
        }
        Tenants.withId(tid) {
          endJob(jid)
        }
      } catch (Exception e) {
        log.error (e.message)
        log.error ("Job execution failed", e)
        Tenants.withId(tid) {
          failJob(jid)
        }
        notify ('jobs:log_info', JobContext.current.get().tenantId, JobContext.current.get().jobId,  "Job execution failed: ${e.message}")
      } finally {
        JobContext.current.remove()
        org.slf4j.MDC.clear()
        jobEnded(tid, jid)
      }
    }.curry(tId, jobId, currentWork)

    try {
      // Execute.
      getRunnerForType(type).execute(work)

      // Remove from the queue.
      potentialJobs.remove( key )
      added = true
    } catch (RejectedExecutionException e) {
      // The global queue is full.
      log.warn("Executor couldn't accept the work.", e)
    }
    
    added
  }
	
	private ThreadPoolExecutor getRunnerForType(Type jobType) {
		if (jobType == Type.TASK) return taskExecutorService

		// Default.
		return executorSvc
	}
  
//  @Subscriber('jobs:job_created')
//  void handleNewJob(final String jobId, final String tenantId) {
//    // Attempt to append to queue.
//    log.info "onJobCreated(${jobId}, ${tenantId})"
//    enqueueJob(jobId, tenantId)
//  }
  
//  @Subscriber('okapi:tenant_enabled')
//  public void onTenantEnabled(final String tenantId) {
//    log.debug "New tenant (re)registered"
//    
//    // initialize the tenants jobs if these were in the deferred list
//    final String tenantSchema = OkapiTenantResolver.getTenantSchemaName(tenantId)
//    if (deferredTenants.contains(tenantSchema)) {
//      // load...
//      enqueueJobMap (initializeTenantJobs(tenantSchema))
//    }
//  }
}

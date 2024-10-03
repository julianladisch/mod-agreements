package org.olf

import com.k_int.okapi.OkapiTenantAdminService
import org.olf.general.jobs.JobRunnerService

import com.k_int.web.toolkit.files.S3FileObject;
import com.k_int.web.toolkit.files.LOBFileObject;

class BootStrap {

  def grailsApplication
  OkapiTenantAdminService okapiTenantAdminService
//  JobRunnerService jobRunnerService

  def init = { servletContext ->

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        log.info("mod-agreements shutdown hook - process terminating");
      }
    });
    
    log.info("mod-agreements startup report");
    log.info("${grailsApplication.getMetadata().getApplicationName()} (${grailsApplication.config?.getProperty('info.app.version')}) initialising");
    log.info("          build number -> ${grailsApplication.metadata['build.number']}");
    log.info("        build revision -> ${grailsApplication.metadata['build.git.revision']}");
    log.info("          build branch -> ${grailsApplication.metadata['build.git.branch']}");
    log.info("          build commit -> ${grailsApplication.metadata['build.git.commit']}");
    log.info("            build time -> ${grailsApplication.metadata['build.time']}");
    log.info("            build host -> ${grailsApplication.metadata['build.host']}");
    log.info("         Base JDBC URL -> ${grailsApplication.config.getProperty('dataSource.url')} / ${grailsApplication.config.getProperty('dataSource.username')}");
    log.info("    default_aws_region -> ${grailsApplication.config.getProperty('kiwt.filestore.aws_region')}");
    log.info("       default_aws_url -> ${grailsApplication.config.getProperty('kiwt.filestore.aws_url')}");
    log.info("    default_aws_bucket -> ${grailsApplication.config.getProperty('kiwt.filestore.aws_bucket')}");

    Map<String, String> env = System.getenv();
    env.each { name,value ->
      log.info("    ENV: ${name}=\"${value}\"");
    }

//    jobRunnerService.populateJobQueue()
  }

  def destroy = {
		log.info("mod-agreements destroy method");
  }
}

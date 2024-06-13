package org.olf.TIRS.WorkSourceIdentifier

import org.olf.TIRS.TIRSSpec

import org.olf.kb.RemoteKB

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.transactions.Transactional
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import grails.web.databinding.DataBindingUtils
import groovy.transform.CompileStatic

import spock.lang.*

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class WorkSourceIdentifierTIRSIngestJobSpec extends TIRSSpec {
  
  // EXAMPLE -- running job runner context and then cleaning up afterwards
  // These might need to be @Ignore-ed because they take a LONG time (~2m) to run in practice.
  // Potentially work needs doing to improve this more directly?

  // Test within job runner context (only run when WorkSourceIdTIRS is the chosen TIRS)
  //@Ignore
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test in job runner context' () {
    given: 'ingest of borked_ids'
      setupAndRunIngestJob('src/integration-test/resources/DebugGoKbAdapter/borked_ids.xml')
    when: 'We lookup TIs'
      def tiGet = doGet("/erm/titles", [stats: true]);
      assert tiGet != null;
    then: 'We have TIs in the system'
      tiGet.total == 1583;
    cleanup:
      cleanupTenant();
  }

  // Test within job runner context (only run when WorkSourceIdTIRS is the chosen TIRS)
  //@Ignore
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test cleanup worked' () {
    when: 'Fetching RemoteKBs'
      def kbGet = doGet("/erm/kbs", [stats: true]);
    then: 'We have no RemoteKBs'
      assert kbGet.total == 0
  }
}


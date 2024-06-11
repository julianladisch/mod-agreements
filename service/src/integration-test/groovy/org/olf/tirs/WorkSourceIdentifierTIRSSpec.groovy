package org.olf.tirs

import org.olf.dataimport.internal.PackageContentImpl
import org.olf.kb.TitleInstance

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.transactions.Transactional
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import grails.web.databinding.DataBindingUtils
import groovy.transform.CompileStatic
import spock.lang.*

@Integration
@Stepwise
class WorkSourceIdentifierTIRSSpec extends TIRSSpec {
  // titleInstanceResolverService is injected in baseSpec now

  // Test directly
  // TODO actually fill this out
  void 'Test directly' () {
    when: 'Do a thing'
      String hw = "Hello world"
    then: 'test a thing'
      true == true
  }

  // Test within job runner context (only run when WorkSourceIdTIRS is the chosen TIRS)
  // TODO actually fill this out
  @Requires({ instance.isWorkSourceTIRS() })
  void 'Test in job runner context' () {
    when: 'Do a thing 2'
      String hw = "Hello world 2"
    then: 'test a thing 2'
      true == true
  } 
}


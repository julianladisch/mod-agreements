package org.olf

import org.olf.kb.Platform

import com.k_int.okapi.OkapiTenantResolver

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*

@Integration
@Stepwise
class PlatformSpec extends BaseSpec {

  @Unroll
  void "Test Platform #name creation" ( final String platformUrl, final String name ) {
    when: 'Resolve platform from url #platformUrl'
    
      def platform = null
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        platform = Platform.resolve(platformUrl)
      }

    then: 'Name is #name'
      platform.name == name

    where:
      platformUrl                             || name
      'http://content.apa.org/journals/str'   || 'content.apa.org'

  }
}


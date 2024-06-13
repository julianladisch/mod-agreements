package org.olf.General

import org.olf.BaseSpec

import org.olf.dataimport.internal.TitleInstanceResolverService

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
class TitleServiceSpec extends BaseSpec {

  // titleInstanceResolverService is injected in baseSpec now

  @Shared PackageContentImpl content
  
  void 'Bind to content' () {
    when: 'Attempt the bind'
      content = new PackageContentImpl()
      DataBindingUtils.bindObjectToInstance(content, [
        'title':'Brain of the firm',
        'instanceMedium': 'electronic',
        'instanceMedia': 'monograph',
        'instanceIdentifiers': [ 
          [
            'namespace': 'eisbn',
            'value': '0713902191'
          ],
          [
            'namespace': 'eisbn',
            'value': '9780713902198'
          ] 
        ],
        'siblingInstanceIdentifiers': [ 
          [
            // 2e - print
            'namespace': 'isbn',
            'value': '047194839X'
          ]
        ],
        'sourceIdentifierNamespace': 'k-int',
        'sourceIdentifier': 'botf-123'
      ])
    
    then: 'Everything is good'
      noExceptionThrown()
  }

  void 'Test Title Resolution' () {

    when: 'Resolve title'

      def title_instance = null
      def num_identifiers = 0
      def matching_titles = []

      // We are exercising the service directly, normally a transactional context will
      // be supplied by the HTTPRequest, but we fake it here to talk directly to the service
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        // N.B. This is a groovy MAP, not a JSON document.
        TitleInstance.withNewTransaction {
          title_instance = TitleInstance.read(titleInstanceResolverService.resolve(content, true))
          num_identifiers = title_instance.identifiers.size()
          matching_titles = TitleInstance.findAllByName('Brain of the firm')
        }
      }

    then: 'New title created and identifier count matches'
      title_instance.name == 'Brain of the firm'
      title_instance.id != null
      // It would be nice to do this. but DON'T. Our session is terminated in the withId block above, so doing
      // this will cause the test to blow up as the session has gone away. Use the approach take, where we count
      // inside the block and check the count below.
      // title_instance.identifiers.size() == 2
      num_identifiers == 2
  }

  void "Second time around don't create a new title" () {

    when: 'Resolve the same title again'
      def title_instance = null
      def num_titles = 0
      def matching_titles = []

      // We are exercising the service directly, normally a transactional context will
      // be supplied by the HTTPRequest, but we fake it here to talk directly to the service
      Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
        TitleInstance.withNewTransaction {
          title_instance = TitleInstance.read(titleInstanceResolverService.resolve(content, true))
          assert title_instance != null
          matching_titles = TitleInstance.findAllByName('Brain of the firm')
          num_titles = matching_titles.size()
        }
      }

      // There are 2 instances here - not 1 - first and second edition.
      if ( num_titles != 2 ) {
        matching_titles.each { mt ->
          log.error("   -->TITLE : ${mt}");
        }
      }

    then: 'Same title is returned and not duplicated'
      title_instance.name == 'Brain of the firm'
      title_instance.id != null
      // It would be nice to do this. but DON'T. Our session is terminated in the withId block above, so doing
      // this will cause the test to blow up as the session has gone away. Use the approach take, where we count
      // inside the block and check the count below.
      // title_instance.identifiers.size() == 2 

      // 2 instances, 1 work
      num_titles == 2
  }

}


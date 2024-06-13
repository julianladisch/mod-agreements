package org.olf

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

import org.olf.general.StringTemplate
import org.olf.kb.Platform
import org.olf.kb.PlatformTitleInstance

import com.k_int.okapi.OkapiTenantResolver

import groovy.json.JsonSlurper

import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import spock.lang.*
import spock.util.concurrent.PollingConditions
import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class StringTemplateSpec extends BaseSpec {

  ImportService importService

  void "Test creation of StringTemplates" (
    final String the_name,
    final String the_rule,
    final String the_context
  ) {

    when: "Post to create new string template"
      Map respMap = doPost("/erm/sts", {
        'name' the_name
        'rule' the_rule
        'context' the_context // This can be the value or id but not the label
      })
    then: "Response is good, we have a new ID, and name/rule/context match input"
      respMap.id != null
      respMap.name == the_name
      respMap.rule == the_rule
      respMap.context.value == the_context
    where:
      the_name   || the_rule                                                                                                  || the_context
      'proxy1'   || 'http://sub-hh-{{replace (replace (removeProtocol inputUrl) \"link.\" \"\") \".com\" \".co.uk\"}}/proxy1' || 'urlproxier'
      'proxy2'   || 'proxy-2-stuff-{{platformLocalCode}}'                                                                     || 'urlproxier'
  }
  
  // Place to store the id of the PTI we load in the package for use in multiple tests
  
  void "Load Packages" (test_package_file) {
    when: 'File loaded'
      Map result = importPackageFromFileViaService(test_package_file, 'src/integration-test/resources/packages/stringTemplating')

    then: 'Package imported'
      result.packageImported == true

    where:
      test_package_file | _
      'simple_pkg.json' | _
  }


  void "Test PTIS created with existing templates" () {
    
    // Created PTIs should have templates created at the same time.

    when: "Fetch list of PTIs"
      def pti = fetchPTI()
      def templatedUrls = pti.templatedUrls

    then: "templatedUrls should now have 3 entries, one for each proxy and one defaultUrl"
      templatedUrls.size() == 3
      
    then: "templatedUrls should have the correctly manipulated urls in them"
      def proxy1TemplatedUrl = templatedUrls.findAll { tu ->
        tu.name == 'proxy1'
      }[0]
      def proxy2TemplatedUrl = templatedUrls.findAll { tu ->
        tu.name == 'proxy2'
      }[0]
      def defaultTemplatedUrl = templatedUrls.findAll { tu ->
        tu.name == 'defaultUrl'
      }[0]

      proxy1TemplatedUrl.url == 'http://sub-hh-springer.co.uk/10.1007/978-3-319-55227-9/proxy1'
      proxy2TemplatedUrl.url == 'proxy-2-stuff-'
      defaultTemplatedUrl.url == 'http://link.springer.com/10.1007/978-3-319-55227-9'
      // This is sufficient to check that the manipulation part is working for ALL contexts, since they're the same mechanism
  }

  void "Test updating PTI has immediate effect" () {
    
    String newUrl  = 'http://link.springer.com/new/url'
    given: "Fetched PTI"

      def pti = fetchPTI()
      
    when: 'Url is removed and PTI refetched'
      
      doPut("/erm/pti/${pti.id}", [
        'url': ''
      ])
      pti = fetchPTI()
    
    then: 'Url and Templated URLs updated (gone)'
      (pti.url ?: '') == ''
      pti.templatedUrls.size() == 0
      
    when: 'URL is updated and PTI refetched'
    
      doPut("/erm/pti/${pti.id}", [
        'url': newUrl
      ])
      pti = fetchPTI()
      
    then: 'Url and Templated URLs updated'
      pti.url == newUrl
      assertTrue( pti.templatedUrls.every {
        ( it.name == 'proxy1' && it.url == 'http://sub-hh-springer.co.uk/new/url/proxy1') ||
        ( it.name == 'proxy2' && it.url == 'proxy-2-stuff-' ) ||
        ( it.name == 'defaultUrl' && it.url == newUrl)
      })
  }
  
  void "Test updating Platform updates PTI" () {
    
    def conditions = new PollingConditions(timeout: 5)
    
    given: "Fetch a PTI and then the Platform"
      def pti = fetchPTI()
      
    when: 'Platform localCode is edited'
      doPut("/erm/platforms/${pti.platform.id}", [
        'localCode': 'shiney_local_code'
      ])
      
    then: 'Applicable URL templates eventually updated by background task'
      conditions.eventually {
        pti = fetchPTI()
        assertTrue( pti.templatedUrls.every {
          ( it.name == 'proxy1' && it.url == 'http://sub-hh-springer.co.uk/new/url/proxy1') ||
          ( it.name == 'proxy2' && it.url == 'proxy-2-stuff-shiney_local_code' ) ||
          ( it.name == 'defaultUrl' && it.url == pti.url)
        })
      }
  }
  
  void "Test CRUD operations on templates" () {
    def conditions = new PollingConditions(timeout: 5)
    def pti
    def sts = doGet("/erm/sts")
    
    when: 'Proxy1 rule is changed'
      def proxy1 = sts.findAll { st ->
        st.name == 'proxy1'
      }[0]

      proxy1 = doPut("/erm/sts/${proxy1.id}", [
        'rule': 'http://ethan-{{replace (removeProtocol inputUrl) \".com\" \".co.uk\"}}'
      ])


    then: 'Applicable URL templates eventually updated by background task'
    
      conditions.eventually {
        pti = fetchPTI()
        assertTrue( pti.templatedUrls.every {
          ( it.name == 'proxy1' && it.url == 'http://ethan-link.springer.co.uk/new/url') ||
          ( it.name == 'proxy2' && it.url == 'proxy-2-stuff-shiney_local_code' ) ||
          ( it.name == 'defaultUrl' && it.url == pti.url)
        })
      }
      
    when: 'we edit proxy 1 to include the PTI platform as a scope'
      
      proxy1 = doPut("/erm/sts/${proxy1.id}", {
        'idScopes' ([pti.platform.id])
      })
      
    then: 'Excluded URL template eventually removed by background task'
      conditions.eventually {
        pti = fetchPTI()
        assertFalse( pti.templatedUrls.any{ it.name == 'proxy1' } )
      }
      
    when: 'we create a url customiser NOT linked to the platform, and wait for a few seconds'
      def customiser = doPost("/erm/sts", {
        'name' 'customiser1'
        'rule' "http://customise-me:{{replace inputUrl \"a\" \"b\"}}"
        'context' 'urlcustomiser'
      })

      int currentUrlSize = pti.templatedUrls?.size() ?: 0
      Thread.sleep(3000)
      pti = fetchPTI()
      
      def applicableTemplates = doGet("/erm/sts/template/${pti.platform.id}")
      
    then: 'templated urls should not change length and template should not appear as applicable'
      
      pti.templatedUrls?.size() == currentUrlSize
      applicableTemplates.urlCustomisers.size() == 0
      

    when: "We add the PTIs Platform to the customiser's idScopes and recheck applicable templates"
      customiser = doPut("/erm/sts/${customiser.id}", {
        'idScopes' ([pti.platform.id])
      })
      applicableTemplates = doGet("/erm/sts/template/${pti.platform.id}")
      
    then: 'Expect template now applicable and 2 new URLs eventually added by background task'
    
    
      applicableTemplates.urlCustomisers.size() == 1
      conditions.eventually {
        pti = fetchPTI()
        def templatedUrls = pti.templatedUrls
        
        templatedUrls.size() == (currentUrlSize + 2)
        
        // customiser1 exists
        templatedUrls.any{ tu ->
          tu.name == 'customiser1'
        } == true
        
        // customiser1 is proxied by proxy2
        templatedUrls.any{ tu ->
          tu.name == 'proxy2-customiser1'
        } == true
      }
      
    when: "we remove the PTI from proxy1's idScopes"
      proxy1 = doPut("/erm/sts/${proxy1.id}", {
        'idScopes' ([""])
        // TODO right now there's a bug where we can't PUT an empty list to a @BindImmutably field and have it overwrite
        // This serves as a workaround,since empty string won't match any ids
      })

    then: 'Expect another 2 new URLs eventually added by background task'
      conditions.eventually {
        pti = fetchPTI()
        def templatedUrls = pti.templatedUrls
        
        templatedUrls.size() == (currentUrlSize + 4)
        
        // customiser1 exists
        templatedUrls.any{ tu ->
          tu.name == 'customiser1'
        } == true
        
        // customiser1 is proxied by proxy2
        templatedUrls.any{ tu ->
          tu.name == 'proxy2-customiser1'
        } == true
        
        templatedUrls.any{ tu ->
          tu.name == 'proxy1-customiser1'
        } == true
      }
      
    when: "We delete the customizer template"
      
      customiser = doDelete("/erm/sts/${customiser.id}")
      applicableTemplates = doGet("/erm/sts/template/${pti.platform.id}")
      currentUrlSize = pti.templatedUrls?.size() ?: 0
      
    then: 'Expect template to not be applicable and the URLs to eventually decrease by 3 in background task'
    
      applicableTemplates.urlCustomisers.size() == 0
      conditions.eventually {
        pti = fetchPTI()
        def templatedUrls = pti.templatedUrls
        
        templatedUrls.size() == (currentUrlSize - 3)
        
        // customiser1 to not exists in list
        templatedUrls.any{ tu ->
          tu.name == 'customiser1'
        } == false
      }
  }

  def fetchPTI() {
      // Find the PTI we imported from the package ingest
      def ptis = doGet("/erm/pti", [
        filters:[
          "titleInstance.name=i=Arithmetic of Finite Fields" // Case insensitive match
        ]
      ])
      return ptis[0]
  }
}


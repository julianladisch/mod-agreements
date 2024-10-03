package org.olf

import com.k_int.okapi.OkapiClient
import groovy.util.logging.Slf4j
import groovyx.net.http.HttpException
import org.olf.general.Org


/**
 * This class acts as a facade for any optional modules that this module would depend on. The initial use case
 * is for mod-vendors. An install might not have mod-vendors installed, in which case this module will only use
 * it's own orgs store. If mod-vendors is installed however, it will be consulted prior to creating a new vendor
 * record. If a record is found, a local proxy will be created which holds a reference to the upstream vendor record.
 * This pattern allows us to integrate with optional third party modules where they are present, but continue
 * if they are not. It also gives us a hook onto which we can cache information for performance reasons.
 */
@Slf4j
public class DependentModuleProxyService {

  def grailsApplication
  OkapiClient okapiClient

  public Org coordinateOrg(String orgName) {
    
    // Simply call the verb method on the client (get, post, put, delete). The client itself should take care of everything else.
    // Get and Delete take the uri with optional params map for the query string.
    // Post, Put and Patch take in the uri, data that can be converted to json (String or map) and optional params map for the query string.
    Org org = Org.findByName(orgName)
    
    if (!org) {
      log.debug "No local org for ${orgName}. Check vendors."
      
      def mod_vendor_lookup_result = null;
      if ( grailsApplication.config.getProperty('useModVendors', boolean) ) {
        // This fetches a max of 2 (we should decide how to handle multiple matches) vendors with an exact name match.
        mod_vendor_lookup_result = okapiClient.get("/vendor", [
          limit: 2,
          query: ('(name=="' + orgName + '")') // CQL
        ])
      }
      else {
        // Disable mod_vendor lookup
        mod_vendor_lookup_result = [ total_records: 0 ]
      }
      
      // Resp is a lazy map representation of the JSON returned by the module.
      /*
        {
          "vendors" : [ ... ],
          "total_records" : 0,
          "first" : 0,
          "last" : 0
        }
       */
      
      switch (mod_vendor_lookup_result.total_records) {
        case 1:
          // Exact match
          def result = mod_vendor_lookup_result.vendors[0]
          
          org = new Org(
            name: result.name,
            orgsUuid: result.id,
            sourceURI: "/vendor/${result.id}"
          ).save( flush:true, failOnError:true )
          break
          
        case 0:
          // No match
          // We should add in an option to create vendors if users configure that
          if ( grailsApplication.config.getProperty('createMissingVendors', boolean) ) {
            throw new RuntimeException("Not yet implemented");
          }

          // Create a new local one.
          log.debug "No vendor found. Adding local org for ${orgName}"
          org = (new Org(name:orgName)).save(flush:true, failOnError:true)
          break
          
        default:
          // Multiples.
          log.debug "Multiple matches for vendor with name ${orgName}"
      }
    }
    
    return org
  }
}

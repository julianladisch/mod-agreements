package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.acqunits.AcquisitionUnitPolicyEngineConfiguration
import com.k_int.accesscontrol.main.PolicyEngine
import com.k_int.accesscontrol.main.PolicyEngineConfiguration
import com.k_int.folio.FolioClientConfig
import com.k_int.okapi.OkapiClient
import com.k_int.okapi.OkapiHeaders
import com.k_int.okapi.OkapiTenantAwareController
import com.k_int.okapi.OkapiTenantResolver
import grails.gorm.multitenancy.Tenants
import org.springframework.security.core.userdetails.UserDetails

import javax.servlet.http.HttpServletRequest

/** * Base controller for policy engine operations.
 * This controller is designed to be extended by specific policy controllers.
 * It provides common functionality for interacting with the Policy Engine.
 *
 * It is used in AccessPolicyController and AccessPolicyAwareController
 *
 * @param <T> The type of resource this controller manages.
 */
class PolicyEngineController<T> extends OkapiTenantAwareController<T> {
  /**
   * The Okapi client used for interacting with the FOLIO Okapi gateway.
   */
  OkapiClient okapiClient

  PolicyEngineController(Class<T> resource) {
    super(resource)
  }

  PolicyEngineController(Class<T> resource, boolean readOnly) {
    super(resource, readOnly)
  }

  /**
   * Converts HTTP request headers into a simple String array of key-value pairs.
   * This is used to pass headers to the policy engine for context.
   * @param req The HttpServletRequest object.
   * @return A String array where elements alternate between header names and header values.
   */
  protected static String[] convertGrailsHeadersToStringArray(HttpServletRequest req) {
    List<String> result = new ArrayList<>()

    Collections.list(req.getHeaderNames()).forEach(headerName -> {
      Collections.list(req.getHeaders(headerName)).forEach(headerValue -> {
        result.add(headerName)
        result.add(headerValue)
      })
    })

    return result as String[]
  }

  /**
   * Retrieves the PolicyEngine instance configured for the current request.
   * This method builds the FolioClientConfig based on environment variables or Grails application configuration.
   * It also resolves the tenant and patron information.
   *
   * @return A PolicyEngine instance configured for the current request.
   */
  protected PolicyEngine getPolicyEngine() {
    // This should work regardless of whether we're in a proper FOLIO space or not now.
    // I'm not convinced this is the best way to do it but hey ho
    UserDetails patron = getPatron()
    String defaultPatronId = 'defaultPatronId'
    if (patron.hasProperty("id")) {
      defaultPatronId = patron.id
    }

    // Build the folio information via ENV_VARS, grailsApplication defaults OR fallback to "this folio".
    // Should allow devs to control where code is pointing dynamically without needing to comment/uncomment different folioConfigs here
    String baseOkapiUrl = grailsApplication.config.getProperty('accesscontrol.folio.baseokapiurl', String)
    boolean folioIsExternal = true
    if (baseOkapiUrl == null) {
      folioIsExternal = false

      // We need to do some fanagling now. If the okapi client thinks that we have an override, use it
      if (okapiClient.getOkapiHost() && okapiClient.getOkapiPort()) {
        baseOkapiUrl = "https://${okapiClient.getOkapiHost()}:${okapiClient.getOkapiPort()}"
      } else {
        // Otherwise we should use the X-OKAPI-URL... Use the static from grails-okapi to keep boundaries clean
        baseOkapiUrl = request.getHeader(OkapiHeaders.URL)
      }
    }

    FolioClientConfig folioClientConfig = FolioClientConfig.builder()
      .baseOkapiUri(baseOkapiUrl)
      .tenantName(grailsApplication.config.getProperty('accesscontrol.folio.tenantname', String, OkapiTenantResolver.schemaNameToTenantId(Tenants.currentId())))
      .patronId(grailsApplication.config.getProperty('accesscontrol.folio.patronid', String, OkapiTenantResolver.schemaNameToTenantId(defaultPatronId)))
      .userLogin(grailsApplication.config.getProperty('accesscontrol.folio.userlogin', String))
      .userPassword(grailsApplication.config.getProperty('accesscontrol.folio.userpassword', String))
      .build()

    // Keep logs to "trace" and ensure we only log INFO for prod
    log.trace("FolioClientConfig configured baseOkapiUri: ${folioClientConfig.baseOkapiUri}")
    log.trace("FolioClientConfig configured tenantName: ${folioClientConfig.tenantName}")
    log.trace("FolioClientConfig configured patronId: ${folioClientConfig.patronId}")
    log.trace("FolioClientConfig configured userLogin: ${folioClientConfig.userLogin}")
    log.trace("FolioClientConfig configured userPassword: ${folioClientConfig.userPassword}")

    // Turn off Acquisition unit access control with ACCESSCONTROL_ACQUNITS_ENABLED=false
    Boolean acqUnitsEnabled = grailsApplication.config.getProperty('accesscontrol.acqunits.enabled', Boolean, true)
    log.trace("Acquisition units enabled: ${acqUnitsEnabled}")

    // TODO This being spun up per request doesn't seem amazingly efficient -- but equally
    //  it's really just a POJO and each request could be from a different tenant so maybe it's fine
    PolicyEngine policyEngine = new PolicyEngine(
      PolicyEngineConfiguration
        .builder()
        // configure the acquisition unit policy engine
        .acquisitionUnitPolicyEngineConfiguration(
          AcquisitionUnitPolicyEngineConfiguration
            .builder()
            .enabled(acqUnitsEnabled)
            .folioClientConfig(folioClientConfig)
            .externalFolioLogin(folioIsExternal)
            .build()
        )
        // In future there may be other policy engines we want to configure here
        .build()
    )

    return policyEngine
  }
}

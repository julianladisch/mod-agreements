package org.olf

import com.k_int.accesscontrol.grails.AccessPolicyAwareController
import org.olf.erm.Entitlement

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import grails.gorm.transactions.Transactional



/**
 * Access to Entitlement resources
 */
@Slf4j
@CurrentTenant
class EntitlementController extends AccessPolicyAwareController<Entitlement> {
  EntitlementController() {
    super(Entitlement)
  }
  
  @Transactional(readOnly=true)
  def index(Integer max) {
    super.index(max)
  }

  @Transactional(readOnly=true)
  def show() {
    super.show()
  }

  def external() {
    Entitlement ent = new Entitlement ()
    ent.properties = params
    
    // Force external type.
    ent.type = 'external'
    
    // Ensure we have uppercase reference.
    ent.authority = ent.authority?.toUpperCase()
    respond ent
  }
}


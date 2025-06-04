package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.kb.Identifier
import org.olf.kb.IdentifierNamespace

/**
 * Controller to lookup Identifiers/Namespaces and get information about occurrences
 */
@Slf4j
@CurrentTenant
class IdentifierController extends OkapiTenantAwareController<Identifier> {
  IdentifierController() {
    super(Identifier)
  }

  @Override
  def index() {
    respond doTheLookup(Identifier) {

      if (params.minOccurrenceCount) {
        sizeGe 'occurrences', Integer.parseInt(params.minOccurrenceCount)
      }
    }
  }

  def namespaces() {
    respond doTheLookup(IdentifierNamespace) {}
  }
}

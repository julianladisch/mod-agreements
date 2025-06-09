package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.kb.PackageContentItem

/**
 * Explore package content items - the KB
 */
@Slf4j
@CurrentTenant
class PackageContentItemController extends OkapiTenantAwareController<PackageContentItem>  {

  PackageContentItemController() {
    super(PackageContentItem)
  }

  // TODO: Override DELETE going to ErmReouscreService.hierarchicalDelete methods
}


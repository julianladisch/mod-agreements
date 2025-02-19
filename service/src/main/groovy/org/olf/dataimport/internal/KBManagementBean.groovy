package org.olf.dataimport.internal

import org.olf.kb.metadata.ResourceIngressType

class KBManagementBean {
  ResourceIngressType ingressType

  // Add a way to override the "sync packages" default in the GOKBOAIAdapter
  boolean syncPackagesViaHarvest = false
}
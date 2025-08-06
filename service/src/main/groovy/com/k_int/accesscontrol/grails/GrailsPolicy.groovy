package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.http.responses.Policy
import grails.validation.Validateable

/**
 * Grails implementation of Policy. This class represents a single policy with its identifier.
 * It implements the Policy interface and is marked as Validateable for Grails validation
 */
class GrailsPolicy implements Policy, Validateable {
  String id

  public GrailsPolicy(String id) {
    this.id = id;
  }

  static constraints = {
    id nullable: false, blank: false
  }
}
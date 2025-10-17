package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyType
import com.k_int.accesscontrol.core.http.bodies.PolicyLink
import com.k_int.accesscontrol.core.ExternalPolicy
import grails.databinding.BindUsing
import grails.validation.Validateable

/** * Grails implementation of PolicyClaim.
 * This class represents a single policy claim with its associated properties.
 * It implements the PolicyClaim interface and is marked as Validateable for Grails validation.
 */
class GrailsPolicyLink implements PolicyLink, Validateable {
  String id

  // Built in binding gets a little confused at this nested level, help it along
  @BindUsing({ obj, source ->
    def policyMap = source['policy']
    if (policyMap instanceof Map && policyMap['id'] != null) {
      return new GrailsPolicy((String) policyMap['id'])
    }

    return null
  })
  GrailsPolicy policy
  AccessPolicyType type
  String description

  void setPolicy(ExternalPolicy p) {
    this.policy = (GrailsPolicy) p
  }

  static constraints = {
    id nullable: true, blank: false
    policy validator: { val, _obj, errors ->
      if (!val || val == '') {
        return false // Don't allow null or blank claims
      }

      if (!val.validate()) {
        // Copy errors from the nested object to the parent's error collection
        val.errors.allErrors.each { nestedError ->
          def fieldName = "policy.${nestedError.field}"
          errors.rejectValue(fieldName, nestedError.code, nestedError.arguments, nestedError.defaultMessage)
        }
      }

      // Return true at the end so that the rejectValues are found on the parent object
      return true
    }
    type nullable: false, blank: false
    description nullable: true, blank: false
  }
}
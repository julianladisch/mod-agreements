package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.AccessPolicyType
import com.k_int.accesscontrol.core.http.bodies.ClaimBody
import com.k_int.accesscontrol.core.http.bodies.PolicyClaim
import grails.validation.Validateable

/** * Grails implementation of ClaimBody.
 * This class represents a collection of policy claims and provides validation for them.
 * It implements the ClaimBody interface and is marked as Validateable for Grails validation.
 */
class GrailsClaimBody implements ClaimBody, Validateable {
  /** * Grails implementation of PolicyClaim.
   * This class represents a single policy claim with its associated properties.
   * It implements the PolicyClaim interface and is marked as Validateable for Grails validation.
   */
  class GrailsPolicyClaim implements PolicyClaim, Validateable {
    String id
    String policyId
    AccessPolicyType type
    String description

    static constraints = {
      id nullable: true, blank: false
      policyId nullable: false, blank: false
      type nullable: false, blank: false
      description nullable: true, blank: false
    }
  }

  List<GrailsPolicyClaim> claims

  static constraints = {
    claims validator: { val, _obj, errors ->
      if (!val) {
        return false // Don't allow null claims
      }

      val.eachWithIndex { claim, i ->
        // Manually trigger validation on the nested object
        if (!claim.validate()) {
          // Copy errors from the nested object to the parent's error collection
          claim.errors.allErrors.each { nestedError ->
            def fieldName = "claims[${i}].${nestedError.field}"
            errors.rejectValue(fieldName, nestedError.code, nestedError.arguments, nestedError.defaultMessage)
          }
        }
      }

      // Return true at the end so that the rejectValues are found on the parent object
      return true
    }
  }



}

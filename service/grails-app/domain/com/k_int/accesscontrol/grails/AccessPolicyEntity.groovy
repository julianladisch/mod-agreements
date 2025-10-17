package com.k_int.accesscontrol.grails

import com.k_int.accesscontrol.core.DomainAccessPolicy
import com.k_int.accesscontrol.core.AccessPolicyType
import grails.gorm.MultiTenant

import java.time.Instant

/**
 * GORM entity representing persisted access policies within the Grails application.
 * Extends the base AccessPolicy structure from AccessControl libraries.
 *
 * This class maps to the `access_policy` table and connects access control
 * data with internal ERM resources (e.g., SubscriptionAgreements).
 */
class AccessPolicyEntity implements MultiTenant<AccessPolicyEntity>, DomainAccessPolicy {
  String id

  // Access policy itself
  String description
  Instant dateCreated
  AccessPolicyType type
  String policyId

  // On what resource
  String resourceClass
  String resourceId

  static String TABLE_NAME = 'access_policy'

  static String ID_COLUMN = 'id'

  static String TYPE_COLUMN = 'acc_pol_type'
  static String DESCRIPTION_COLUMN = 'acc_pol_description'
  static String DATE_CREATED_COLUMN = 'acc_pol_date_created'
  static String POLICY_ID_COLUMN = 'acc_pol_policy_id'

  static String RESOURCE_CLASS_COLUMN = 'acc_pol_resource_class'
  static String RESOURCE_ID_COLUMN = 'acc_pol_resource_id'

  static mapping = {
            table TABLE_NAME
               id column: ID_COLUMN, generator: 'uuid2', length:36
          version column: 'version'

             type column: TYPE_COLUMN
      description column: DESCRIPTION_COLUMN
      dateCreated column: DATE_CREATED_COLUMN
         policyId column: POLICY_ID_COLUMN

    // Map the foreign key to the AccessPolicyContainer
    resourceClass column: RESOURCE_CLASS_COLUMN
       resourceId column: RESOURCE_ID_COLUMN
  }

  static constraints = {
             type nullable: false
      description nullable: true
         policyId blank: false
    resourceClass nullable: false
       resourceId nullable: false
  }
}

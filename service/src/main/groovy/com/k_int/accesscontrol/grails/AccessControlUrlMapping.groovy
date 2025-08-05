package com.k_int.accesscontrol.grails

/**
 * AccessControlUrlMapping is a utility class that builds URL mappings for access control operations.
 * It defines routes for checking permissions on creating, reading, updating, and deleting resources.
 */
class AccessControlUrlMapping {

  /**
   * Builds URL mappings for access control operations based on the provided list of mappings.
   * Each mapping contains a path and a controller name.
   *
   * @param mappings A list of maps, each containing 'path' and 'controller' keys.
   * @return A Closure that defines the URL mappings.
   */
  static Closure buildRoutes(String basePath, List<Map<String, String>> mappings) {
    return {
      mappings.each { mapping ->
        String path = mapping.path
        String controller = mapping.controller

        "${basePath}${path}/canCreate"(controller: controller, action: "canCreate", method: 'GET')

        "${basePath}${path}/$id/canRead"(controller: controller, action: "canRead", method: 'GET')
        "${basePath}${path}/$id/canUpdate"(controller: controller, action: "canUpdate", method: 'GET')
        "${basePath}${path}/$id/canDelete"(controller: controller, action: "canDelete", method: 'GET')
        "${basePath}${path}/$id/canApplyPolicies"(controller: controller, action: "canApplyPolicies", method: 'GET')

        // CLAIM endpoint is needed to be able to apply policies to a resource
        "${basePath}${path}/$id/claim"(controller: controller, action: "claim", method: 'POST')
        "${basePath}${path}/$id/policies"(controller: controller, action: "policies", method: 'GET')
      }

      "${basePath}/accessControl"(resources: 'accessPolicy', excludes: ['patch', 'save', 'create', 'edit', 'delete']) {
        collection {
          "/readPolicies"(controller: 'accessPolicy', action: 'getReadPolicyIds', method: 'GET')
          "/deletePolicies"(controller: 'accessPolicy', action: 'getDeletePolicyIds', method: 'GET')
          "/updatePolicies"(controller: 'accessPolicy', action: 'getUpdatePolicyIds', method: 'GET')
          "/createPolicies"(controller: 'accessPolicy', action: 'getCreatePolicyIds', method: 'GET')
          "/claimPolicies"(controller: 'accessPolicy', action: 'getClaimPolicyIds', method: 'GET')
          "/applyPolicies"(controller: 'accessPolicy', action: 'getApplyPolicyIds', method: 'GET')
        }
      }
    }
  }
}

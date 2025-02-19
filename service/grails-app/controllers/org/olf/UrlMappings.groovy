package org.olf

import org.olf.erm.SubscriptionAgreement

class UrlMappings {

  static mappings = {

    "/erm/compare" (controller: 'comparison', action: 'compare', method: 'POST')

    "/"(controller: 'application', action:'index')
    "500"(view: '/error')
    "404"(view: '/notFound')

    // Map /kbs to RemoteKBController
    '/erm/kbs'(resources: 'remoteKB')

    // Map /sas to SubscriptionAgreementController
    '/erm/sas'(resources: 'subscriptionAgreement') {
      "/resources"            (action: 'resources', method: 'GET')
      "/resources/all"        (action: 'resources', method: 'GET')
      "/resources/current"    (action: 'currentResources', method: 'GET')
      "/resources/future"     (action: 'futureResources', method: 'GET')
      "/resources/dropped"    (action: 'droppedResources', method: 'GET')

      // The "static" versions will ONLY return PCIs, not PTIs linked directly (for now)
      "/resources/static"    (action: 'staticResources', method: 'GET')
      "/resources/static/all"    (action: 'staticResources', method: 'GET')
      "/resources/static/current"    (action: 'staticCurrentResources', method: 'GET')
      "/resources/static/dropped"    (action: 'staticDroppedResources', method: 'GET')
      "/resources/static/future"    (action: 'staticFutureResources', method: 'GET')

      "/resources/export/$format?"          (controller: 'export', method: 'GET')
      "/resources/export/current/$format?"  (controller: 'export', action: 'current', method: 'GET')
      "/resources/export/all/$format?"  (controller: 'export', action: 'index', method: 'GET')
//      "/resources/export/future/$format?"   (controller: 'export', action: 'future', method: 'GET')
//      "/resources/export/dropped/$format?"  (controller: 'export', action: 'dropped', method: 'GET')

      "/export"          (action: 'export', method: 'GET')
      "/export/current"  (action: 'export', method: 'GET') {
        currentOnly = true
      }

      '/clone' (controller: 'subscriptionAgreement', action: 'doClone', method: 'POST')

      '/linkedLicenses' {
        controller = 'remoteLicenseLink'
        method = 'GET'
        filters = { "owner==${params.subscriptionAgreementId}" }
      }

      '/usageDataProviders' {
        controller = 'usageDataProvider'
        method = 'GET'
        filters = { "owner==${params.subscriptionAgreementId}" }
      }

      // Root level extensions
      collection {
        '/publicLookup' (action: 'publicLookup', method: 'GET') {
          perPage = { Math.min(params.int('perPage') ?: params.int('max') ?: 5, 5) }
        }

        '/linkedLicenses' {
          controller = 'remoteLicenseLink'
          method = 'GET'
        }

        '/usageDataProviders' {
          controller = 'usageDataProvider'
          method = 'GET'
        }

        "/validate/$prop?" (controller: 'validate', method: 'POST') {
          domain = SubscriptionAgreement.class.name
        }
      }
    }

    '/erm/sts' (resources: 'stringTemplate') {
      collection {
        "/template"  (controller: 'stringTemplate', action: 'refreshTemplatedUrls', method: 'GET')
        "/template/$id"  (controller: 'stringTemplate', action: 'getStringTemplatesForId', method: 'GET')
      }
    }

    '/erm/titles'(resources: 'title', excludes: ['patch', 'save', 'create', 'edit', 'delete']) {
      collection {
        "/entitled" (action: 'entitled')
        "/electronic" (action: 'electronic', method: 'GET')
      }
    }

    '/erm/pti' (resources: 'platformTitleInstance', excludes: ['patch', 'save', 'create', 'edit', 'delete'])

    '/erm/packages'(resources: 'package') {
      collection {
        "/import" (controller: 'package', action: 'import', method: 'POST')
        "/tsvParse" (controller: 'package', action: 'tsvParse', method: 'POST')
        "/sources" (controller: 'package', action: 'fetchSources', method: 'GET')
        "/controlSync" (controller: 'package', action: 'controlPackageSynchronization', method: 'POST')
        '/metadata' (resources: 'packageIngressMetadata', excludes: ['update', 'patch', 'save', 'create', 'edit', 'delete'])
      }

      "/content"         (controller: 'package', action: 'content', method: 'GET')
      "/content/all"     (controller: 'package', action: 'content', method: 'GET')
      "/content/current" (controller: 'package', action: 'currentContent', method: 'GET')
      "/content/future"  (controller: 'package', action: 'futureContent', method: 'GET')
      "/content/dropped" (controller: 'package', action: 'droppedContent', method: 'GET')
      "/metadata"        (controller: 'packageIngressMetadata', action: 'getMetadataForPackage', method: 'GET')
    }

    "/erm/pci"(resources:'packageContentItem')
    "/erm/platforms"(resources:'platform')
    "/erm/entitlements"(resources:'entitlement', excludes: ['patch']) {
      collection {
        "/external" ( action: 'external' )
      }
    }
    '/erm/contacts'(resources: 'internalContact', excludes: ['update', 'patch', 'save', 'create', 'edit', 'delete'])

    '/erm/refdata'(resources: 'refdata') {
      collection {
        "/$domain/$property" (controller: 'refdata', action: 'lookup', method: 'GET')
      }
    }

    '/erm/org'(resources: 'org') {
      collection {
        "/find/$id"(controller:'org', action:'find')
      }
    }

    "/erm/admin/$action"(controller:'admin')

    "/erm/content"(controller:'subscribedContent', action:'index')
    "/erm/content/$action"(controller:'subscribedContent')

    "/codex-instances" ( controller:'subscribedContent', action:'codexSearch', stats:'true')
    "/codex-instances/$id" ( controller:'subscribedContent', action:'codexItem')

//    "/erm/knowledgebase" ( controller:'kb', action:'index')
//    "/erm/knowledgebase/$action" ( controller:'kb' )

    "/erm/jobs" ( resources:'persistentJob', excludes: ['update', 'patch', 'save']) {
      collection {
        "/type/$type" ( action: 'listTyped', method: 'GET' )
        "/$type" ( action: 'save', method: 'POST' )
      }

      "/fullLog" ( controller: 'persistentJob', action: 'fullLog', method: 'GET' )
      "/fullLogStream" ( controller: 'persistentJob', action: 'fullLogStream', method: 'GET' )
      "/errorLog" ( controller: 'persistentJob', action: 'errorLog', method: 'GET' )
      "/errorLogStream" ( controller: 'persistentJob', action: 'errorLogStream', method: 'GET' )
      "/infoLog" ( controller: 'persistentJob', action: 'infoLog', method: 'GET' )
      "/infoLogStream" ( controller: 'persistentJob', action: 'infoLogStream', method: 'GET' )
      "/downloadFileObject" ( controller: 'persistentJob', action: 'downloadFileObject', method: 'GET' )
    }


     // This is the URL path used by the eresources screen.
     // See http://docs.grails.org/latest/guide/theWebLayer.html#embeddedVariables#_dynamically_resolved_variables for information on
     // how we might make this path more dynamic.
    "/erm/resource" ( resources:'resource', excludes: ['delete', 'update', 'patch', 'save', 'edit', 'create']) {
      collection {
        "/electronic" ( action:'electronic', method: 'GET')
      }
      "/static/entitlementOptions" ( action:'staticEntitlementOptions', method: 'GET')
      "/entitlementOptions" ( action:'entitlementOptions', method: 'GET')
      "/entitlements" ( action:'entitlements', method: 'GET' )
      "/entitlements/related" ( action:'relatedEntitlements', method: 'GET' )
    }

    "/erm/files" ( resources:'fileUpload', excludes: ['update', 'patch', 'save', 'edit', 'create']) {
      collection {
        '/' (controller: "fileUpload", action: "uploadFile", method: 'POST')
      }
      "/raw" ( controller: "fileUpload", action: "downloadFile", method: 'GET' )
    }

    '/erm/custprops'(resources: 'customPropertyDefinition') {
      collection {
        "/" (controller: 'customPropertyDefinition', action: 'index')
        "/contexts" (controller: 'customPropertyDefinition', action: "fetchContexts", method: 'GET')
      }
    }

    // export endpoints
    "/export/$format?"          (controller: 'export', method: 'GET')
    "/export/current/$format?"  (controller: 'export', action: 'current', method: 'GET')
//    "/export/future/$format?"   (controller: 'export', action: 'future', method: 'GET')
//    "/export/dropped/$format?"  (controller: 'export', action: 'dropped', method: 'GET')


    "/erm/validate/$domain/$prop?" (controller: 'validate', method: 'POST')

    "/erm/entitlementLogEntry" ( resources: 'EntitlementLogEntry')

    "/erm/settings/appSettings" (resources: 'setting');

    "/erm/pushKB/pkg" (controller: 'pushKB', action: 'pushPkg', method: 'POST')
    "/erm/pushKB/pci" (controller: 'pushKB', action: 'pushPci', method: 'POST')
    // Two GET ONLY endpoints for sessions/chunks
    "/erm/pushKB/sessions" (resources: 'pushKBSession', excludes: ['delete', 'update', 'patch', 'save', 'edit', 'create'])
    "/erm/pushKB/chunks" (resources: 'pushKBChunk', excludes: ['delete', 'update', 'patch', 'save', 'edit', 'create']) {
      "/fullLog" ( controller: 'pushKBChunk', action: 'fullLog', method: 'GET' )
      "/fullLogStream" ( controller: 'pushKBChunk', action: 'fullLogStream', method: 'GET' )
      "/errorLog" ( controller: 'pushKBChunk', action: 'errorLog', method: 'GET' )
      "/errorLogStream" ( controller: 'pushKBChunk', action: 'errorLogStream', method: 'GET' )
      "/infoLog" ( controller: 'pushKBChunk', action: 'infoLog', method: 'GET' )
      "/infoLogStream" ( controller: 'pushKBChunk', action: 'infoLogStream', method: 'GET' )
    }

    "/dashboard/definitions" (controller: 'dashboardDefinitions', action: 'getDefinitions' ,method: 'GET')
  }
}

package org.olf

class UrlMappings {

  static mappings = {

    "/"(controller: 'application', action:'index')
    "/_/tenant"(controller: 'okapi', action:'tenant')
    "500"(view: '/error')
    "404"(view: '/notFound')

    // Map /kbs to RemoteKBController
    '/kbs'(resources: 'remoteKB')

    // Map /sas to SubscriptionAgreementController
    '/sas'(resources: 'subscriptionAgreement') {
       "/addToAgreement"(action:'addToAgreement');
    }

    "/admin/$action"(controller:'admin')

    "/content"(controller:'subscribedContent', action:'index')
    "/content/$action"(controller:'subscribedContent')

    "/codex-instances" ( controller:'subscribedContent', action:'codexSearch')
    "/codex-instances/$id" ( controller:'subscribedContent', action:'codexItem')
  }
}

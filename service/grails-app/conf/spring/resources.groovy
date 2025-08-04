import org.olf.dataimport.internal.titleInstanceResolvers.*
import org.olf.dataimport.internal.KBManagementBean
import org.olf.kb.metadata.ResourceIngressType

// Place your Spring DSL code here
beans = {
  /* --- Swapping these will change the way mod-agreements handles resolution of TitleInstances --- */
  String TIRS = System.getenv("TIRS")
  switch (TIRS) {
    case 'IdFirst':
      titleInstanceResolverService(IdFirstTIRSImpl)
      break;
    case 'TitleFirst':
      titleInstanceResolverService(TitleFirstTIRSImpl)
      break;
    case 'WorkSourceIdentifier':
    default:
      titleInstanceResolverService(WorkSourceIdentifierTIRSImpl)
      break;
  }
  
  //
  //titleInstanceResolverService(WorkSourceIdentifierTIRSImpl)

  /*
    Diagram of the structure of the TIRSs

 ┌─────────────┐      ┌──────────────┐
 │BaseTIRSUtils├──┬──►│TitleFirstTIRS│
 └─────────────┘  │   └──────────────┘
                  │   ┌───────────┐   ┌────────────────────────┐
                  └──►│IdFirstTIRS├──►│WorkSourceIdentifierTIRS│
                      └───────────┘   └────────────────────────┘
  */

  // Swap between PushKB and Harvest processes to get data into internal KB
  String INGRESS_TYPE = System.getenv("INGRESS_TYPE")
  String SYNC_PACKAGES_VIA_HARVEST = System.getenv("SYNC_PACKAGES_VIA_HARVEST")

  kbManagementBean(KBManagementBean) {
    switch (INGRESS_TYPE) {
      case 'PushKB':
        ingressType = ResourceIngressType.PUSHKB
        break;
      case 'Harvest':
      default:
        ingressType = ResourceIngressType.HARVEST
        break;
    }

    switch (SYNC_PACKAGES_VIA_HARVEST) {
      case true:
      case 'true':
      case 'True':
        syncPackagesViaHarvest = true
        break;
      case false:
      case 'false':
      case 'False':
      default:
        syncPackagesViaHarvest = false
        break;
    }
  }

	/* Original list
  okapiAuthenticationFilter
  securityRequestHolderFilter
  securityContextPersistenceFilter
  logoutFilter
  authenticationProcessingFilter
  securityContextHolderAwareRequestFilter
  rememberMeAuthenticationFilter
  anonymousAuthenticationFilter
  formContentFilter
  exceptionTranslationFilter
  filterInvocationInterceptor


  springSecurityFilterChain.filterChainMap = [
        '/**': [
            'okapiAuthenticationFilter',
            'securityRequestHolderFilter',
            'securityContextHolderAwareRequestFilter',
            'anonymousAuthenticationFilter',
            'exceptionTranslationFilter',
            'filterSecurityInterceptor'
        ]
    ]
  */
}

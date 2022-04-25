package org.olf

import grails.converters.JSON
import org.hibernate.Hibernate
import org.hibernate.sql.JoinType
import org.olf.erm.Entitlement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance

import com.k_int.okapi.OkapiTenantAwareController
import grails.gorm.DetachedCriteria
import org.hibernate.criterion.CriteriaSpecification
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import java.time.Duration
import java.time.Instant

@Slf4j
@CurrentTenant
class ResourceController extends OkapiTenantAwareController<ErmResource>  {

  ResourceController() {
    // True means read only. This should block post and puts to this.
    super(ErmResource, true)
  }

  // LEFT JOIN query
  def electronic () {
    respond doTheLookup ({
      def res = ErmResource;
      or {
        eq 'class', Pkg
        
        and {
          createAlias('platformInstances', 'presentPtis', CriteriaSpecification.LEFT_JOIN)
          eq 'class', TitleInstance
          eq 'subType', TitleInstance.lookupOrCreateSubType('electronic')
          
          isNotEmpty('presentPtis.packageOccurences')
        }
      }
    })
  }
  
  /**
   * This is an availability checker for the resource. Show me all the places I can get this resource.
   * @return List of resources representing things that can be added to an Entitlement
   */
  @Transactional(readOnly=true)
  def entitlementOptions ( final String resourceId ) {
    log.debug("entitlementOptions(${resourceId})");

    // Easiest way to check that this resource is a title is to read it in as one.
    // We use criteria here to ensure
    
    final TitleInstance ti = resourceId ? TitleInstance.findByIdAndSubType ( resourceId, TitleInstance.lookupOrCreateSubType('electronic') ) : null
    
    log.debug("Got ti ${ti?.id}");
    
    //For issue ERM-285
    if (!ti) {
      //Check to see if the resourceId points to a package or a PCI
      final Pkg pkg = resourceId ? Pkg.read( resourceId ) : null
      final PackageContentItem pci = resourceId ? PackageContentItem.read( resourceId ) : null
      
      if (!pkg && !pci) {
        //if not then we return the empty set
        //response.status = 404
        respond ([]);
        return 
      } else {
        pkg ? respond ([pkg]) : respond ([pci]);
        return
      }
    }
    // We have a matching title. We need to work out where we can get the title from. This means finding all
    // resources that can be added to an Entitlement, that lead back to this title
    // Lets build the base query and pass into the simpleLookupService.
    // This will allow the usual parameters to be used to filter the results even further.
    final Instant start = Instant.now()
    log.debug("Start query ${start}")
    respond doTheLookup ({
      readOnly(true)
      
      or {
          
          // PTIs
//          'in' 'id', new DetachedCriteria(PlatformTitleInstance).build {
//            readOnly (true)
//            
//            eq 'titleInstance', ti
//              
//            projections {
//              property ('id')
//            }
//          }
          
          // PCIs
          'in' 'id', new DetachedCriteria(PackageContentItem, 'direct_pci').build {
            readOnly (true)
            
            pti {
              eq 'titleInstance.id', resourceId
            }
              
            isNull 'removedTimestamp'

            projections {
              property ('id')
            }
            
          }
          
          // Packages.
          'in' 'id', new DetachedCriteria(PackageContentItem, 'pkg_pcis').build {
            readOnly (true)
            
            pti {
              eq 'titleInstance.id', resourceId
            }
            isNull 'removedTimestamp'

            projections {
              property ('pkg.id')
            }
          }
        }
    })
    log.debug("completed in ${Duration.between(start, Instant.now()).toSeconds()} seconds")
  }
  
  private final Closure entitlementCriteria = { final Class<? extends ErmResource> resClass, final ErmResource res ->
    switch (resClass) {
      case TitleInstance:
        or {
          'in' 'resource.id', new DetachedCriteria(PlatformTitleInstance,  'ti_ptis').build {
            readOnly (true)
            
            eq 'titleInstance.id', res.id
              
            projections {
              property ('id')
            }
          }
          
          // PCIs
          'in' 'resource.id', new DetachedCriteria(PackageContentItem, 'ti_pcis').build {
            readOnly (true)
            
            pti {
              eq 'titleInstance.id', res.id
            }
              
            projections {
              property ('id')
            }
          }
          
          // Packages.
          'in' 'resource.id', new DetachedCriteria(PackageContentItem, 'ti_pkg_pcis').build {
            readOnly (true)
            pti {
              eq 'titleInstance.id', res.id
            }

            isNull 'removedTimestamp'

            projections {
              property ('pkg.id')
            }
          }
        }
        break
      case PlatformTitleInstance:
        or {
          eq 'resource.id', res.id
          
          // PCIs
          'in' 'resource.id', new DetachedCriteria(PackageContentItem, 'pti_pci').build {
            readOnly (true)
            
            eq 'pti.id', res.id
              
            projections {
              property ('id')
            }
          }
          
          // Packages.
          'in' 'resource.id', new DetachedCriteria(PackageContentItem, 'pti_pkg_pci').build {
            readOnly (true)
            
            eq 'pti.id', res.id

            isNull 'removedTimestamp'

            projections {
              property ('pkg.id')
            }
          }
        }
        break
        
      case PackageContentItem:
        or {
          eq 'resource.id', res.id // Direct
          eq 'resource.id', (res as PackageContentItem).pkg.id // Via package
        }
        break
      default :
        eq 'resource.id', res.id
        break
    }
  }
  
  def entitlements (String resourceId) {
    
    // Easiest way to check that this resource is a title is to read it in as one.
    // We use criteria here to ensure
    
    final ErmResource res = resourceId ? ErmResource.read ( resourceId ) : null
    final Class<? extends ErmResource> resClass = res ? Hibernate.getClass( res ) : null
    
    // Not allowed type Just show a 404.
    if (resClass == null || (!(resClass == TitleInstance || Entitlement.ALLOWED_RESOURCES.contains( resClass )))) {
      response.status = 404
      return
    }
    
    // We have a matching resource. Grab all entitlements that lead to this resource.
    final Instant start = Instant.now()
    log.debug("Start query ${start}")
    respond doTheLookup (Entitlement, entitlementCriteria.curry(resClass, res) )
    log.debug("completed in ${Duration.between(start, Instant.now()).toSeconds()} seconds")
  }
  
  def relatedEntitlements (String resourceId) {
    // Grab the supplied id and lookup the resource. We can then determine the type.
    final ErmResource res = resourceId ? ErmResource.read ( resourceId ) : null
    final Class<? extends ErmResource> resClass = res ? Hibernate.getClass( res ) : null
    
    if (resClass == TitleInstance) {
      return respond ([]) 
    }
    
    // Not allowed type Just show a 404.
    if (resClass == null) {
      response.status = 404
      return
    }
    
    // Grab the title to use as the "related" filter.
    TitleInstance ti = null
    switch (resClass) {
      case PackageContentItem:
        ti = (res as PackageContentItem).pti.titleInstance
        break
        
      case PlatformTitleInstance:
        ti = (res as PlatformTitleInstance).titleInstance
        break
        
      default:
        response.status = 404
        return
    }
    
    // We have a matching resource. Grab all entitlements that lead to this resource.
    final Instant start = Instant.now()
    log.debug("Start query ${start}")
    
    // Local reference for detached criteria.
    final Closure entCrit = entitlementCriteria
    
    respond doTheLookup (Entitlement, {
      entCrit.rehydrate(delegate, owner, thisObject)(TitleInstance, ti)
      notIn 'id', new DetachedCriteria(Entitlement, 'excludes').build ({
        switch (resClass) {
          case TitleInstance:
            or {
              'in' 'excludes.resource.id', new DetachedCriteria(PlatformTitleInstance, 'excl_ptis').build {
                readOnly (true)
                
                eq 'titleInstance', res
                  
                projections {
                  property ('id')
                }
              }
              
              // PCIs
              'in' 'excludes.resource.id', new DetachedCriteria(PackageContentItem, 'excl_pcis').build {
                readOnly (true)
                
                pti {
                  eq 'titleInstance.id', res.id
                }
                  
                projections {
                  property ('id')
                }
              }
              
              // Packages.
              'in' 'excludes.resource.id', new DetachedCriteria(PackageContentItem, 'excl_pkg_pcis').build {
                readOnly (true)
                
                pti {
                  eq 'titleInstance.id', res.id
                }
    
                isNull 'removedTimestamp'
    
                projections {
                  property ('pkg.id')
                }
              }
            }
            break
          case PlatformTitleInstance:
            or {
              eq 'excludes.resource', res
              
              // PCIs
              'in' 'excludes.resource.id', new DetachedCriteria(PackageContentItem,'excl_pcis').build {
                readOnly (true)
                
                eq 'pti.id', res.id
                  
                projections {
                  property ('id')
                }
              }
              
              // Packages.
              'in' 'excludes.resource.id', new DetachedCriteria(PackageContentItem, 'excl_pkg_pcis').build {
                readOnly (true)
                
                eq 'pti.id', res.id
    
                isNull 'removedTimestamp'
    
                projections {
                  property ('pkg.id')
                }
              }
            }
            break
            
          case PackageContentItem:
            or {
              eq 'excludes.resource.id', res.id
              eq 'excludes.resource.id', (res as PackageContentItem).pkg.id
            }
            break
          default :
            eq 'excludes.resource.id', res.id
            break
        }
        projections {
          property ('excludes.id')
        }
      })
    })
    log.debug("completed in ${Duration.between(start, Instant.now()).toSeconds()} seconds")
  }
}


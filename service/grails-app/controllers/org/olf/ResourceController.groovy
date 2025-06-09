package org.olf


import org.hibernate.Hibernate
import org.olf.erm.Entitlement
import org.olf.kb.ErmResource
import org.olf.kb.PackageContentItem
import org.olf.kb.Pkg
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.TitleInstance

import com.k_int.okapi.OkapiTenantAwareController
import grails.gorm.DetachedCriteria
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import org.olf.kb.http.request.body.DeleteBody
import org.springframework.http.HttpStatus

import java.time.Duration
import java.time.Instant

import static org.olf.general.Constants.Queries.*

@Slf4j
@CurrentTenant
class ResourceController extends OkapiTenantAwareController<ErmResource> {
  ErmResourceService ermResourceService
  UtilityService utilityService

  ResourceController() {
    // True means read only. This should block post and puts to this.
    super(ErmResource, true)

  }

  DetachedCriteria pciSubQuery = PackageContentItem.where({
    eqProperty('pti.id', 'platformTitleInstance.id')
    setAlias 'packageContentItem'

    projections {
      property 'id'
    }
  })

  DetachedCriteria ptiSubQuery = PlatformTitleInstance.where({
    eqProperty('titleInstance.id', "${DEFAULT_ROOT_ALIAS}.id") //here "this" refers to the root alias of criteria
    setAlias 'platformTitleInstance'

    exists(pciSubQuery)

    projections {
      property 'id'
    }
  })

  // LEFT JOIN query
  def electronic () {
    respond doTheLookup ({
      def res = ErmResource;
      or {
        eq 'class', Pkg
        
        and {
          eq 'class', TitleInstance
          eq 'subType', TitleInstance.lookupOrCreateSubType('electronic')

          exists(ptiSubQuery)
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

  private String buildStaticEntitlementOptionsHQL(Boolean isCount = false) {
    return """
      SELECT ${isCount ? 'COUNT(res.id)' : 'res'} FROM ErmResource as res WHERE
      res.id IN :flattenedIds
    """.toString();
  }

  // I'd like to move this "static fetch" code into a shared space if we get a chance before some kind of OS/ES implementation
  @Transactional(readOnly=true)
  def staticEntitlementOptions (final String resourceId) {
    //final String resourceId = params.get("resourceId")
    final Integer perPage = (params.get("perPage") ?: "10").toInteger();
    
    // Funky things will happen if you pass 0 or negative numbers...
    final Integer page = (params.get("page") ?: "1").toInteger();

    if (resourceId) {
      // Splitting this into two queries to avoid unnecessary joins. Wish we could do this in one but there's
      // seemingly no way to flatten results like this
      List<String> flattenedIds = PackageContentItem.executeQuery("""
        SELECT pci.id, pci.pkg.id FROM PackageContentItem as pci WHERE
          pci.removedTimestamp IS NULL AND
          pci.pti.titleInstance.id = :resourceId
      """.toString(), [resourceId: resourceId]).flatten()

      final List<PackageContentItem> results = PackageContentItem.executeQuery(
        buildStaticEntitlementOptionsHQL(),
        [
          flattenedIds: flattenedIds,
        ],
        [
          max: perPage,
          offset: (page - 1) * perPage
          //readOnly: true -- handled in the transaction, no?
        ]
      );

      if (params.boolean('stats')) {
        final Integer count = PackageContentItem.executeQuery(
          buildStaticEntitlementOptionsHQL(true),
          [
            flattenedIds: flattenedIds,
          ]
        )[0].toInteger();

        final def resultsMap = [
          pageSize: perPage,
          page: page,
          totalPages: ((int)(count / perPage) + (count % perPage == 0 ? 0 : 1)),
          meta: [:], // Idk what this is for
          totalRecords: count,
          total: count,
          results: results
        ];

        // respond with full result set
        respond resultsMap;
      } else {

        // Respond the list of items
        respond results
      }
    }
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

  // For /erm/resources/markForDelete/pcis
  def markPcisForDelete(DeleteBody deleteBody) {
    log.info("ResourceController::markPcisForDelete({})", deleteBody)
    handleDeleteCall(deleteBody) { ids ->
      return ermResourceService.markForDelete(ids, PackageContentItem.class)
    }
  }

  // For /erm/resources/markForDelete/ptis
  def markPtisForDelete(DeleteBody deleteBody) {
    log.info("ResourceController::markPtisForDelete({})", deleteBody)

    handleDeleteCall(deleteBody) { ids ->
      return ermResourceService.markForDelete(ids, PlatformTitleInstance.class);
    }
  }

  // For /erm/resources/markForDelete/tis
  def markTisForDelete(DeleteBody deleteBody) {
    log.info("ResourceController::markTisForDelete({})", deleteBody)

    handleDeleteCall(deleteBody) { ids ->
      return ermResourceService.markForDelete(ids, TitleInstance.class);
    }
  }

  // For /erm/resources/delete/pci
  def deletePcis(DeleteBody deleteBody) {
    log.info("ResourceController::deletePcis({})", deleteBody)

    handleDeleteCall(deleteBody) { ids ->
      return ermResourceService.deleteResources(ids, PackageContentItem.class)
    }
  }

  // For /erm/resources/delete/ptis
  def deletePtis(DeleteBody deleteBody) {
    log.info("ResourceController::deletePtis({})", deleteBody)

    handleDeleteCall(deleteBody) { ids ->
      return ermResourceService.deleteResources(ids, PlatformTitleInstance.class)
    }
  }

  // For /erm/resources/delete/tis
  def deleteTis(DeleteBody deleteBody) {
    log.info("ResourceController::deleteTis({})", deleteBody)

    handleDeleteCall(deleteBody) { ids ->
      return ermResourceService.deleteResources(ids, TitleInstance.class)
    }
  }

  /**
   * Private helper method to handle the common logic for delete actions (markForDelete/delete).
   * @param deleteBody The deleteBody object (must implement Validateable and have a resources property corresponding to ErmResource IDs)
   * @param serviceCall A closure that takes a List<String> of IDs and calls the appropriate service method.
   */
  private void handleDeleteCall(DeleteBody deleteBody, Closure serviceCall) {
    if (deleteBody == null) {
      log.warn("Received null delete body for handleDeleteCall({})", deleteBody)
      respond([message: "Nothing in delete request body.", statusCode: HttpStatus.BAD_REQUEST.value()], status: HttpStatus.BAD_REQUEST.value())
      return
    }

    if (!utilityService.checkValidBinding(deleteBody)) {
      // NOTE!! We can only assume this because there is only one validation rule on DeleteBody
      respond([message: "DeleteBody.resources must be non-null and not empty", statusCode: HttpStatus.BAD_REQUEST.value()], status: HttpStatus.BAD_REQUEST.value())
      return
    }

    try {
      respond serviceCall.call(deleteBody.resources)
    } catch (Exception e) {
      log.error("Error during delete service call for IDs {}: {}", deleteBody.resources, e.message, e)
      respond ([message: "Error during delete call", statusCode: HttpStatus.INTERNAL_SERVER_ERROR.value()], status: HttpStatus.INTERNAL_SERVER_ERROR.value())
    }
  }
}


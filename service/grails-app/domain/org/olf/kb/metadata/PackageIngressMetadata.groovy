package org.olf.kb.metadata

import org.olf.kb.ErmResource
import java.time.Instant;

import grails.gorm.MultiTenant

/*
 * A domain class to store the metadata about how the current package
 * data came to be in the system. This includes ingressType (enum)
 */
public class PackageIngressMetadata implements MultiTenant<PackageIngressMetadata> {
  String id
  ErmResource resource

  ResourceIngressType ingressType

  /* Will hold:
   *  Package level PushTaskId for Pushkb
   *  RemoteKB.id for harvest,
   *  LOCAL RemoteKB.id for json/kbart import
   */
  String ingressId

  /* Will hold:
   *  PushKB url for Pushkb (should come in along with Pkg data)
   *  null for harvest,
   *  null for json/kbart import
   */
  String ingressUrl

  /* Will hold:
   *  TIPP level PushTaskId for Pushkb
   *  null for harvest,
   *  null for json/kbart import
   */
  String contentIngressId

  /* Will hold:
   *  PushKB url for Pushkb (should come in along with TIPP data)
   *  null for harvest,
   *  null for json/kbart import
   */
  String contentIngressUrl

  Instant dateCreated
  Instant lastUpdated

  static mapping = {
                   id column: 'pim_id', generator: 'uuid2', length:36
              version column: 'pim_version'
             resource column: 'pim_resource_fk'
          ingressType column: 'pim_ingress_type'
            ingressId column: 'pim_ingress_id'
           ingressUrl column: 'pim_ingress_url'
     contentIngressId column: 'pim_content_ingress_id'
    contentIngressUrl column: 'pim_content_ingress_url'
          dateCreated column: 'pim_date_created'
          lastUpdated column: 'pim_last_updated'
  }

  static constraints = {
             resource (nullable:false, blank:false)
          ingressType (nullable:true, blank:false)
            ingressId (nullable:true, blank:false)
           ingressUrl (nullable:true, blank:false)
     contentIngressId (nullable:true, blank:false)
    contentIngressUrl (nullable:true, blank:false)
  }
}

package org.olf

import groovy.transform.CompileStatic
import org.olf.kb.metadata.ResourceIngressType
import org.olf.kb.metadata.PackageIngressMetadata

import org.olf.kb.Pkg

import grails.gorm.multitenancy.CurrentTenant
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
@CompileStatic
class IngressMetadataService implements DataBinder {

  PackageIngressMetadata getPIMFromPackageId(String pkgId) {
    //log.debug("IngressMetdataService::getPIMFromPackageId(${pkgId})")

    List<PackageIngressMetadata> pimList = PackageIngressMetadata.executeQuery("""
      SELECT pim FROM PackageIngressMetadata pim WHERE pim.resource.id = :pkgId
    """, [pkgId: pkgId]);

    if (pimList.size() == 0) {
      return null;
    } else if (pimList.size() > 1) {
      throw new RuntimeException("There should only be a single ingressMetadata for package(${pkgId}), found: ${pimList.size()}")
    }

    return pimList[0];
  }

  PackageIngressMetadata createPackageIngressMetadata(String pkgId, Map ingressMetadata = [:]) {
    //log.debug("IngressMetdataService::createPackageIngressMetadata(${pkgId}, ${ingressMetadata})")

    Pkg pkg = Pkg.get(pkgId);
    ingressMetadata.resource = pkg;
    PackageIngressMetadata pim = new PackageIngressMetadata(ingressMetadata).save(failOnError: true);

    return pim;
  }

  PackageIngressMetadata upsertPackageIngressMetadata (final String pkgId, final Map ingressMetadata = [:]) {
    //log.debug("IngressMetdataService::upsertPackageIngressMetadata(${pkgId}, ${ingressMetadata})")
    PackageIngressMetadata pim = getPIMFromPackageId(pkgId);

    if (pim != null) {
      // We have a pre-existing PIM -- decide whether to update

      // NOTE! This means that package import for a gokb package will OVERWRITE ingressMetadata from Pushkb/Harvest
      if (pim.ingressType != ingressMetadata.ingressType) {
        // We have a brand new set of metadata, delete the old one and create new
        pim.delete();
        pim = createPackageIngressMetadata(pkgId, ingressMetadata);
      }  else {
        pim = updatePackageIngressMetadata(pim, ingressMetadata)
      }

    } else {
      // Have not yet got a PIM for this pkg, create one
      pim = createPackageIngressMetadata(pkgId, ingressMetadata);
    }

    return pim;
  }

  boolean pimFieldUpdate(
      PackageIngressMetadata pim,
      Map ingressMetadata,
      String fieldName
  ) {
    if (
        ingressMetadata[fieldName] != null && // This allows us to ONLY update certain fields at a time.
        pim[fieldName] != ingressMetadata[fieldName]
    ) {
      pim[fieldName] = ingressMetadata[fieldName]
      return true
    }
    return false;
  }

  PackageIngressMetadata updatePackageIngressMetadata(PackageIngressMetadata pim, final Map ingressMetadata = [:]) {
    //log.debug("IngressMetdataService::updatePackageIngressMetadata(${pim}, ${ingressMetadata})")

    // This is where it gets a little more complicated...
    // We want to UPDATE anything coming in but be careful
    int updateCount = 0;
    if (pimFieldUpdate(pim, ingressMetadata, 'ingressUrl')) updateCount++
    if (pimFieldUpdate(pim, ingressMetadata, 'ingressId')) updateCount++

    // Only update PIM content fields if we're in the PushKB case
    if (pim.ingressType == ResourceIngressType.PUSHKB) {
      // Special case for the contentMetadata
      if (pimFieldUpdate(pim, ingressMetadata, 'contentIngressId')) updateCount++
      if (pimFieldUpdate(pim, ingressMetadata, 'contentIngressUrl')) updateCount++
    }

    // Only actually save if we made a change
    if (updateCount > 0) {
      pim.save(failOnError: true);
    }

    return pim;
  }

}

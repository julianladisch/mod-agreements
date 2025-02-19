package org.olf

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW

import java.util.concurrent.TimeUnit

import org.olf.kb.metadata.ResourceIngressType

import org.olf.general.Org

import org.olf.general.StringUtils
import org.olf.general.IngestException

import org.olf.dataimport.internal.PackageSchema
import org.olf.dataimport.internal.PackageSchema.ContentItemSchema
import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.kb.Embargo
import org.olf.kb.PackageContentItem
import org.olf.kb.AlternateResourceName
import org.olf.kb.ContentType
import org.olf.kb.AvailabilityConstraint
import org.olf.kb.PackageDescriptionUrl
import org.olf.kb.Pkg
import org.olf.kb.Platform
import org.olf.kb.PlatformTitleInstance
import org.olf.kb.RemoteKB
import org.olf.kb.TitleInstance
import org.slf4j.MDC

import com.k_int.web.toolkit.utils.GormUtils

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import grails.web.databinding.DataBinder
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@CurrentTenant
class PackageIngestService implements DataBinder {

  // This boolean controls the behaviour of the loader when we encounter a title that does not have
  // a platform URL. We can error the row and do nothing, or create a row and point it at a proxy
  // platform to flag the error. Currently trialling the latter case. set to false to error and ignore the
  // row.
  private boolean PROXY_MISSING_PLATFORM = true

  TitleIngestService titleIngestService
  IdentifierService identifierService
  CoverageService coverageService
  IngressMetadataService ingressMetadataService

  // dependentModuleProxyService is a service which hides the fact that we might be dependent upon other
  // services for our reference data. In this class - vendors are erm Org entries, but in folio these are
  // managed by the vendors app. If we are running in folio mode, this service hides the detail of
  // looking up an Org in vendors and stashing the vendor info in the local cache table.
  DependentModuleProxyService dependentModuleProxyService

  public Map upsertPackage(PackageSchema package_data, Map ingressMetadata = [ingressType: ResourceIngressType.JSON_IMPORT]) {
    return upsertPackage(package_data,'LOCAL',true, ingressMetadata)
  }


  // For now this is repeated in pushKBService
  private static final def countChanges = ['accessStart', 'accessEnd']
	
	@Transactional(propagation= REQUIRES_NEW)
	private RemoteKB findOrCreateKbByName( String kbName, boolean kbCreateReadOnly) {
		RemoteKB kb = RemoteKB.findByName(kbName) ?: new RemoteKB(
			name:kbName,
		  rectype: RemoteKB.RECTYPE_PACKAGE,
		  active: Boolean.TRUE,
		  readonly:kbCreateReadOnly,
		  trustedSourceTI:false).save(flush:true, failOnError:true)
		
		kb
	}


  // TODO This is a bit of a mess now. PushKB does NOT use upsertPackage in the same way,
  // instead kind of mirroring the process while skipping all the RemoteKB AND contents logic.
  // This will be used, however, for all RemoteKB upserts
  // We should try to identify more of the work
  /**
   * Load the paackage data (Given in the agreed canonical json package format) into the KB.
   * This function must be passed VALID package data. At this point, all package contents are
   * assumed to be valid. Any invalid rows should be filtered out at this point.
   * This is to keep the implementation of this function clean, all error checking shoud be
   * performed prior to this step. This function is soley concerned with absorbing a valid
   * package into the KB.
   * @return id of package upserted
   */
  public Map upsertPackage(
      PackageSchema package_data,
      String remotekbname,
      boolean kbCreateReadOnly=false,
      Map ingressMetadata = [:] // Allow JSON/KBART to pre-declare that they're coming from Json or Kbart, otherwise empty
  ) {
		// Really messy but required as withNew session does not work unless there is already a session
  	// bound.

		final def result = [
			startTime: System.currentTimeMillis(),
			titleCount: 0,
			newTitles: 0,
			removedTitles: 0,
			updatedTitles: 0,
			updatedAccessStart: 0,
			updatedAccessEnd: 0,
		]
				
		Pkg.withSession { currentSess ->
			Pkg.withTransaction {
				Pkg.withNewSession { newSess ->
					Pkg.withTransaction {
						Pkg pkg = null;
						// Remove MDC title at top of upsert package
						MDC.remove('title')
            MDC.remove('packageSource')
						MDC.remove('packageReference')
				
						// ERM caches many remote KB sources in it's local package inventory
						// Look up which remote kb via the name
						final RemoteKB kb = findOrCreateKbByName(remotekbname, kbCreateReadOnly)
						
						final boolean trustedSourceTI = package_data.header?.trustedSourceTI ?: kb.trustedSourceTI
						Pkg.withNewTransaction { status ->
				
							result.updateTime = System.currentTimeMillis()
				
							log.info("Package header: ${package_data.header} - update start time is ${result.updateTime}")
							
							// Farm out package lookup and creation to a separate method

              if (ingressMetadata.ingressType == null) {
                ingressMetadata.ingressType = ResourceIngressType.HARVEST
              }
              ingressMetadata.ingressId = kb.id;

							pkg = lookupOrCreatePkg(package_data, ingressMetadata);
							// Retain logging information
							MDC.put('packageSource', pkg.source.toString())
							MDC.put('packageReference', pkg.reference.toString())
				
							// Update identifiers from citation
							identifierService.updatePackageIdentifiers(pkg, package_data.identifiers)
				
							result.packageId = pkg.id
						}

            // We are treating `null` as `true`, but NOT overwriting it moving forward
            // This is the analogue to the same check happening in PushKBService::pushPCIs
            if (pkg.syncContentsFromSource != false) {
              package_data.packageContents.eachWithIndex { ContentItemSchema pc, int index ->
                // ENSURE MDC title is set as early as possible
                MDC.put('title', StringUtils.truncate(pc.title.toString()))

                try {
                  PackageContentItem.withNewSession { tsess ->
                    PackageContentItem.withNewTransaction { status ->
                      // Delegate out to TitleIngestService so that any shared steps can move there.
                      Map titleIngestResult = titleIngestService.upsertTitle(pc, kb, trustedSourceTI)
                      // titleIngestResult.titleInstanceId will be non-null IFF TitleIngestService managed to find a title with that Id.
                      if (titleIngestResult.titleInstanceId != null) {
                        // Pass off to new hierarchy method (?)
                        Map hierarchyResult = lookupOrCreateTitleHierarchy(
                            titleIngestResult.titleInstanceId,
                            pkg.id,
                            trustedSourceTI,
                            pc,
                            result.updateTime,
                            result.titleCount
                        )

                        PackageContentItem pci = PackageContentItem.get(hierarchyResult.pciId)
                        hierarchyResultMapLogic(hierarchyResult, result, pci)
                      } else {
                        // Almost the same message exists in TitleIngestService if result is null
                        //String message = "Skipping \"${pc.title}\". Unable to resolve title from ${pc.title} with identifiers ${pc.instanceIdentifiers}"
                        //log.error(message)
                      }
                    }
                    // Without this, created objects live on in the hibernate cache and will balloon memory badly
                    tsess.clear();
                  }
                } catch (IngestException ie) {
                  // When we've caught an ingest exception, should have helpful error log message
                  String message = "Skipping \"${pc.title}\": ${ie.message}"
                  log.error(message, ie)
                } catch (Exception e) {
                  String message = "Skipping \"${pc.title}\". System error: ${e.message}"
                  log.error(message, e)
                }
                result.titleCount++

                // Do we really need a running average?
                /* if ( result.titleCount % 100 == 0 ) {
                  result.averageTimePerTitle=(System.currentTimeMillis()-result.startTime)/(result.titleCount * 1000)
                  log.debug ("(Package in progress) processed ${result.titleCount} titles, average per title: ${result.averageTimePerTitle}s")
                } */
              }

              // This removed logic is WRONG under pushKB because it's chunked -- ensure pushKB does not call full upsertPackage method
              // At the end - Any PCIs that are currently live (Don't have a removedTimestamp) but whos lastSeenTimestamp is < result.updateTime
              // were not found on this run, and have been removed. We *may* introduce some extra checks here - like 3 times or a time delay, but for now,
              // this is how we detect deletions in the package file.
              log.debug("Remove any content items that have disappeared since the last upload. ${pkg.name}/${pkg.source}/${pkg.reference}/${result.updateTime}")
              int removal_counter = 0

              PackageContentItem.withNewTransaction { status ->
                // FIXME we're querying on pkg itself here not pkg.id
                PackageContentItem.executeQuery('select pci from PackageContentItem as pci where pci.pkg = :pkg and pci.lastSeenTimestamp < :updateTime and pci.removedTimestamp is null',
                    [pkg: pkg, updateTime: result.updateTime]).each { removal_candidate ->
                  try {
                    log.debug("Removal candidate: pci.id #${removal_candidate.id} (Last seen ${removal_candidate.lastSeenTimestamp}, thisUpdate ${result.updateTime}) -- Set removed")
                    removal_candidate.removedTimestamp = result.updateTime
                    removal_candidate.save(flush: true, failOnError: true)
                  } catch (Exception e) {
                    log.error("Problem removing ${removal_candidate} in package load", e)
                  }
                  result.removedTitles++
                }
              }
            } else {
              log.info("Package: ${pkg.name}(${pkg.id}) has syncContentsFromSource: false, skipping title ingest")
            }
					}
          newSess.clear();
				}
			}
		}

    // Not sure if MDC logic can go in shared method
		//    MDC.clear()
		MDC.remove('recordNumber')
		MDC.remove('title')
		long finishedTime = (System.currentTimeMillis()-result.startTime) // Don't divide by 1000 here, instead leave in ms for greater accuracy
    logPackageResults(result, finishedTime);

		return result
  }

  // Pass in finished time so we're not waiting for package content cleanup
  public void logPackageResults(Map result, long finishedTime) {
    // Need to pause long enough so that the timestamps are different
    TimeUnit.MILLISECONDS.sleep(1)
    if (result.titleCount > 0) {
      log.debug ("Processed ${result.titleCount} titles in ${finishedTime/1000} seconds (${(finishedTime/result.titleCount)/1000}s average)")
      log.info(
"""\
Package titles summary::Processed/${result.titleCount}, \
Added/${result.newTitles}, \
Updated/${result.updatedTitles}, \
Removed/${result.removedTitles}, \
Not synced/${result.nonSyncedTitles}, \
AccessStart/${result.updatedAccessStart}, \
AccessEnd/${result.updatedAccessEnd}\
"""
      )
      // Log the counts too.
      for (final String change : countChanges) {
        if (result[change]) {
          TimeUnit.MILLISECONDS.sleep(1)
          log.info ("Changed ${GrailsNameUtils.getNaturalName(change).toLowerCase()} on ${result[change]} titles")
        }
      }
    } else {
      if (result.titleCount > 0) {
        log.info ("No titles to process")
      }
    }
  }

  /*
   * We split out the lookup vs the update vs the create code for packages,
   * as there is potentially differences in behaviour between pushKB and harvest.
   */

  // This WILL NOT update or create a package from the data.
  public Pkg lookupPkg(PackageSchema package_data) {
    Pkg pkg = null

    // header.packageSlug contains the package maintainers authoritative identifier for this package.
    pkg = Pkg.findBySourceAndReference(package_data.header.packageSource, package_data.header.packageSlug)
    if (pkg == null) {
      // at this point we check alternate slugs
      for ( alternateSlug in package_data.header.alternateSlugs ) {
        pkg = Pkg.findBySourceAndReference(package_data.header.packageSource, alternateSlug);
        if ( pkg != null ) {
          pkg.reference = package_data.header.packageSlug;
          break;
        }
      }
    }

    return pkg;
  }

  public Org getVendorFromPackageData(PackageSchema package_data) {
    Org vendor = null;

    if (
      package_data.header?.packageProvider?.name != null &&
      package_data.header?.packageProvider?.name.trim().length() > 0
    ) {
      log.debug("Package contains provider information: ${package_data.header?.packageProvider?.name} -- trying to match to an existing organisation.")
      vendor = dependentModuleProxyService.coordinateOrg(package_data.header?.packageProvider?.name)
      // reference has been removed at the request of the UI team
      // vendor.enrich(['reference':package_data.header?.packageProvider?.reference])
    }
    else {
      log.warn('Package ingest - no provider information present')
    }

    return vendor;
  }

  public Pkg lookupPkgAndUpdate(PackageSchema package_data, final Map ingressMetadata = [:]) {
    Pkg pkg = lookupPkg(package_data)
    Org vendor = getVendorFromPackageData(package_data)
    // Do update step but NOT create step
    if (pkg != null) {
      pkg.sourceDataUpdated = package_data.header.sourceDataUpdated

      if (package_data.header.lifecycleStatus) {
        pkg.lifecycleStatusFromString = package_data.header.lifecycleStatus
      }

      if (package_data.header.availabilityScope) {
        pkg.availabilityScopeFromString = package_data.header.availabilityScope
      }

      // DO NOT UPDATE syncContentsFromSource.

      pkg.vendor = vendor
      pkg.description = package_data.header.description
      pkg.name = package_data.header.packageName
      pkg.save(failOnError:true)

      // Call separate methods for updating collections for code cleanliness
      // These methods are responsible for their own saves
      updateContentTypes(pkg.id, package_data)
      updateAlternateNames(pkg.id, package_data)
      updateAvailabilityConstraints(pkg.id, package_data)
      updatePackageDescriptionUrls(pkg.id, package_data)

      // Handle "update" of ingressMetadata
      ingressMetadataService.upsertPackageIngressMetadata(pkg.id, ingressMetadata)
    }

    return pkg;
  }


  /*
   * Separate out the create or lookup pkg code, so that it can
   * be used both by the ingest service (via upsert pkg), as well
   * as the pushKBService (directly)
   *
   * This method ALSO updates information for packages.
   */
  public Pkg lookupOrCreatePkg(PackageSchema package_data, final Map ingressMetadata = [:]) {
    // This takes care of any updates
    Pkg pkg = lookupPkgAndUpdate(package_data, ingressMetadata);

    // If pkg is null, then we can safely create a new one
    if ( pkg == null ) {
      Org vendor = getVendorFromPackageData(package_data)
      pkg = new Pkg(
                          name: package_data.header.packageName,
                        source: package_data.header.packageSource,
                     reference: package_data.header.packageSlug,
                   description: package_data.header.description,
             sourceDataCreated: package_data.header.sourceDataCreated,
             sourceDataUpdated: package_data.header.sourceDataUpdated,
              sourceTitleCount: package_data.header.sourceTitleCount,
        syncContentsFromSource: package_data.header.syncContentsFromSource,
             availabilityScope: ( package_data.header.availabilityScope != null ? Pkg.lookupOrCreateAvailabilityScope(package_data.header.availabilityScope) : null ),
               lifecycleStatus: Pkg.lookupOrCreateLifecycleStatus(package_data.header.lifecycleStatus != null ? package_data.header.lifecycleStatus : 'Unknown'),
                        vendor: vendor,
      ).save(flush:true, failOnError:true)

      // Set up ingressMetadata for new packages
      ingressMetadataService.createPackageIngressMetadata(pkg.id, ingressMetadata)

      (package_data?.header?.contentTypes ?: []).each {
        pkg.addToContentTypes(new ContentType([contentType: ContentType.lookupOrCreateContentType(it.contentType)]))
      }

      (package_data?.header?.alternateResourceNames ?: []).each {
        pkg.addToAlternateResourceNames(new AlternateResourceName([name: it.name]))
      }

      (package_data?.header?.availabilityConstraints ?: []).each {
        pkg.addToAvailabilityConstraints(new AvailabilityConstraint([body: AvailabilityConstraint.lookupOrCreateBody(it.body)]))
      }

      (package_data?.header?.packageDescriptionUrls ?: []).each {
        pkg.addToPackageDescriptionUrls(new PackageDescriptionUrl([url: it.url]))
      }

      pkg.save(failOnError: true)
    }

    return pkg;
  }

  def updateContentTypes (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);
    def contentTypes = package_data?.header?.contentTypes ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def contentTypesToRemove = [];

    pkg.contentTypes.each {
      if (!contentTypes.contains(it.contentType.label)) {
        contentTypesToRemove << it
      }
    }

    contentTypesToRemove.each {
      pkg.removeFromContentTypes(it)
    }

    contentTypes.each {def ct ->
      if(!pkg.contentTypes?.collect {def pct -> pct.contentType.label }.contains(ct.contentType)) {
        pkg.addToContentTypes(new ContentType([contentType: ContentType.lookupOrCreateContentType(ct.contentType)]))
      }
    }

    pkg.save(failOnError: true)
  }

  /*  ---- Individual update package methods ---- */
  def updateAlternateNames (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);

    def resourceNames = package_data?.header?.alternateResourceNames ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def alternateNamesToRemove = [];

    pkg.alternateResourceNames.each {
      if (!resourceNames.contains(it.name)) {
        def arn_tbd = AlternateResourceName.findByName(it.name)
        alternateNamesToRemove << arn_tbd
      }
    }

    alternateNamesToRemove.each {
      pkg.removeFromAlternateResourceNames(it)
    }

    resourceNames.each {def arn ->
      if(!pkg.alternateResourceNames?.collect {def parn -> parn.name }.contains(arn.name)) {
        pkg.addToAlternateResourceNames(new AlternateResourceName([name: arn.name]))
      }
    }

    pkg.save(failOnError: true)
  }

  def updateAvailabilityConstraints (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);
    def availabilityConstraints = package_data?.header?.availabilityConstraints ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def availabilityConstraintsToRemove = [];

    pkg.availabilityConstraints.each {
      if (!availabilityConstraints.contains(it.body.label)) {
        availabilityConstraintsToRemove << it
      }
    }

    availabilityConstraintsToRemove.each {
      pkg.removeFromAvailabilityConstraints(it)
    }

    availabilityConstraints.each {def ac ->
      if(!pkg.availabilityConstraints?.collect {def pac -> pac.body.label }.contains(ac.body)) {
        pkg.addToAvailabilityConstraints(new AvailabilityConstraint([body: AvailabilityConstraint.lookupOrCreateBody(ac.body)]))
      }
    }

    pkg.save(failOnError: true)
  }

  def updatePackageDescriptionUrls (String pkgId, PackageSchema package_data) {
    Pkg pkg = Pkg.get(pkgId);
    def urls = package_data?.header?.packageDescriptionUrls ?: []
    // To avoid changing the object we're iterating over,
    // we first iterate over the object to grab the items to remove,
    // then iterate over _that_ list to remove them
    def urlsToRemove = [];

    pkg.packageDescriptionUrls.each {
      if (!urls.contains(it.url)) {
        urlsToRemove << it
      }
    }

    urlsToRemove.each {
      pkg.removeFromPackageDescriptionUrls(it)
    }

    urls.each {def url ->
      if(!pkg.packageDescriptionUrls?.collect {def pdu -> pdu.url }.contains(url.url)) {
        pkg.addToPackageDescriptionUrls(new PackageDescriptionUrl([url: url.url]))
      }
    }

    pkg.save(failOnError: true)
  }

  /*
   * Lookup or create a package from contentItemPackage
   * within the passed contentItem. This is used for PushKB on the
   * off chance that a PCI is attempting to be ingested when the package
   * for that title has not been yet.
   */
  public Pkg lookupOrCreatePackageFromTitle(ContentItemSchema pc, Map ingressMetadata = [:]) {
    Pkg pkg = null;
    if (pc?.contentItemPackage) {
      pkg = lookupOrCreatePkg(pc.contentItemPackage, ingressMetadata)
    } else {
      /* WIP this feels like not the right thing to do */
      throw new Exception("Cannot create package from title if no contentItemPackage is provided.")
    }

    return pkg
  }

  public Platform lookupOrCreatePlatform(ContentItemSchema pc) {
    // lets try and work out the platform for the item
    def platform_url_to_use = pc.platformUrl

    if ( ( pc.platformUrl == null ) && ( pc.url != null ) ) {
      // No platform URL, but a URL for the title. Parse the URL and generate a platform URL
      def parsed_url = new java.net.URL(pc.url)
      platform_url_to_use = "${parsed_url.getProtocol()}://${parsed_url.getHost()}"
    }

    Platform platform = Platform.resolve(platform_url_to_use, pc.platformName)

    if ( platform == null && PROXY_MISSING_PLATFORM ) {
      platform = Platform.resolve('http://localhost.localdomain', 'This platform entry is used for error cases')
    }

    platform
  }

  public PlatformTitleInstance lookupOrCreatePTI(TitleInstance title, Platform platform, Boolean trustedSourceTI, ContentItemSchema pc) {
    PlatformTitleInstance pti = PlatformTitleInstance.findByTitleInstanceAndPlatform(title, platform)
    if ( pti == null ) {
      pti = new PlatformTitleInstance(titleInstance:title,
        platform:platform,
        url:pc.url,
      );

      // Ensure coverages don't change here... I _think_ this is only used during ingest where PCI coverage sweep will get triggered eventually
      pti.doNotCalculateCoverage = true
      pti.save(failOnError: true)
    } else if (trustedSourceTI) {
      // Update any PTI fields directly
      if (pti.url != pc.url) {
        pti.url = pc.url
      }

      // Ensure coverages don't change here... I _think_ this is only used during ingest where PCI coverage sweep will get triggered eventually
      pti.doNotCalculateCoverage = true

      pti.save(flush: true, failOnError: true)
    }

    pti
  }


  // Once package/title has been created, setup rest of hierarchy PTI/PCI etc PCI etc
  // Return special map containing data on whether PCI was created or updated,
  public Map lookupOrCreateTitleHierarchy(
    String titleId,
    String pkgId,
    Boolean trustedSourceTI,
    ContentItemSchema pc,
    long updateTime,
    long titleCount
  ) {
    TitleInstance title = TitleInstance.get(titleId);
    Pkg pkg = Pkg.get(pkgId);

    Map result = [
      pciStatus: 'none' // This should be 'none', 'updated' or 'new'
    ]

    // lets try and work out the platform for the item
    Platform platform = lookupOrCreatePlatform(pc);

    if ( platform != null ) {

      // See if we already have a title platform record for the presence of this title on this platform
      PlatformTitleInstance pti = lookupOrCreatePTI(title, platform, trustedSourceTI, pc)

      // ADD PTI AND PCI ID TO RESULT
      result.ptiId = pti.id;

      // Lookup or create a package content item record for this title on this platform in this package
      // We only check for currently live pci records, as titles can come and go from the package.
      // N.B. addedTimestamp removedTimestamp lastSeenTimestamp
      def pci_qr = PackageContentItem.executeQuery('select pci from PackageContentItem as pci where pci.pti = :pti and pci.pkg.id = :pkg and pci.removedTimestamp is null',
          [pti:pti, pkg:pkg.id])
      PackageContentItem pci = pci_qr.size() == 1 ? pci_qr.get(0) : null;

      boolean isUpdate = false
      boolean isNew = false
      if ( pci == null ) {
        log.debug("Record ${titleCount} - Create new package content item")
        MDC.put('recordNumber', (titleCount+1).toString())
        pci = new PackageContentItem(
          pti:pti,
          pkg:pkg,
          addedTimestamp:updateTime,
        )

        isNew = true
      }
      else {
        // Note that we have seen the package content item now - so we don't delete it at the end.
        log.debug("Record ${titleCount} - Update package content item (${pci.id})")
        isUpdate = true
      }

      String embStr = pc.embargo?.trim()

      // Pre attempt to parse. And log error.
      Embargo emb = null
      if (embStr) {
        emb = Embargo.parse(embStr)
        if (!emb) {
          log.error "Could not parse ${embStr} as Embargo"
        }
      }

      // Add/Update common properties.
      pci.with {
        note = StringUtils.truncate(pc.coverageNote)
        depth = pc.coverageDepth
        accessStart = pc.accessStart
        accessEnd = pc.accessEnd
        addedTimestamp = updateTime
        lastSeenTimestamp = updateTime

        if (emb) {
          if (embargo) {
            // Edit
            bindData(embargo, emb.properties, [exclude: ['id']])
          } else {
            // New
            emb.save()
            embargo = emb
          }
        }
      }

      // ensure that accessStart is earlier than accessEnd, otherwise stop processing the current item
      if (pci.accessStart != null && pci.accessEnd != null) {
        if (pci.accessStart > pci.accessEnd ) {
          throw new IngestException("accessStart date cannot be after accessEnd date for title: ${title} in package: ${pkg.name}");
        }
      }

      // Return a status containing information about whether the PCI was created or updated
      if (isUpdate && pci.isDirty()) {
        result.pciStatus = 'updated'
      } else if (isNew) {
        result.pciStatus = 'new'
      }

      // ADD PTI AND PCI ID TO RESULT
      result.pciId = pci.id;

      // If the row has a coverage statement, check that the range of coverage we know about for this title on this platform
      // extends to include the supplied information. It is a contract with the KB that we assume this is correct info.
      // We store this generally for the title on the platform, and specifically for this title in this package on this platform.
      if ( pc.coverage ) {

        // We define coverage to be a list in the exchange format, but sometimes it comes just as a JSON map. Convert that
        // to the list of maps that coverageService.extend expects
        Iterable<CoverageStatementSchema> cov = pc.coverage instanceof Iterable ? pc.coverage : [ pc.coverage ]
        // Ensure this doesn't trigger the calculateCoverage, that will happen on PCI save
        coverageService.setCoverageFromSchema (pci, cov, false)
      }

      pci.save(failOnError: true, flush: true)
    }
    else {
      throw new IngestException("Unable to identify platform from ${platform_url_to_use} and ${pc.platformName}");
    }

    result
  }


  /* FIXME this is here only to remove some repetition of code
   * between pushKB and harvest. Once we tweak how logging works
   * wholesale we should look into removing this method.
   *
   * Equivalent to moving this code within hierarchy method
   * but kept separate for easier removal down the line.
   */
  void hierarchyResultMapLogic(Map hierarchyResult, Map result, PackageContentItem pci) {
    // This method changes some fields on the passed result map -- not ideal
    switch (hierarchyResult.pciStatus) {
      case 'updated':
        // This means we have changes to an existing PCI and not a new one.
        result.updatedTitles++

        // Grab the dirty properties
        def modifiedFieldNames = pci.getDirtyPropertyNames()
        for (fieldName in modifiedFieldNames) {
          if (fieldName == "accessStart") {
            result.updatedAccessStart++
          }
          if (fieldName == "accessEnd") {
            result.updatedAccessEnd++
          }
          if (countChanges.contains(fieldName)) {
            def currentValue = pci."$fieldName"
            def originalValue = pci.getPersistentValue(fieldName)
            if (currentValue != originalValue) {
              result["${fieldName}"] = (result["${fieldName}"] ?: 0)++
            }
          }
        }
        break;
      case 'new':
        // New item.
        result.newTitles++
        break;
      case 'none':
      default:
        break;
    }
  }
}

## 7.3.0 IN PROGRESS

## 7.2.1 2025-03-27
  * ERM-3662 Patch mod-agreements/licenses/serials/service-interaction/oa for instability during update
  * Updated Env vars table to include S3_GLOBAL_SECRET_KEY

## 7.2.0 2025-03-13
  * ERM-3629 mod-agreements OOM killed during GOKb harvest
  * ERM-3581 Support changing the sync status of a single package in the local KB
  * ERM-3580 Support changing the sync status of multiple packages in the local KB
  * ERM-3579 Support re-starting synchronisation of pushKB ingested packages
  * ERM-3578 Add ingest metadata to ERM Resources
  * ERM-3567 Implement boolean “synchronise” property for packages: mod-agreements
  * ERM-3470 S3 environment variable settings
    * Added new environment variable GLOBAL_S3_SECRET_KEY
    * Deprecated environment variable AWS_SECRET_ACCESS_KEY

## 7.1.5 2025-01-24
  * ERM-3570 Add missing required interfaces to Module Descriptor

## 7.1.4 2025-01-07
  * ERM-3464 /erm/entitilements API returns invalid JSON data

## 7.1.3 2024-12-06
  * ERM-3436 When exporting a KBART file, the column date_last_issue_online should not contain a NULL value in case there is no end year
  * ERM-3432 Add documentation to mod-agreements README regarding upgrade / federated lock advice

## 7.1.2 2024-11-28
  * ERM-3455 pushPkg endpoint in mod-agreements works unexpectedly slowly

## 7.1.1 2024-11-27
  * ERM-3425: Missing interface dependencies in module descriptor in mod-agreements

## 7.1.0 2024-10-30
  * ERM-3394 Ensure initial pushkb support in Ramsons
  * ERM-3284 GOKb ingest process should handle multiple coverage statements on a single TIPP/PCI
  * ERM-3368 Update module license, guidance and dependencies for mod-agreements
  * ERM-3331 Add documents to agreement lines
  * ERM-3330 Open ended coverage statements not ingested from GOKb
  * ERM-3299 Minimise supplementary term data populated as reference data
  * ERM-3281 Design and implement process to resolve eissn, pissn, eisbn and pisbn namespaces
  * ERM-3257 Write integration tests for title instance resolving use cases
  * ERM-3225 Upgrade commons-fileupload from 1.4 to 1.5 fixing FileUpload DoS CVE-2023-24998
  * Performance upgrades around session management for harvest
  * Additional logging information for harvest
  * Sample data now has Gokb RemoteKB OFF by default
  * Fix for connection closed error when changing schema
  * Automated integration test runners
  * Fix and automatically validate module descriptor (Eureka)
  * Tweaked how code reads from grailsApplication (getProperty(\*) instead of config.\*)
  * Separate out interface and version number

## 7.0.9 2024-10-18
  * Fixed problem with concurrentJobsGlobal not actually reading from environment variable

## 7.0.8 2024-09-09
  * ERM-3321 Description can be too long for index, causing mod-agreements error when updating from 6.0.x to later versions or failure on saving agreements with a long description
    * Swap basic GIN indices for GIN indices with trigram operator where they're not already set.

## 7.0.7 2024-08-23
  * ERM-3310 Expose concurrent jobs as env var
  * ERM-3308 Ingest process slows down dramatically on certain packages
  * Tweaked processing log message for more accurate harvest performance numbers

## 7.0.6 2024-07-05
  * ERM-3291 Fix permissions on /erm/validate/remoteKB in mod-agreements
  * ERM-3289 Fix permission on /erm/validate/subscriptionAgreement in mod-agreements
  * ERM-3288 Fix permission on /erm/files/{id}/raw in mod-agreements
  * ERM-3274 If a citation gets passed to BaseTIRS without an instancePublicationMedia field it will crash
  * ERM-3254 With WorkSourceIDTIRS for GOKb we see "Field error in object" errors 

## 7.0.5 2024-05-28
  * ERM-3246 Improve performance of entitlementOptions endpoint
  * ERM-3187 Re-write query to show "list of resources in an agreement" for improved performance
  * Titles previously _always_ enriched, sometimes multiple times, on ingest. Fixed this behaviour where no changes occur

## 7.0.4 2024-05-03
  * ERM-3166 On encountering a GOKb title with the same ISSN assigned as both print and electronic ISSN, ingest stops
  * Refactored some TIRS methods to use Strings instead of Domain Objects for performance/transaction stability

## 7.0.3 2024-04-26
  * ERM-3190 DB Connections are not being released
    * Refactor clearJobAllocation in JobRunnerService to properly handle instances where module shuts down

## 7.0.2 2024-04-26
  * ERM-3190 DB Connections are not being released
    * Transactional annotations were being ignored, refactored JobRunnerService to properly set up necessary transactions

## 7.0.1 2024-04-17
  * ERM-3193 Error saving proxy
  * ERM-3191 Add logging to support issue triage for HibernateOptimisticLockingFailureException
  * ERM-3190 DB Connections are not being released
  * ERM-3185 Remove PTI/PCI filter from the /titles/electronic endpoint in Agreements
  * ERM-3181 sourceTitleCount should not be required on uploading packages via JSON using mod-agreements-package schema
  * ERM-3180 LOCAL external source should not offer delete option
  * ERM-3177 Check on `noSICount` in WorkSourceIdentifierTIRSImpl is not correct
  * ERM-3174 Review outdated/vulnerable dependencies in mod-agreements
  * ERM-3157 Missed declaration of backend permissions
  * ERM-3139 Propose additional info.log messages for jobs/processes
  * ERM-3048 Add a package property to store remote source current title count

## 7.0.0 2024-03-22
  * ERM-3155 URL on PTI is limited to 255 characters
  * ERM-3154 Consolidate package upsert summary logging in info log
  * ERM-3153 Remove match_key porcesses/domain modelling
  * ERM-3152 Ensure external data source set to idle after zombie job found
  * ERM-3151 Implement flag to not fall back to IDFirstTIRS
  * ERM-3148 Query optimisation on data ingest
  * ERM-3141 Add remote source current title count property to data model
  * ERM-3127 Use clearly identifiable user-agent for requests to GOkb
  * ERM-3120 Add Content type filter to Agreements dashboard widget
  * ERM-3111 Upgrade Grails from 5 to 6
  * ERM-3072 Identify and implement indexes to be added
  * ERM-3063 StringTemplate.Context refdata should be internal
  * ERM-1796 Indicated module name in pg_stat_activity

## 6.0.1 2023-11-09
  * ERM-3087 Add debug message to indicate ingest mode
  * ERM-3064 Add preconditions to index creation
  * ERM-3061 On setting document filter spaces are stripped from filter value
  * Some service choices can now be selected via ENV_VAR at run time.
    * `TIRS` can be chosen from: `TitleFirst`, `WorkSourceIdentifier` and `IdFirst` (default)
    * `INGRESS_TYPE` can be chosen from : `PushKB` and `Harvest` (default)
  * Extra logging enabled for aiding with TIRS debugging


## 6.0.0 2023-10-11
  * ERM-3050 Title instance metadata overwritten by non-matching work
  * ERM-3047 Remove "accessEnd date guessed for retired title..." from job logs
  * ERM-3044 Open Access custom property has space in property name
  * ERM-3022 Error on attempting to download job error or info logs
  * ERM-3020 IsNotSet not working for custom properties
  * ERM-3018 Implement GOKb Title UUID as primary match ID where available
  * ERM-2992 Switch default behaviour for Agreements to not expand items
  * ERM-2966 Error when match and filter contain the same path root
    * update to 'com.k_int.grails:web-toolkit-ce:8.1.0'
  * ERM-2961 Extend length of document URL to 2048 chars
  * ERM-2956 Investigate issues on deploying agreements/licenses/service-interaction/serials-management
  * ERM-2940 spring-webmvc 5.3.25 security bypass vulnerability
  * ERM-2934 Agreement lines simple search widget definition
  * ERM-2925 Add backend support for retrieving list of source values
  * ERM-2923 Add created/updated metadata for Resources in local KB
  * ERM-2919 Gracefully handle ingesting PCIs with long notes into the Local KB
  * ERM-2910 JSON package import fails
  * ERM-2909 Gracefully handle ingesting resources with long names into the Local KB
  * ERM-2885 Reduce number of internal contact role defaults in Agreements
  * ERM-2641 Upgrade to Grails 5 (including Hibernate 5.6.x) for Poppy
  * ERM-2630 Add new endpoint for external KB "push" process
  * ERM-2628 Add Alternative Name filter to Agreements dashboard widget
  * ERM-2624 Add view only permissions for Agreement settings

## 5.5.0 2023-02-20
  * ERM-2638 No name of linked agreement in license
  * ERM-2629 Add Description filter to Agreements dashboard widget
  * ERM-2612 Add support for content type property on Agreements
  * ERM-2611 Implement change to Package key used for GOKb packages
  * ERM-2610 Implement 'alternative slugs' for package in package schema
  * ERM-2569 MG/Nolana | Package/Title managed/custom coverage dates from eHoldings not displaying in Agreement Line accordion
  * ERM-2514 License terms are not displayed in Publication Finder when a journal title is directly linked to an agreement
  * ERM-2510 Titles Endpoint does not have functionality of eresources "electronic" endpoint
  * ERM-2509 URL customisers applied for all platforms
  * ERM-2508 Cannot delete string templates
  * ERM-2482 Incorrect overlapping coverage errors created on import to local KB
  * ERM-2480 Refactor string template to improve performance
  * ERM-2475 Loading update to existing package fails if lifecycleStatus or availabilityScope are omitted
  * ERM-2474 Local KB admin: Info log export populates title element in additionalInfo with non-relevant data
  * ERM-2469 HibernateException: Duplicate identifier in table for AppSetting
  * ERM-2443 Title Instance appearing twice in /erm/resource/electronic search
  * ERM-2433 Upgrade hibernate, postgresql, opencsv, minio, okhttp, kotlin
  * ERM-2431 Job runner gets stuck in loop with NullPointerException
  * ERM-2423 Performance issues on displaying external agreement lines
  * ERM-2422 JSON errors on agreement lines in Nolana bugfest
  * ERM-2389 Importing KBART fails
  * ERM-2387 Package name field missed in updating package metadata definitions
  * ERM-2306 Add availabilityConstraints to packages
  * Added more harvest logging
## 5.4.0 2022-10-26
  * ERM-2388 Package identifiers not 'removed' from package on ingest of updated package
  * ERM-2385 On updating package description URLs KB sync job crashes
  * ERM-2378 Remove defaults from ContentType
  * ERM-2370 Extend GOKb harvest to include new monograph metadata fields and stop using secondary lookup
  * ERM-2367 New availabilityScope values not created on GOKb harvest
  * ERM-2361 Minimise list of default values for availabilityScope
  * ERM-2311 Support setting Package Description and Description URLs on package ingest and GOKb harvest
  * ERM-2310 On updating an existing PTI from a trusted source in Agreements local KB, update/overwrite url if it has changed
  * ERM-2307 Populate availabilityConstraints from GOKb package ingest
  * ERM-2305 Entitlement options not displaying
  * ERM-2304 Cannot populate package lifecycleStatus when uploading JSON package
  * ERM-2288 On updating an existing package in Agreements local KB, update/overwrite existing package metadata
  * ERM-2285 StaleObjectStateException errors in Morning Glory bugfest
    * ERM-2286 Add instrumentation to logs for better troubleshooting
  * ERM-2219 Description property missing from package schema
  * ERM-2093 Populate provider specific IDs from GOKb
  * ERM-946 Title identifiers have no fixed order
  * Added readonly transaction scopes for read only operations in subscription agreements and entitlements

## 5.3.1 2022-11-08
  * ERM-2431 Job runner gets stuck in loop with NullPointerException

## 5.3.0 2022-09-27
  * ERM-2345 Add federation logic

## 5.2.0 2022-06-29
  * ERM-2219 Description property missing from package schema
  * ERM-2218 Removing values from multi-valued custom property in UI does not update the values
  * ERM-2209 New package metadata fields give errors on null
  * ERM-2201 Error on attempting to render agreement with a supplementary property
  * ERM-2174 Invalid JSON returned for some entitlementOptions
  * ERM-2172 Populate extended Package metadata from GOKb data (hasMany properties)
  * ERM-2124 Add multi-value support to custom properties
  * ERM-2094 Populate GOKb UUID and ID as package identifier
  * ERM-2092 Include package IDs in exports
  * ERM-2069 mod-agreements Grails wrapper SAXParseException
  * ERM-2059 Populate extended Package metadata from GOKb data
  * ERM-2046 Support package identifiers
  * ERM-2045 Extend Package data model to store additional information
  * ERM-2044 Ability to make custom properties deprecated
  * ERM-2042 Duplicating agreement/license inc. documents moves files from original to new agreement/license
  * ERM-1986 Move identifiers between title instances
  * ERM-1981 Add version property for Subscription Agreements
  * ERM-1850 In e-resources search and sort results only include results that have entitlementOptions
  * ERM-778 Add Date type to customProperties
  * Removed GOKb_TEST from sample data
  * Removed default perPage 100 in urlMappings for customPropertyDefinitions

## 5.1.0 2022-03-03
  * ERM-2036 Remove 'is not' option from Organization filter in Agreements simple search
  * ERM-2001 Duplication of 'suppressFromDiscovery' key in items json
  * ERM-2000 Performance Issue: Slowness in loading agreements list - Kiwi
  * ERM-1988 Backend & API support for movement of identifiers from one title instance to another
  * ERM-1938 Abstract and subclass existing toolkit FileObject (for Agreements)
  * ERM-1932 Remove coverage statement errors from KB Sync Error logs
  * ERM-1931 Sort agreement lines by name then reference then UUID in the UI
  * ERM-1926 Error in entitlementLog on deleting agreement line
  * ERM-1917 Duplicate title instance created on package import in Kiwi bugfest
  * ERM-1914 Exception thrown on rendering the agreement / entitlement gson templates
  * ERM-1912 In the case of a package harvested from GOKb/external source lacking key information about the package, log the issue and fail gracefully
  * ERM-1903 On editing an agreement with an agreement line for a PCI, "custom coverage" incorrectly populated
  * ERM-1902 Framework for UPDATE entitlement log entry events
  * ERM-1900 Change to suppressFromDiscovery should lead to updated entry in EntitlementLog
  * ERM-1892 Error on attempting to view comparison job report
  * ERM-1891 Agreement Lines do not display in Agreement edit screen
  * ERM-1890 On ingesting TIPPs from GOKb only attempt to create a coverage statement in Local KB if there is a coverage statement in GOKb
  * ERM-1884 Populate "suppress" field in EntitlementLog
  * ERM-1883 Change to coverage should lead to updated entry in EntitlementLog
  * ERM-1882 Use Custom Coverage (if exists) in EntitlementLog
  * ERM-1813 Include sibling title instance details in agreement and agreement line export
    * ERM-1879 Introduce Grails integration test for Export
  * ERM-1800 Create "re-match" process for existing PTI/PCIs
  * ERM-1799 Store "match key" information on PTI and PCI
  * ERM-1744 Add Organisations to Agreements simple search widget definition

## 5.0.0 2021-10-04
 * ERM-1848 Support duplicating supplementary docs and supplementary properties separately on duplicating an agreement
 * ERM-1847 Failure to resolve a title should lead to skipping title
 * ERM-1819 Add PTI URL to entitlement log entries
 * ERM-1816 Error on tagging an agreement line which references an external (eHoldings) resource
 * ERM-1801 Implement Title Ingest process
 * ERM-1781 Error on adding coverage to PCI which overlaps with existing coverage on PTI/TI
 * ERM-1777 Templated URL not updating on deletion of Proxy
 * ERM-1774 Regularly remove organizations that do not have any links to Agreements/Packages
 * ERM-1766 Change restriction on GOKb harvest `editStatus` value
 * ERM-1765 triggerEntitlementLogUpdate fails after deletion of agreement line
 * ERM-1755 If a KBART file fails to load, the job outcome should be "failure"
 * ERM-1754 PCI accessStart and accessEnd dates should be ignored when PCI added as individual resource to agreement
 * ERM-1753 Migration to new Org / Org role structure leads to duplicate notes
 * ERM-1747 Bump versions across ERM apps follow Organization management changes
 * ERM-1739 Remove duplicate stanzas from application.yml
 * ERM-1723 Use label rather than name when sorting custom properties in agreements
 * ERM-1649 Strictly enforce ISSN and ISBN vs eISSN/pISSN and eISBN/pISBN
 * ERM-1645 The package resourceCount incorrectly includes PCIs with a removedTimestamp
 * ERM-1542 Make organization roles for agreements editable in tenant settings
 * ERM-1540 Support for multiple roles per organisation in Agreements
 * ERM-1459 Support a log of resources that have been added/removed from a library collection
 * ERM-1231 Add date created/last updated metadata to Agreements
 * ERM-1001 Separate permissions for file download in Licenses/Agreements
 * ERM-506 KB Local Admin | Export import logs as JSON

## 4.1.0 2021-06-15
 * ERM-1730 Add renewalPriority to agreement widget definition
 * ERM-1724 Reduce running time for StringTemplateBulkSpec integration test
 * ERM-1696 Added match terms to WidgetDefinitions
 * ERM-1652 Agreement jobs simple search widget definition
 * ERM-1651 Agreements simple search widget definition
 * ERM-1650 Add unique indexes for refdata tables
 * The package resourceCount incorrectly includes PCIs with a removedTimestamp
 * ERM-1643 Implemented dashboard interface
 * ERM-1632 Remote KB "LOCAL" should be created as read only
 * FOLIO-3131: Use https for maven.k-int.com
 * FOLIO-3106 Use https for maven.indexdata.com
 * Enable tenant logging
 * Harmonize snapshot versions
 * Added sample KBs

## 4.0.1 2021-03-31
 * ERM-1616 ERM admin/triggerHousekeeping fails

## 4.0.0 2021-03-15
 * ERM-1567 Cancellation deadline is now non-transient property on agreement
 * ERM-1564 Period starting/ending on current date not shown as current period
 * ERM-1534 Changed Agreement API to no longer directly expose "current agreement", switching instead to a tag on the period object.
 * ERM-1533 Agreement start and end dates should be the earliest period start and latest period end date respectively
 * ERM-1457 Extend length of field for monographVolume, monographEdition, firstAuthor, firstEditor
 * ERM-1248 If local platform code not set, appears as `null` when a customised URL is proxied
 * ERM-1246 Template with null output will fail silently
 * ERM-1243 CustomProperty values not duplicated properly
 * ERM-1238 Agreements /erm/entitlements endpoint doesn't expand owner when entitlement is external
 * ERM-1213 Exporting Resources from Agreement when "All" filter selected does not export all resources
 * ERM-1203 Agreement Resources Export contains duplicate resources and incorrect agreement line information
 * ERM-1156 Support user access to platform records
  * ERM-1157 Implement Platform controller and URLMappings in mod-agreements
  * ERM-1185 Add hasMany platformLocators to platform in domain model
 * ERM-1079 Unable to delete license without permissions in agreements module
 * ERM-1048 Implement "template" mechanism to create URLs for resources based on existing data
  * ERM-1108 Add proxied URLs to exports
 * ERM-1047 Support for customised URLs at platform level
  * ERM-1135 Platform local code field
 * ERM-972 Missing permission definitions
 * Removed static version from vagrant box for developers
 * Feature ii kbplus kb -- Internal tweaks to the KIJPFAdapter
## 3.0.0 2020-10-14
 * ERM-1180 Add @ EnableScheduling annotation in mod-agreements
 * ERM-1163 Remove default values from publicationType
 * ERM-1154 change name of an agreement with local resource AL fails
 * ERM-1147 Incorrect values displaying in "Acquisition method" column in "Agreements for this e-resource" MCL
 * ERM-1131 GOKb record numbers in info/error log
 * ERM-1117 Adding PCI to basket from /erm/eresources/<pci UUID> view results in different JSON shape added to basket
 * ERM-1114 Duplicating an Agreement with Supplementary Documents does not duplicate the documents -- data cleanup service job created
 * ERM-1101 Attempt to export All resources in KBART fails
 * ERM-1094 Error on saving PCI
 * ERM-1093 Fetching an Agreement with a PCI Entitlement gives 500 error
 * ERM-1086 Exception being thrown on adding an agreement line while creating an agreement
 * ERM-1080 Logging not working after Grails upgrade
 * ERM-1070 Support POST operation for creating new entitlements
 * ERM-1069 Add an agreement line without a resource
 * ERM-1055 Comparison report "overlap" discrepancy
 * ERM-1046 Non-phrase searching support for agreements
 * ERM-1012 Correct order of fields in KBART export
 * ERM-1010 Ensure use of MDC fields is consistent in import job error and info logging
 * ERM-1006 "Future" and "Dropped" title lists do not include directly linked PCIs
 * ERM-994 Comparison treats two separate resources with same title as the same
 * ERM-966 Add "Note" to Organisation link in Agreements
 * ERM-952 Process and save package/agreement comparisons
 * ERM-948 Enhance eHoldings display in dedicated Agreement Line view
 * ERM-943 Separate ERM `publication type` from `type`
 * ERM-932 Add TI "suppress from discovery" field to Agreement resource JSON export
 * ERM-904 Update tooling and framework
   * ERM-908 Update agreements to Grails 4
   * ERM-909 Update docker image to Java 11
  * ERM-892 Sample data being used on setup
  * ERM-851 Securing APIs by default
 * ERM-742 Custom properties: Backend validation not working
 * ERM-510 KB Local Admin | Log should include information to identify Record where appropriate

## 2.3.0 2020-06-11
 * ERM-941 remove time check from remotekb harvest
 * ERM-931 Add Agreement Line "suppress from discovery" field to agreement resource JSON export
 * ERM-930 Add PCI "suppress from discovery" field to Agreement resource JSON export
 * ERM-929 Add Agreement Line tags to agreement resource JSON export
 * ERM-925 Add PCI and TI tags to resources JSON export
 * ERM-910 Loading titles to local KB as JSON or KBART fails
 * ERM-903 Migration scripts has hardcoded diku tenant
 * ERM-897 Add support for tags to agreement lines
 * ERM-896 Add support for tags to eresources
 * ERM-895 Add "Suppress from discovery" property to Agreement lines
 * ERM-894 Add "Suppress from discovery" property to e-resources
 * ERM-891 Require Agreement Names to be unique (Backend Validation)
 * ERM-889 Add new agreement-agreement relationships
 * ERM-866 Import of KBART incorrectly generates errors for monograph coverage
 * ERM-864 Updates of selected fields fail silently
 * ERM-837 Agreement line note does not save and/or retrieve for Agreement lines from eHoldings
 * ERM-834 PCI not created when title is too long
 * ERM-827 Add support for "Alternative name" for agreements
 * ERM-826 RemoteLicenseLink.Status refdata category should be "internal"
 * ERM-807 Add Embargo to PCI in Agreement exports
 * ERM-800 Add "Embargo" to local KB data model and imports
 * ERM-793 Support controlled updating of title instances in agreements local KB
 * ERM-786 Agreement supplementary property descriptions are limited to 255 characters
 * ERM-775 Use information from siblingInstances in KBART export
 * ERM-773 Import service | Missing instanceMedia on import should skip line and continue
 * ERM-772 KBART export should use subtype to classify identifier as print_identifier or online_identifier
 * ERM-769 Error loading KBART file
 * ERM-767 Monograph resources are not exported in KBART format
 * ERM-735 Separate refdata categories into "internal" and "user" lists
 * ERM-689 Add monograph fields to KBART export
 * ERM-685 Support import and processing of KBART in Local KB Admin
 * ERM-681 Fetch enhanced metadata for books from GOKb after harvesting
 * ERM-547 Interpret GOKb Package Status, Edit Status and List Status
 * ERM-427 No way of fetching agreement entitlements sorted by name
 * ERM-193 Deleting a License (and possibly Agreement) with a Tag isn't possible

## 2.2.2 2020-04-29
 * ERM-858 Postgres sequences out of sync when existing agreement data is upgraded

## 2.2.1 2020-04-18
 * ERM-838 Add custom_properties_id to existing subscription_agreement entries (upgrade)

## 2.2.0 2020-03-13
 * ERM-747 Custom Properties: Unable to correctly save decimals with german browser locale
 * ERM-711 POST/PUT on erm/sas/{:id} endpoint appears to not save reasonForClosure
 * ERM-696 Agreements: Reason for closure is not displayed
 * ERM-689 Add monograph fields to KBART export
 * ERM-685 Support import and processing of KBART in Local KB Admin
   * ERM-687 Create tsv parser
 * ERM-682 Upgrading mod-agreements module from Daisy to Edelweiss fails
 * ERM-680 Show spinner in agreement lines when switching between agreements
 * ERM-676 Support Editor information in Agreements local KB
 * ERM-655 Sorting limits output in some cases
 * ERM-649 Display a better message when the ExternalDataSources name isn't unique
 * ERM-641 License custom property "note" field should not be returned in public API
 * ERM-587 Agreements : On duplicating an agreement the "General notes about this agreement's license" field should be copied
 * ERM-571 Ensure remote KB syncs are not left "In Progress" when an error occurs.
 * ERM-507 Agreement: Agreement Lines: Package item count always equals "1"
 * ERM-486 Support monograph volume in Agreements local KB
 * ERM-485 Support e/p monograph publication dates in Agreements local KB
 * ERM-482 Support Author information in Agreements local KB
 * ERM-481 Support Edition information in Agreements local KB
 * ERM-461 Local KB Admin | Log error when accessEnd date before accessStart date
 * ERM-437 Local KB admin | External data source name should be unique
   * ERM-643 Add validation clause to ensure that the names data source names are unique
 * ERM-434 "LOCAL" Remote KB/data source should not be editable or be able to be deleted
   * ERM-644 Add preUpdate and preDelete listeners
 * ERM-356 Expose license terms over API
   * ERM-361 Support API for retrieving license terms based on a resource identifier

## 2.0.1 2020-01-28
 * ERM-682 Upgrading mod-agreements module from Daisy to Edelweiss fails

## 2.0.0 2019-12-04
 * ERM-621 License terms not included in Agreement JSON export
 * ERM-614 rkb_cursor reset after harvest completed
 * ERM-583 Use JVM features to manage container memory
 * ERM-561 GOKb harvest job ends in failure
 * ERM-552 Clone endpoint clones properties that are omitted/set to false
 * ERM-546 Interpret <status> element on GOKb TIPP
 * ERM-538 Support health check endpoint (for example /admin/health provided by RMB)
 * ERM-536 mod-agreements Re-assess the container Memory allocation in default LaunchDescriptor
 * ERM-515 Agreements | Handling removed/deleted resources
 * ERM-508 Support multiple POLs on a single Agreement Line
 * ERM-505 Move test data so it's only active for the diku tenant only
 * ERM-500 reasonForClosure Field is not cleared
 * ERM-498 Modify KB Local admin to improve the retrieval and display of logs
 * ERM-477 License and agreement APIs are not protected by FOLIO permissions
   * ERM-479 Add permission definitions and api endpoint config
 * ERM-465 Close an Agreement
   * ERM-483 Migrate agreement status "Rejected" to 'Reason for closure'
 * ERM-462 Agreement | Export resources covered by this agreement gives error
 * ERM-457 Use Agreement Line active from/active to dates when calculating current/expected/previous content of agreement
 * ERM-443	Remove "existing coverage statements overlapping" messages from info log
 * ERM-440 KB Local Admin | Titles not searchable/viewable after file upload
 * ERM-419 Duplicate refdata entries in folio builds
 * ERM-418 Duplicate titles in KBART export
 * ERM-396 Viewing upcoming joiners/leavers for a package
 * ERM-395 Populate access start and access end dates
 * ERM-394 Viewing only current content of package in UI
   * ERM-407 Ensure "E-resources in package" concertina only includes resources currently part of the package
   * ERM-406 Ensure Agreement resources export only includes resources currently part of the package
 * ERM-393 Reporting change to the date a resource joined/date left package
   * ERM-401 Local KB Admin | Add info logging for number of titles where access_start updated
   * ERM-400 Local KB Admin | Add info logging for number of titles where access_end updated
 * ERM-392 Reporting change in title count in a package
 * ERM-377 entitlementOptions endpoint is too slow
 * ERM-376 Export full Agreement as JSON
 * ERM-362 Issue with calling install?purge=true option multiple times
 * ERM-349 Create New Local KB Import - backend work
 * ERM-338 Log import/sync errors on package import
 * ERM-315 Linking PO Line to an agreement line for an eHolding resource fails
 * ERM-297 File attachment over 10MB causes out of memory errors
 * ERM-285 Entitlement Options endpoint throws a 500 Error
 * ERM-182 On sync with remote KB, keep TIPPs where coverage data is incompatible

## 1.8.0 2019-09-11
 * ERM-440 KB Local Admin | Titles not searchable/viewable after file upload
 * ERM-349 Create New Local KB Import - backend work
 * ERM-338 Log import/sync errors on package import
 * ERM-182 On sync with remote KB, keep TIPPs where coverage data is incompatible

## 1.8.0 2019-08-12
 * ERM-290 Set up Job Management for Local KB Admin
   * ERM-334 Delete job not in progress
 * ERM-274 Add cleanup task for orphan file uploads
 * ERM-265 Export Agreement data as JSON
 * ERM-215 Export Agreement data as KBART
 * ERM-327 Export entitlements as JSON from single agreement
 * ERM-328 Export entitlements as KBART from single agreement

## 1.8.0 2019-07-24
 * ERM-273 Manage usage data providers on agreements
   * ERM-291 Add usage data provider to subscription agreement
 * ERM-262 Agreement start and end date display with a time offset in some environments
 * ERM-189 Custom coverage dates display with a time offset in some environments

## 1.7.0 2019-06-11
 * ERM-259 Set supplementary information for an Agreement
 * ERM-245 Tenant bootstrap improvements
   * ERM-249 Create bootstrap data
   * ERM-247 Change descriptors to reflect new interface version

## 1.6.0 2019-05-21
 * ERM-220 Support Organizations app as source of Organizations in Agreements
 * ERM-198 Ensure that mod-agreements used in place of olf-erm
 * ERM-92  Require UUIDs that are RFC 4122 compliant
   * ERM-135 Change UUID generator from UUID to UUID-2

## 1.5.0 2019-05-07

 * ERM-166 Remove unwanted extra license section
 * ERM-133 Configure Document Categories
 * ERM-143 Add License / Supplementaty License Information Panel UI
 * ERM-181 Fix data sync issue with GOKb (Resumption Token and Broken Coverage)
 * ERM-139 Convert from SearchAndSort to SearchAndSortQuery
 * ERM-79 Set supplementary informaiton for a license
 * ERM-173 Manage Tags on Agreements
 * ERM-174 Seach Agreements by Tag
 * ERM-194 BUGFIX: Opening edit/create license with only one page does not work

## 1.4.0 2019-04-08

 * ERM-115 Provide correct data for agreement line
 * ERM-111 Build Settings Page
 * ERM-112 Build Wrapper Component for supression
 * ERM-113 Use Wrapper Component in Agreements
 * ERM-114 Write tests
 * ERM-98 Rendering Controlling Terms License
 * ERM-127 Resources with no coverage set should not display
 * ERM-110 Agreement Detail record - View attached EBSCO eResource
 * ERM-109 Support the ability to create an agreement from eHoldings
 * ERM-108 Supress agreements app functions
 * ERM-64 Show Controlling License Terms

## 1.3.0 Not issued

## 1.2.0 2019-03-22
 * ERM-130 Sort order of multiple coverage statements should be ascending by start date
 * ERM-129 Cannot edit custom coverage dates once they have been added
 * ERM-65 Manage custom entitlement coverage for titles
 * ERM-91 Indicate the coverage for a title within an Agreement
 * ERM-63 View linked agreement details in a license

## 1.1.0 2019-03-12
 * ERM-59 Manage licenses linked to agreements
 * ERM-71 Add Model for JSON resource
 * ERM-47 Fix defaults in SubsciriptionAgreementOrg
 * ERM-46 Update note about a license for an agreement
 * ERM-41 Manage external licenses for an Agreement
 * ERM-70 Add LicenseAttachment Domain model
 * ERM-44 Remove an external license from an Agreement
 * ERM-43 Edit external license details
 * ERM-42 Add external license for an Agreement
 * ERM-7 Add an Organisation to a License

## 1.0.3 2019-02-23

 * ERM-1 eResource Managers can manually create licenses
 * ERM-6 Set pre-defined Terms for a License
 * ERM-7 Add an Organisation to a License
 * ERM-8 Add an Organisation to an existing License
 * ERM-10 Remove an Organisation from a License
 * ERM-11 eResource Managers can edit basic license details
 * ERM-12 Filter License Search Results by License Status
 * ERM-13 Filter License Search Results by License Type
 * ERM-16 Set open-ended License Expiry
 * ERM-17 See basic License details in search results
 * ERM-35 Filter Agreement Search Results by Organisation Role

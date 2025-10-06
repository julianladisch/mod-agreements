package org.olf.erm

import com.k_int.accesscontrol.core.policycontrolled.PolicyControlled

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.olf.PeriodService
import org.olf.general.DocumentAttachment
import org.olf.general.Org
import org.olf.kb.ErmTitleList
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.support.RequestContextUtils

import com.k_int.web.toolkit.custprops.CustomProperties
import com.k_int.web.toolkit.domain.traits.Clonable
import com.k_int.web.toolkit.refdata.CategoryId
import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.tags.Tag

import grails.gorm.MultiTenant
import groovy.util.logging.Slf4j
import javax.persistence.Transient

/**
 * Subscription agreement - object holding details about an SA connecting a resource list (Composed Of packages and platform-titles).
 */
@Slf4j
@PolicyControlled(
  resourceIdColumn = "sa_id", // For grails modules we use SQL Column name for resourceId
  resourceIdField = "id"
)
public class SubscriptionAgreement extends ErmTitleList implements CustomProperties,MultiTenant<SubscriptionAgreement>, Clonable<SubscriptionAgreement> {

  static cloneStaticValues = [
    periods: { [new Period('owner': delegate, 'startDate': LocalDate.now())] },
    name: { "Copy of: ${owner.name}" /* Owner is the current object. */ }
  ]
  static copyByCloning = ['customProperties', 'supplementaryDocs', 'docs', 'externalLicenseDocs']

  String description
  String id
  String name
  String localReference
  String vendorReference
  String attachedLicenceId
  String licenseNote
  Date dateCreated
  Date lastUpdated
  LocalDate renewalDate
  LocalDate nextReviewDate

  /**
   * By default the RefdataCategory would be generated from the concatenation
   * of the class name and the property name. So the below property of agreementType
   * would result in a category named SubscriptionAgreement.AgreementType
   *
   * If we wanted to share a category across multiple Classes (like a global "Yes_No"),
   * or just wanted to specify the category, we can use the \@CategoryId annotation.
   *
   * @CategoryId('AgreementType') - Would create a category named 'AgreementType' for values stored here.
   */
  @Deprecated
  @Defaults(['Draft', 'Trial', 'Current']) // Defaults to create for this property.
  RefdataValue agreementType

  @Defaults(['Definitely renew', 'For review', 'Definitely cancel'])
  RefdataValue renewalPriority

  @CategoryId(defaultInternal=true)
  @Defaults(['Draft', 'Requested', 'In negotiation', 'Active', 'Closed'])
  RefdataValue agreementStatus

  @Defaults(['Cancelled', 'Ceased', 'Superseded', 'Rejected'])
  RefdataValue reasonForClosure

  @CategoryId(value='Global.Yes_No', defaultInternal=true)
  @Defaults(['Yes', 'No'])
  RefdataValue isPerpetual

  @CategoryId(value='Global.Yes_No', defaultInternal=true)
  @Defaults(['Yes', 'No'])
  RefdataValue contentReviewNeeded

  Boolean enabled

  Org vendor

  Set<Period> periods = []

  Set<Entitlement> items
  Set<AlternateName> alternateNames
  Set<SubscriptionAgreementContentType> agreementContentTypes

  private Period currentPeriod

  LocalDate startDate
  LocalDate endDate
  LocalDate cancellationDeadline

  static hasMany = [
           alternateNames: AlternateName,
                    items: Entitlement,
             historyLines: SAEventHistory,
                 contacts: InternalContact,
                     tags: Tag,
                     orgs: SubscriptionAgreementOrg,
      externalLicenseDocs: DocumentAttachment,
                     docs: DocumentAttachment,
           linkedLicenses: RemoteLicenseLink,
        supplementaryDocs: DocumentAttachment,
       usageDataProviders: UsageDataProvider,
                  periods: Period,
      inwardRelationships: AgreementRelationship,
     outwardRelationships: AgreementRelationship,
    agreementContentTypes: SubscriptionAgreementContentType,
  ]

  static mappedBy = [
            alternateNames: 'owner',
                     items: 'owner',
              historyLines: 'owner',
                  contacts: 'owner',
                      orgs: 'owner',
            linkedLicenses: 'owner',
        usageDataProviders: 'owner',
                   periods: 'owner',
       inwardRelationships: 'inward',
      outwardRelationships: 'outward',
     agreementContentTypes: 'owner',
  ]

  static mapping = {
             description column:'sa_description', type: 'text'
                      id column:'sa_id', generator: 'uuid2', length:36
                 version column:'sa_version'
                    name column:'sa_name'
          localReference column:'sa_local_reference'
         vendorReference column:'sa_vendor_reference'
             renewalDate column:'sa_renewal_date'
          nextReviewDate column:'sa_next_review_date'
           agreementType column:'sa_agreement_type'
         renewalPriority column:'sa_renewal_priority'
         agreementStatus column:'sa_agreement_status'
         reasonForClosure column:'sa_reason_for_closure'
             isPerpetual column:'sa_is_perpetual'
     contentReviewNeeded column:'sa_content_review_needed'
                 enabled column:'sa_enabled'
                  vendor column:'sa_vendor_fk'
       attachedLicenceId column:'sa_licence_fk'
             licenseNote column:'sa_license_note'
               startDate column: 'sa_start_date'
                 endDate column: 'sa_end_date'
    cancellationDeadline column: 'sa_cancellation_deadline'
             dateCreated column: 'sa_date_created'
             lastUpdated column: 'sa_last_updated'
          alternateNames cascade: 'all-delete-orphan'
                   items cascade: 'all-delete-orphan', lazy: false
                contacts cascade: 'all-delete-orphan', lazy: false
            historyLines cascade: 'all-delete-orphan'
                    tags cascade: 'save-update'
                 periods cascade: 'all-delete-orphan', lazy: false
                    orgs cascade: 'all-delete-orphan', lazy: false
                    docs cascade: 'all-delete-orphan', lazy: false
     externalLicenseDocs cascade: 'all-delete-orphan', joinTable: [name: 'subscription_agreement_ext_lic_doc', key: 'saeld_sa_fk', column: 'saeld_da_fk']
          linkedLicenses cascade: 'all-delete-orphan'
       supplementaryDocs cascade: 'all-delete-orphan', joinTable: [name: 'subscription_agreement_supp_doc', key: 'sasd_sa_fk', column: 'sasd_da_fk']
      usageDataProviders cascade: 'all-delete-orphan'
     inwardRelationships cascade: 'all-delete-orphan', lazy: false
    outwardRelationships cascade: 'all-delete-orphan', lazy: false
        customProperties cascade: 'all-delete-orphan'
    agreementContentTypes cascade: 'all-delete-orphan'
  }

  static fetchMode = [periods: 'eager']

  static constraints = {
                    name(nullable:false, blank:false, unique: true)
             dateCreated(nullable:true)
             lastUpdated(nullable:true)
               startDate(nullable:true, bindable: false)
                 endDate(nullable:true, bindable: false)
    cancellationDeadline(nullable:true, bindable: false)
          localReference(nullable:true, blank:false)
         vendorReference(nullable:true, blank:false)
             renewalDate(nullable:true)
          nextReviewDate(nullable:true)
           agreementType(nullable:true)
        reasonForClosure(nullable:true)
         renewalPriority(nullable:true)
         agreementStatus(nullable:false)
             isPerpetual(nullable:true)
     contentReviewNeeded(nullable:true)
                 enabled(nullable:true)
             description(nullable:true, blank:false)
                  vendor(nullable:true)
       attachedLicenceId(nullable:true, blank:false)
             licenseNote(nullable:true, blank:false)
                 periods(nullable:false, minSize: 1, validator:Period.PERIOD_COLLECTION_VALIDATOR, sort:'startDate')

          linkedLicenses(validator: { Collection<RemoteLicenseLink> license_links ->

            int controlling_count = ((license_links?.findAll({ RemoteLicenseLink license -> license.status?.value == 'controlling' })?.size()) ?: 0)
            ( controlling_count > 1 ? [ 'only.one.controlling.license' ] : true )
          })

          // it should be validated that primaryOrg is true only for one org per subscription agreement
          orgs(validator: { Collection<SubscriptionAgreementOrg> sa_orgs, _obj, errors ->
            int primaryCount = ((sa_orgs?.findAll({ SubscriptionAgreementOrg org -> org.primaryOrg == true })?.size()) ?: 0)
            if (primaryCount > 1) {
              errors.rejectValue('orgs', 'only.one.primary.org')
            }
          })
  }

  def beforeValidate() {
    checkAgreementStatus()
    calculateDates()
  }

  public void checkAgreementStatus () {

    // Null out the reasonForClosure if agreement status is not closed
    if (agreementStatus?.value != 'closed') {
      reasonForClosure = null
    }
  }

  public void calculateDates () {
    LocalDate originalStartDate = this.startDate
    LocalDate originalEndDate = this.endDate
    LocalDate originalCancellationDeadline = this.cancellationDeadline

    startDate = PeriodService.calculateStartDate(periods)
    endDate = PeriodService.calculateEndDate(periods)
    cancellationDeadline = PeriodService.calculateCancellationDeadline(periods)

    if (
        startDate != originalStartDate ||
        endDate != originalEndDate ||
        cancellationDeadline != originalCancellationDeadline
    ) {
      // This is currently ONLY required when SubscriptionAgreement
      // has a customProperty of type "MultiRefdata". This behaviour is
      // a complete mystery to me, and while the below solves it, it seems
      // a quick-and-dirty (pardon the pun) fix for some complicated
      // grails domain interactions happening under the hood.
      // I'd like to return to this and understand what's going wrong.
      //
      // In general I think the use of `beforeValidate`
      // everywhere to persist what would otherwise be transient
      // values is a pattern we should really strive to stamp out anyway
      this.markDirty()
    }
  }

  /**
   * Need to resolve the conflict manually and add the call to the clonable method here.
   */
  @Override
  public SubscriptionAgreement clone () {
    Clonable.super.clone()
  }

  @Transient
  public LocalDate getLocalDate() {
    LocalDate ld
    // Use the request if possible
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes()
    if(attributes && attributes instanceof GrailsWebRequest) {

      GrailsWebRequest gwr = attributes as GrailsWebRequest

      log.trace "Is within a request context"
      TimeZone tz = RequestContextUtils.getTimeZone(gwr.currentRequest) ?: TimeZone.getDefault()

      log.trace "Using TZ ${tz}"
      ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.now(), tz.toZoneId())

      log.trace "Now in ${tz} is ${zdt}"
      ld = zdt.toLocalDate()

      log.trace "LocalDate of ${ld} extracted for query"
    } else {
      log.trace "Is not within a request context, using default TZ (${TimeZone.getDefault()})"
      ld = LocalDate.now()
    }

    ld
  }

  @Transient
  public String findCurrentPeriod() {
    log.trace "Find current period"
    LocalDate ld = getLocalDate()

    String cpId = this.periods.find(p ->
      (p.startDate <= ld) &&
      (p.endDate >= ld || p.endDate == null)
    )?.id

    cpId
  }

  @Transient
  public String findPreviousPeriod() {
    log.trace "Find previous period"
    LocalDate ld = getLocalDate()
    String ppId = this.periods.findAll(p -> (p.endDate != null && p.endDate < ld)).max({a,b -> a.startDate - b.startDate})?.id;

    ppId
  }

  @Transient
  public String findNextPeriod() {
    log.trace "Find next period"
    LocalDate ld = getLocalDate()
    String npId = this.periods.findAll(p -> p.startDate > ld).min({a,b -> a.startDate - b.startDate})?.id;

    npId
  }

  @Transient
  RemoteLicenseLink getControllingLicense() {
    RemoteLicenseLink result = null;
    linkedLicenses.each { ll ->
      if ( ll.status.value == 'controlling' ) {
        result = ll;
      }
    }
    return result;
  }
}

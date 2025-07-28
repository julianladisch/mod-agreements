package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionsUnitSubset;
import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineImplementor;
import com.k_int.accesscontrol.core.sql.PolicySubquery;
import com.k_int.accesscontrol.main.PolicyEngineConfiguration;
import com.k_int.folio.FolioCall;
import com.k_int.folio.FolioClientException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

/**
 * Policy engine implementor for acquisition units.
 * <p>
 * Handles policy subquery generation based on acquisition unit restrictions,
 * using FOLIO integration for user acquisition unit lookups.
 * </p>
 */
@Slf4j
public class AcquisitionUnitPolicyEngine implements PolicyEngineImplementor {
  private final AcquisitionsClient acqClient;
  private final AcquisitionUnitPolicyEngineConfiguration config;

  /**
   * Constructs a new AcquisitionUnitPolicyEngineImplementor.
   *
   * @param config the policy engine configuration, including FOLIO client config
   */
  public AcquisitionUnitPolicyEngine(PolicyEngineConfiguration config) {
    AcquisitionUnitPolicyEngineConfiguration acqConfig = config.getAcquisitionUnitPolicyEngineConfiguration();

    this.config = acqConfig;
    this.acqClient = new AcquisitionsClient(acqConfig.getFolioClientConfig());
  }

  /**
   * Handles exceptions thrown by FOLIO client calls, wrapping them in a PolicyEngineException.
   *
   * @param context the context of the operation being performed
   * @param call    the FOLIO client call to execute
   * @param <T>     the type of the result expected from the FOLIO client call
   * @return the result of the FOLIO client call
   * @throws PolicyEngineException if an error occurs during the FOLIO client call
   */
  protected <T> T folioClientExceptionHandler(String context, FolioCall<T> call) {
    try {
      return call.execute();
    } catch (FolioClientException fce) {
      Throwable cause = fce.getCause();
      if (cause != null) {
        throw new PolicyEngineException("FolioClientException thrown while " + context + ": " + fce.getCause().getMessage(), fce);
      }
      throw new PolicyEngineException("FolioClientException thrown while " + context, fce);
    } catch (InterruptedException | IOException exc) {
      Throwable cause = exc.getCause();
      if (cause != null) {
        throw new PolicyEngineException("Something went wrong while " + context + ": " + cause.getMessage(), exc);
      }
      throw new PolicyEngineException("Something went wrong while " + context, exc);
    } catch (Exception exc) {
      Throwable cause = exc.getCause();
      if (cause != null) {
        throw new PolicyEngineException("Unexpected exception thrown while " + context + ": " + cause.getMessage(), exc);
      }
      throw new PolicyEngineException("Unexpected exception thrown while " + context, exc);
    }
  }

  /**
   * Handles FOLIO login and retrieves the necessary headers for subsequent requests.
   *
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @return an array of headers including the FOLIO access token cookie
   */
  private String[] handleLoginAndGetHeaders(String[] headers) {
    return folioClientExceptionHandler("performing FOLIO login", () -> {
      long beforeLogin = System.nanoTime();
      String[] finalHeaders;
      /* ------------------------------- LOGIN LOGIC ------------------------------- */

      if (config.isExternalFolioLogin()) {
        // Only perform a separate login if configured to
        finalHeaders = acqClient.getFolioAccessTokenCookie(new String[]{ });

      } else {
        finalHeaders = AcquisitionsClient.getFolioHeaders(headers);
      }

      /* ------------------------------- END LOGIN LOGIC ------------------------------- */
      long afterLogin = System.nanoTime();
      log.trace("AcquisitionUnitPolicyEngineImplementor login time: {}", Duration.ofNanos(afterLogin - beforeLogin));

      return finalHeaders;
    });
  }

  /**
   * Generates policy subqueries for acquisition unit restrictions.
   *
   * @param headers   the request context headers, used for FOLIO/internal service authentication
   * @param pr        the policy restriction to filter by
   * @param queryType the type of query to generate (boolean or filter)
   * @return a list of {@link PolicySubquery} objects for the given restriction and query type
   * @throws PolicyEngineException if acquisition unit lookup fails or FOLIO client errors occur
   */
  public List<PolicySubquery> getPolicySubqueries(String[] headers, PolicyRestriction pr, AccessPolicyQueryType queryType) {
    String[] finalHeaders = handleLoginAndGetHeaders(headers);

    return folioClientExceptionHandler("fetching Acquisition units", () -> {
      List<PolicySubquery> policySubqueries = new ArrayList<>();

      long beforePolicyLookup = System.nanoTime();
      // Do the acquisition unit logic
      AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);
      // Fetch the acquisition units for the user (MemberRestrictiveUnits and NonMemberRestrictiveUnits);
      UserAcquisitionUnits userAcquisitionUnits = acqClient.getUserAcquisitionUnits(
        finalHeaders,
        acqRestriction,
        Set.of(
          UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE,
          UserAcquisitionsUnitSubset.NON_MEMBER_RESTRICTIVE,
          UserAcquisitionsUnitSubset.NON_RESTRICTIVE
        )
      );

      long afterPolicyLookup = System.nanoTime();
      log.trace("AcquisitionUnitPolicyEngineImplementor policy lookup time: {}", Duration.ofNanos(afterPolicyLookup - beforePolicyLookup));

      /* In theory we could have a separate individual PolicySubquery class for every Restriction,
       * but READ/UPDPATE/DELETE are all the same for Acq Units (with slight tweak when LIST vs SINGLE),
       * and CREATE is simple, so we'll do all the work on one class.
       *
       * If we want to make this more performant we could shortcut in the "CREATE" case since that doesn't need the acquisition unit fetch
       */
      policySubqueries.add(
        AcquisitionUnitPolicySubquery
          .builder()
          .userAcquisitionUnits(userAcquisitionUnits)
          .queryType(queryType)
          .restriction(pr)
          .build()
      );

      return policySubqueries;
    });
  }

  /**
   * Retrieves a list of access policy IDs grouped by their type for the given policy restriction.
   *
   * @param headers the request context headers, used for FOLIO/internal service authentication
   * @param pr      the policy restriction to filter by
   * @return a list of {@link AccessPolicyTypeIds} containing policy IDs grouped by type
   */
  public List<AccessPolicyTypeIds> getPolicyIds(String[] headers, PolicyRestriction pr) {
    String[] finalHeaders = handleLoginAndGetHeaders(headers);

    AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);
    return folioClientExceptionHandler("fetching Acquisition units", () -> {
      List<AccessPolicyTypeIds> policyIds = new ArrayList<>();
      UserAcquisitionUnits userAcquisitionUnits = acqClient.getUserAcquisitionUnits(finalHeaders, acqRestriction, Set.of(UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE, UserAcquisitionsUnitSubset.NON_RESTRICTIVE));

      // Add all the member restrictive unit policy IDs to the list
      policyIds.add(
        AccessPolicyTypeIds
          .builder()
          .type(AccessPolicyType.ACQ_UNIT)
          .policyIds(userAcquisitionUnits.getMemberRestrictiveUnitIds())
          .name(UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE.toString())
          .build()
      );

      // Add all the non-restrictive unit policy IDs to the list
      policyIds.add(
        AccessPolicyTypeIds
          .builder()
          .type(AccessPolicyType.ACQ_UNIT)
          .policyIds(userAcquisitionUnits.getNonRestrictiveUnitIds())
          .name(UserAcquisitionsUnitSubset.NON_RESTRICTIVE.toString())
          .build()
      );

      return policyIds;
    });
  }

  public boolean arePolicyIdsValid(String[] headers, PolicyRestriction pr, List<AccessPolicyTypeIds> policyIds) {
    String[] finalHeaders = handleLoginAndGetHeaders(headers);
    AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);

    return folioClientExceptionHandler("fetching Acquisition units", () -> {
      UserAcquisitionUnits userAcquisitionUnits = acqClient.getUserAcquisitionUnits(finalHeaders, acqRestriction, Set.of(UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE, UserAcquisitionsUnitSubset.NON_RESTRICTIVE));
      // For ACQ_UNITs the policyIds are valid if they're in the member restrictive or non-restrictive units lists
      return policyIds
        .stream()
        .allMatch(pids -> {
          // For all AccessPolicyTypeIds, we grab the policy IDs, then check that they ALL exist in the user's acquisition units
          return pids.getPolicyIds()
            .stream()
            .allMatch(pid -> Stream.concat(
              Optional.ofNullable(userAcquisitionUnits.getMemberRestrictiveUnits()).stream().flatMap(Collection::stream),
              Optional.ofNullable(userAcquisitionUnits.getNonRestrictiveUnits()).stream().flatMap(Collection::stream) // Concatenate both streams to check against both member and non-restrictive units
            )
            .anyMatch(unit -> Objects.equals(unit.getId(), pid))); // If any unit matches the policy ID, then it's valid
        });
    });
  }
}

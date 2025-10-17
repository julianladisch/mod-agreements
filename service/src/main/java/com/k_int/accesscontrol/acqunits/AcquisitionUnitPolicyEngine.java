package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitPolicy;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionsUnitSubset;
import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.core.ExternalPolicy;
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
import java.util.stream.Collectors;
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
   * @return a list of {@link GroupedExternalPolicies} containing policy IDs grouped by type
   */
  public List<GroupedExternalPolicies> getRestrictionPolicies(String[] headers, PolicyRestriction pr) {
    String[] finalHeaders = handleLoginAndGetHeaders(headers);

    AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);
    return folioClientExceptionHandler("fetching Acquisition units", () -> {
      List<GroupedExternalPolicies> policyIds = new ArrayList<>();
      UserAcquisitionUnits userAcquisitionUnits = acqClient.getUserAcquisitionUnits(
        finalHeaders,
        acqRestriction,
        Set.of(
          UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE,
          UserAcquisitionsUnitSubset.NON_MEMBER_NON_RESTRICTIVE,
          UserAcquisitionsUnitSubset.MEMBER_NON_RESTRICTIVE
        )
      );

      // Add all the member restrictive unit policy IDs to the list
      policyIds.add(
        GroupedExternalPolicies
          .builder()
          .type(AccessPolicyType.ACQ_UNIT)
          .policies(userAcquisitionUnits.getMemberRestrictiveUnitPolicies())
          .name(UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE.toString())
          .build()
      );

      // Add all the member non-restrictive unit policy IDs to the list
      policyIds.add(
        GroupedExternalPolicies
          .builder()
          .type(AccessPolicyType.ACQ_UNIT)
          .policies(userAcquisitionUnits.getNonMemberNonRestrictiveUnitPolicies())
          .name(UserAcquisitionsUnitSubset.NON_MEMBER_NON_RESTRICTIVE.toString())
          .build()
      );

      // Add all the member non-restrictive unit policy IDs to the list
      policyIds.add(
        GroupedExternalPolicies
          .builder()
          .type(AccessPolicyType.ACQ_UNIT)
          .policies(userAcquisitionUnits.getMemberNonRestrictiveUnitPolicies())
          .name(UserAcquisitionsUnitSubset.MEMBER_NON_RESTRICTIVE.toString())
          .build()
      );

      return policyIds;
    });
  }

  public boolean arePoliciesValid(String[] headers, PolicyRestriction pr, List<GroupedExternalPolicies> policies) {
    String[] finalHeaders = handleLoginAndGetHeaders(headers);
    AcquisitionUnitRestriction acqRestriction = AcquisitionUnitRestriction.getRestrictionFromPolicyRestriction(pr);

    if (policies.stream().anyMatch(pid -> pid.getType() != AccessPolicyType.ACQ_UNIT)) {
      throw new PolicyEngineException("arePoliciesValid in AcquisitionUnitPolicyEngine is only valid for policyIds of type AccessPolicyType.ACQ_UNIT", PolicyEngineException.INVALID_POLICY_TYPE);
    }

    return folioClientExceptionHandler("fetching Acquisition units", () -> {
      UserAcquisitionUnits userAcquisitionUnits = acqClient.getUserAcquisitionUnits(
        finalHeaders,
        acqRestriction,
        Set.of(
          UserAcquisitionsUnitSubset.MEMBER_RESTRICTIVE,
          UserAcquisitionsUnitSubset.NON_RESTRICTIVE // We don't need to differentiate here since we're not expanding them in the response
        )
      );
      // For ACQ_UNITs the policyIds are valid if they're in the member restrictive or non-restrictive units lists
      return policies
        .stream()
        .allMatch(pids -> {
          // We grab the policy IDs, then check that they ALL exist in the user's acquisition units
          return pids.getPolicies().stream().map(ExternalPolicy::getId).toList()
            .stream()
            .allMatch(pid -> Stream.concat(
              Optional.ofNullable(userAcquisitionUnits.getMemberRestrictiveUnits()).stream().flatMap(Collection::stream),
              Optional.ofNullable(userAcquisitionUnits.getNonRestrictiveUnits()).stream().flatMap(Collection::stream) // Concatenate both streams to check against both member and non-restrictive units
            )
            .anyMatch(unit -> Objects.equals(unit.getId(), pid))); // If any unit matches the policy ID, then it's valid
        });
    });
  }

  public List<GroupedExternalPolicies> enrichPolicies(String[] headers, List<GroupedExternalPolicies> policies) {
    if (policies.stream().anyMatch(pol -> pol.getType() != AccessPolicyType.ACQ_UNIT)) {
      throw new PolicyEngineException("enrichPolicies in AcquisitionUnitPolicyEngine is only valid for GroupedExternalPolicies of type AccessPolicyType.ACQ_UNIT", PolicyEngineException.INVALID_POLICY_TYPE);
    }

    String[] finalHeaders = handleLoginAndGetHeaders(headers);
    // For each AccessPolicy passed in, return an AccessPolicy with the enriched policies information
    // However it'll be more efficient to fetch all of the necessary policy information in one go and then map from it.

    Set<String> policyIds = policies.stream().reduce(
      new HashSet<>(),
      (acc, curr) -> {
        acc.addAll(curr.getPolicies().stream().map(ExternalPolicy::getId).collect(Collectors.toSet()));
        return acc;
      },
      (idSet1, idSet2) -> {
        idSet1.addAll(idSet2);
        return idSet1;
      }
    );

    List<AcquisitionUnitPolicy> acqUnitPolicies = acqClient.getAcquisitionUnitPolicies(finalHeaders, policyIds);
    // We now have the acqUnitPolicies, so we map back to List<GroupedExternalPolicies>
    return policies.stream()
      .map(pol -> {
        // Build new List<Policy> using what we had in pol.getPolicies and acqUnitPolicies
        List<ExternalPolicy> innerPolicies = pol.getPolicies().stream()
          .map(innerPol -> {
            List<AcquisitionUnitPolicy> acqPolCandidates = acqUnitPolicies.stream()
              .filter(acqPol -> Objects.equals(acqPol.getId(), innerPol.getId()))
              .toList();

            // If we can't find one, return innerPol as we found it
            if (acqPolCandidates.isEmpty()) {
              return innerPol;
            }
            // Should only be one candidate... grab first I guess
            return acqPolCandidates.get(0);
          })
          .toList();

        return GroupedExternalPolicies.builder()
          .type(pol.getType())
          .name(pol.getName())
          .policies(innerPolicies)
          .build();
      })
      .toList();
  }
}

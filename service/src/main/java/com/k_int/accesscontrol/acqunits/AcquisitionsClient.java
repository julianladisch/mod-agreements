package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitMembershipResponse;
import com.k_int.accesscontrol.acqunits.responses.AcquisitionUnitResponse;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnitsMetadata;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionsUnitSubset;
import com.k_int.folio.FolioClient;
import com.k_int.folio.FolioClientConfig;
import com.k_int.folio.FolioClientException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Client for interacting with FOLIO's acquisition unit and membership APIs.
 * Provides both synchronous and asynchronous access to key data structures
 * relevant to resource protection (e.g., acquisition unit restrictions).
 */
public class AcquisitionsClient extends FolioClient {
  /**
   * Base path for acquisition unit API endpoints.
   * This is used to construct the full URL for API requests.
   */
  public static final String ACQUISITION_UNIT_PATH = "/acquisitions-units/units";
  /**
   * Path for acquisition unit membership API endpoints.
   * This is used to fetch memberships associated with acquisition units.
   */
  public static final String ACQUISITION_UNIT_MEMBERSHIP_PATH = "/acquisitions-units/memberships";

  /**
   * Constructs an AcquisitionsClient with the specified base URL, tenant, patron ID, user login, and password.
   *
   * @param baseUrl Base URL for the FOLIO instance
   * @param tenant Tenant identifier for the FOLIO instance
   * @param patronId Patron ID for the user
   * @param userLogin User login name
   * @param userPassword User password
   */
  public AcquisitionsClient(String baseUrl, String tenant, String patronId, String userLogin, String userPassword) {
    super(baseUrl, tenant, patronId, userLogin, userPassword);
  }

  /**
   * Constructs an AcquisitionsClient using a FolioClientConfig object.
   * This allows for more flexible configuration options, such as custom headers or timeouts.
   *
   * @param config Configuration object containing base URL, tenant, patron ID, user login, and password
   */
  public AcquisitionsClient(FolioClientConfig config) {
    super(config);
  }

  /**
   * Default limit for acquisition unit queries.
   * This is set to the maximum integer value to allow fetching all records.
   */
  private static final Map<String, String> BASE_LIMIT_PARAM = new HashMap<>() {{
    put("limit", "2147483647");
  }};

  /**
   * Default query parameters for acquisition unit queries.
   * This includes a query to fetch all records sorted by name.
   * It combines with the base limit parameter to ensure all units are fetched.
   */
  private static final Map<String, String> BASE_UNIT_QUERY_PARAMS = combineQueryParams(new HashMap<>() {{
    put("query", "cql.allRecords=1 sortby name");
  }}, BASE_LIMIT_PARAM);


  /**
   * Asynchronously fetches all acquisition unit memberships with default limits.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @return Future with unit membership response
   */
  public CompletableFuture<AcquisitionUnitMembershipResponse> getAsyncAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) {
    return getAsync(
      ACQUISITION_UNIT_MEMBERSHIP_PATH,
      headers,
      combineQueryParams(BASE_LIMIT_PARAM, queryParams),
      AcquisitionUnitMembershipResponse.class
    );
  }

  /**
   * Synchronously fetches all acquisition unit memberships with default limits.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @return AcquisitionUnitMembershipResponse
   * @throws FolioClientException For failed or invalid responses
   */
  public AcquisitionUnitMembershipResponse getAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncAcquisitionUnitMemberships(headers, queryParams));
  }

  /**
   * Asynchronously fetches unit memberships for the current patron.
   *
   * @param headers Request headers
   * @param queryParams Query parameters
   * @return Future with unit membership response
   */
  public CompletableFuture<AcquisitionUnitMembershipResponse> getAsyncUserAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) {
    return getAsync(
      ACQUISITION_UNIT_MEMBERSHIP_PATH,
      headers,
      combineQueryParams(
        combineQueryParams(
          BASE_LIMIT_PARAM,
          new HashMap<>() {{
            put("query", "(userId==" + getPatronId() + ")");
          }}
        ),
        queryParams
      ),
      AcquisitionUnitMembershipResponse.class
    );
  }

  /**
   * Synchronously fetches unit memberships for the current patron by blocking on the async path.
   *
   * @param headers Request headers
   * @param queryParams Query parameters
   * @return Membership response
   * @throws FolioClientException If the request fails or is interrupted
   */
  public AcquisitionUnitMembershipResponse getUserAcquisitionUnitMemberships(String[] headers, Map<String,String> queryParams) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncUserAcquisitionUnitMemberships(headers, queryParams));
  }

  /**
   * Asynchronously fetches acquisition units filtered by a restriction flag.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param restriction Type of restriction (READ, CREATE, etc.)
   * @param restrictBool Whether the restriction is expected to be true or false
   * @return Future with filtered acquisition units
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, AcquisitionUnitRestriction restriction, boolean restrictBool) {
    Map<String, String> restrictionQueryParams;
    // Handle "no restriction" case
    if (restriction == AcquisitionUnitRestriction.NONE) {
      restrictionQueryParams = new HashMap<>();
    } else {
      restrictionQueryParams = new HashMap<>() {{
        put("query", "(" + restriction.getRestrictionAccessor() + "==" + restrictBool + ")");
      }};
    }

    return getAsync(
      ACQUISITION_UNIT_PATH,
      headers,
      combineQueryParams(
        BASE_LIMIT_PARAM,
        combineQueryParams(
          restrictionQueryParams,
          queryParams
        )
      ),
      AcquisitionUnitResponse.class);
  }

  /**
   * Asynchronously fetches all acquisition units with default query params.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @return Future with acquisition unit response
   */
  public CompletableFuture<AcquisitionUnitResponse> getAsyncAcquisitionUnits(String[] headers, Map<String,String> queryParams) {
    return getAsyncRestrictionAcquisitionUnits(headers, combineQueryParams(BASE_UNIT_QUERY_PARAMS, queryParams), AcquisitionUnitRestriction.NONE, false);
  }

  /**
   * Synchronously fetches all acquisition units with default query params.
   *
   * @param headers Headers for the request
   * @param queryParams Extra query parameters
   * @return AcquisitionUnitResponse
   * @throws FolioClientException For failed or invalid responses
   */
  public AcquisitionUnitResponse getAcquisitionUnits(String[] headers, Map<String,String> queryParams) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncAcquisitionUnits(headers, queryParams));
  }

  /**
   * Synchronously fetches acquisition units filtered by restriction flag.
   *
   * @param headers Request headers
   * @param queryParams Additional query parameters
   * @param restriction Type of restriction (READ, CREATE, etc.)
   * @param restrictBool Whether the restriction is expected to be true or false
   * @return Filtered acquisition units
   * @throws FolioClientException If the async path fails
   */
  public AcquisitionUnitResponse getRestrictionAcquisitionUnits(String[] headers, Map<String,String> queryParams, AcquisitionUnitRestriction restriction, boolean restrictBool) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncRestrictionAcquisitionUnits(headers, queryParams, restriction, restrictBool));
  }

  /**
   * Asynchronously fetches user acquisition units based on the specified restriction and subsets.
   * This method fetches the required acquisition units and memberships, returning a UserAcquisitionUnits
   * object containing the results and metadata to ascertain which subsets were fetched.
   *
   * @param headers Request headers
   * @param restriction Restriction type (READ, CREATE, etc.)
   * @param fetchSubsets Subset of user acquisition units to fetch
   * @return CompletableFuture with UserAcquisitionUnits
   */
  public CompletableFuture<UserAcquisitionUnits> getAsyncUserAcquisitionUnits(String[] headers, AcquisitionUnitRestriction restriction, Set<UserAcquisitionsUnitSubset> fetchSubsets) {
    // When called for Restriction.NONE, the nonRestrictiveUnits will be all units, and the memberRestrictiveUnits/nonMemberRestrictiveUnits will comprise all the units the patron is/isn't a member of

    // Construct metadata for the user acquisition units we're about to fetch
    UserAcquisitionUnitsMetadata userAcquisitionUnitsMetadata = new UserAcquisitionUnitsMetadata(fetchSubsets);

    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch memberRestrictive or nonMemberRestrictive units,
     * we will fetch the restrictive units. Otherwise, we will complete the future with null.
     */
    CompletableFuture<AcquisitionUnitResponse> restrictiveUnitsResponse =
      (userAcquisitionUnitsMetadata.isMemberRestrictive() || userAcquisitionUnitsMetadata.isNonMemberRestrictive())
        ? getAsyncRestrictionAcquisitionUnits(headers, Collections.emptyMap(), restriction, true)
        : CompletableFuture.completedFuture(null);

    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch nonRestrictive units,
     * we will fetch them. Otherwise, we will complete the future with null.
     */
    CompletableFuture<AcquisitionUnitResponse> nonRestrictiveUnitsResponse =
      (userAcquisitionUnitsMetadata.isNonRestrictive())
        ? getAsyncRestrictionAcquisitionUnits(headers, Collections.emptyMap(), restriction, false)
        : CompletableFuture.completedFuture(null);

    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch any memberships,
     * we will fetch the acquisition unit memberships. Otherwise, we will complete the future with null.
     */
    CompletableFuture<AcquisitionUnitMembershipResponse> acquisitionUnitMembershipsResponse =
      (userAcquisitionUnitsMetadata.isNonRestrictive() || userAcquisitionUnitsMetadata.isMemberRestrictive() || userAcquisitionUnitsMetadata.isNonMemberRestrictive())
        ?  getAsyncUserAcquisitionUnitMemberships(headers, Collections.emptyMap())
        : CompletableFuture.completedFuture(null);

    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch memberRestrictive units,
     * we will filter the restrictive units response to get only those units where the user is a member.
     * Otherwise, we will complete the future with null.
     */
    CompletableFuture<List<AcquisitionUnit>> memberRestrictiveUnits = userAcquisitionUnitsMetadata.isMemberRestrictive() ?
      restrictiveUnitsResponse.thenCombine(acquisitionUnitMembershipsResponse, (rur, aumr) ->
        rur.getAcquisitionsUnits()
          .stream()
          .filter(au -> aumr.getAcquisitionsUnitMemberships()
            .stream()
            .anyMatch(aum ->
              Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
                Objects.equals(aum.getUserId(), this.getPatronId())
            )
          )
          .toList()
      ) : CompletableFuture.completedFuture(null);

    /*
     * If the userAcquisitionUnitsMetadata indicates that we want to fetch nonMemberRestrictive units,
     * we will filter the restrictive units response to get only those units where the user is not a member.
     * Otherwise, we will complete the future with null.
     */
    CompletableFuture<List<AcquisitionUnit>> nonMemberRestrictiveUnits = userAcquisitionUnitsMetadata.isNonMemberRestrictive() ?
      restrictiveUnitsResponse.thenCombine(acquisitionUnitMembershipsResponse, (nrur, aumr) ->
        nrur.getAcquisitionsUnits()
          .stream()
          .filter(au -> aumr.getAcquisitionsUnitMemberships()
            .stream()
            .noneMatch(aum ->
              Objects.equals(aum.getAcquisitionsUnitId(), au.getId()) &&
                Objects.equals(aum.getUserId(), this.getPatronId())
            )
          )
          .toList()
      ) : CompletableFuture.completedFuture(null);

    /*
     * Combine all the futures and return a UserAcquisitionUnits object containing the results.
     */
    return CompletableFuture.allOf(
        memberRestrictiveUnits,
        nonMemberRestrictiveUnits,
        nonRestrictiveUnitsResponse
      )
      .thenApply(ignoredVoid -> UserAcquisitionUnits
        .builder()
        .memberRestrictiveUnits(memberRestrictiveUnits.join())
        .nonMemberRestrictiveUnits(nonMemberRestrictiveUnits.join())
        // If we didn't fetch non-restrictive units, this will be null, so we should handle that gracefully.
        .nonRestrictiveUnits(nonRestrictiveUnitsResponse.join() != null ? nonRestrictiveUnitsResponse.join().getAcquisitionsUnits() : null)
        .userAcquisitionUnitsMetadata(userAcquisitionUnitsMetadata)
        .build());
  }

  /**
   * Synchronously constructs and returns the 3 acquisition unit lists for access control,
   * using the async version internally.
   *
   * @param headers Request headers
   * @param restriction Restriction type (READ, etc.)
   * @param fetchSubsets Subset of user acquisition units to fetch
   * @return UserAcquisitionUnits
   * @throws FolioClientException If any async call fails
   */
  public UserAcquisitionUnits getUserAcquisitionUnits(String[] headers, AcquisitionUnitRestriction restriction, Set<UserAcquisitionsUnitSubset> fetchSubsets) throws FolioClientException {
    return asyncFolioClientExceptionHelper(() -> getAsyncUserAcquisitionUnits(headers, restriction, fetchSubsets));
  }
}

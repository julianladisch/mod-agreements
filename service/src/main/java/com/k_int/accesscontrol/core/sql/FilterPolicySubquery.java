package com.k_int.accesscontrol.core.sql;

import com.k_int.accesscontrol.core.GroupedExternalPolicies;
import com.k_int.accesscontrol.core.AccessPolicyQueryType;
import com.k_int.accesscontrol.core.http.filters.PoliciesFilter;
import com.k_int.accesscontrol.core.ExternalPolicy;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Implementation of {@link PolicySubquery} that generates SQL subqueries based on a list of {@link PoliciesFilter}.
 * <p>
 * This class constructs SQL subqueries to filter records based on access policy restrictions.
 * Each {@link PoliciesFilter} contains a list of {@link GroupedExternalPolicies} that are ORed together,
 * while the top-level list of {@link PoliciesFilter} is ANDed together in the final SQL.
 * This implementation allows for different query types (LIST or SINGLE), representing an index operation or the fetch
 * of a single record directly filtered by existence of a given policy on that resource.
 * </p>
 */
@Builder
@Slf4j
@SuppressWarnings("javadoc")
public class FilterPolicySubquery implements PolicySubquery {
  /**
   * The type of query being generated, either LIST or SINGLE.
   * This determines how the SQL will be structured.
   * @param queryType The type of query to generate (LIST or SINGLE)
   * @return The type of query being generated (LIST or SINGLE)
   */
  private final AccessPolicyQueryType queryType;

  /** Template for the SQL filter subquery.
   * This template is used to construct the SQL string for filtering based on access policies.
   * Placeholders in the template are replaced with actual table names, column names, and parameters.
   */
  public static final String FILTER_TEMPLATE = """
    (
      EXISTS (
        SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME #ACCESS_POLICY_TABLE_ALIAS
        WHERE
        #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_TYPE_COLUMN_NAME = #THE_TYPE AND
        #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ID_MATCH AND
        #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
        #ACCESS_POLICY_TABLE_ALIAS.#ACCESS_POLICY_ID_COLUMN_NAME IN (#FILTER_UNITS)
        LIMIT 1
      )
    )
  """;

  /** A list of PoliciesFilter objects representing the filters to be applied.
   * Each PoliciesFilter contains a list of GroupedExternalPolicies objects that will be ORed together,
   * while the top-level list of PoliciesFilter objects will be ANDed together in the final SQL.
   * @param policiesFilters A list of PoliciesFilter objects representing the filters to be applied.
   * @return A list of PoliciesFilter objects representing the filters to be applied.
   */
  List<PoliciesFilter> policiesFilters; // Set up the policiesFilters, so we can return the correct shape right out the back of getSql

  /**
   * Generates an SQL subquery based on the provided parameters and the internal policies filters.
   *
   * @param parameters the parameters required to generate the SQL subquery
   * @return an {@link AccessControlSql} object containing the SQL string, parameters, and their types
   */
  public AccessControlSql getSql(PolicySubqueryParameters parameters) {
    // Spin up a list of all SQL parameters;
    List<String> allParameters = new ArrayList<>();
    // Keep track of their types as well.
    List<AccessControlSqlType> allTypes = new ArrayList<>();

    // Handle resourceIdMatch based on query type -- We will only be using this for Index for now but hopefully this will allow it to work for single record queries in future
    final String resourceIdMatch;
    if (queryType == AccessPolicyQueryType.SINGLE) {
      if (parameters.getResourceId() == null) {
        throw new PolicyEngineException("PolicySubqueryParameters for AccessPolicyQueryType.SINGLE must include resourceId", PolicyEngineException.INVALID_QUERY_PARAMETERS);
      }
      resourceIdMatch = "?"; // We will bind an extra parameter for these, using parameters.getResourceId().
    } else {
      resourceIdMatch = parameters.getResourceAlias() + "." + parameters.getResourceIdColumnName();
    }

    // Approach -- we want to AND together each PoliciesFilter. Each of these objects then contains a list of
    // GroupedExternalPolicyList which we want to OR together. So we build the SQL string from the template above for
    // each AccessPolicyType group per PoliciesFilter, ORing them together in the process.

    String filterSql = "(\n" +
      String.join(
        "\n AND \n", // Take each top level PoliciesFilter and AND them together
        IntStream.range(0, policiesFilters.size()) // Use IntStream to map WITH pfIndex -- we'll use to uniquely alias the AccessPolicy table
          .mapToObj(pfIndex -> {
            PoliciesFilter pf = policiesFilters.get(pfIndex);
            return "(\n" +
              String.join(
                "\n OR \n",
                IntStream.range(0, pf.getFilters().size()) // Use IntStream to get GroupedExternalPolicyList index -- we'll use to uniquely alias the AccessPolicy table
                  .mapToObj(apIndex -> {
                    GroupedExternalPolicies ap = pf.getFilters().get(apIndex);

                    if (queryType == AccessPolicyQueryType.SINGLE) {
                      allParameters.add(parameters.getResourceId()); // Add resource id (But only when in a SINGLE query, list is handled by alias above)
                      allTypes.add(AccessControlSqlType.STRING); // Resource id is a string
                    }

                    allParameters.add(parameters.getResourceClass()); // Add resource class
                    allTypes.add(AccessControlSqlType.STRING); // Resource class is a string

                    allParameters.addAll(ap.getPolicies().stream().map(ExternalPolicy::getId).toList()); // Add policy ids
                    allTypes.addAll(Collections.nCopies(ap.getPolicies().size(), AccessControlSqlType.STRING)); // all policy ids are strings

                    return FILTER_TEMPLATE
                      .replaceAll("#ACCESS_POLICY_TABLE_NAME", parameters.getAccessPolicyTableName())
                      .replaceAll("#ACCESS_POLICY_TABLE_ALIAS", "apFilters" + pfIndex + "_" + apIndex) // Unique alias per EXISTS subquery
                      .replaceAll("#ACCESS_POLICY_TYPE_COLUMN_NAME", parameters.getAccessPolicyTypeColumnName())
                      .replaceAll("#THE_TYPE", "'" + ap.getType().toString() + "'")
                      .replaceAll("#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME", parameters.getAccessPolicyResourceClassColumnName())
                      .replaceAll("#RESOURCE_CLASS", "?") // MAPPING RESOURCE CLASS TO A PARAMETER
                      .replaceAll("#RESOURCE_ID_MATCH", resourceIdMatch)
                      .replaceAll("#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME", parameters.getAccessPolicyResourceIdColumnName())
                      .replaceAll("#ACCESS_POLICY_ID_COLUMN_NAME", parameters.getAccessPolicyIdColumnName())
                      .replaceAll("#FILTER_UNITS", String.join(",", Collections.nCopies(ap.getPolicies().size(), "?"))); // MAPPING FILTER UNITS TO PARAMETERS
                  })
                  .toList()
              ) +
              "\n)" ;
          })
          .toList()
      ) + "\n)";

    log.trace("FilterPolicySubquery::getSql : filterSql = {}", filterSql);
    log.trace("FilterPolicySubquery::getSql : parameters = {}", allParameters);
    log.trace("FilterPolicySubquery::getSql : types = {}", allTypes);

    return AccessControlSql.builder()
      .sqlString(filterSql)
      .parameters(allParameters.toArray())
      .types(allTypes.toArray(new AccessControlSqlType[0]))
      .build();
  }
}

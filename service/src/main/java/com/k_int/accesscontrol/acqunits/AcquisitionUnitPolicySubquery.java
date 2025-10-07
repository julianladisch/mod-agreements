package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.core.*;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.core.sql.AccessControlSql;
import com.k_int.accesscontrol.core.sql.AccessControlSqlType;
import com.k_int.accesscontrol.core.sql.PolicySubquery;
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a subquery for acquisition unit policies in the context of access control.
 * <p>
 * This class is used to generate SQL queries that determine access to resources based on
 * user acquisition units and their membership status in restrictive or non-restrictive units.
 * </p>
 */
@Builder
@SuppressWarnings("javadoc")
public class AcquisitionUnitPolicySubquery implements PolicySubquery {
  /**
   * The user acquisition units that this subquery will use to determine access.
   * This should be populated by the PolicyEngine before calling getSql().
   * @param userAcquisitionUnits The user acquisition units to use for this subquery
   */
  private final UserAcquisitionUnits userAcquisitionUnits;
  /**
   * The type of query being generated, either LIST or SINGLE.
   * This determines how the SQL will be structured.
   * @param queryType The type of query to generate (LIST or SINGLE)
   */
  private final AccessPolicyQueryType queryType;
  /**
   * The restriction type for which this subquery is being generated.
   * This should not be CLAIM, as acquisition units do not support that restriction.
   * @param restriction The policy restriction type for this subquery
   */
  private final PolicyRestriction restriction;

    /*
     * Least restrictive wins
     * Policy | Restricts | Member
     * A      | YES       | YES
     * B      | YES       | NO
     * C      | NO        | (irrelevant)
     *
     * Resource | Policies | Can view
     * 1        |          | Yes
     * 2        | A        | Yes
     * 3        | AB       | Yes
     * 4        | C        | Yes
     * 5        | AC       | Yes
     * 6        | B        | No
     * 7        | BC       | Yes
     * 8        | AB       | Yes
     *
     *
     * Strategy -- find all resources where
     * There are no restrictive policies for which user is not a member OR
     * There is at least one restrictive policy for which user IS a member OR
     * There is at least one non-restrictive policy
     */
    static final String SQL_TEMPLATE = """
      (
        NOT EXISTS (
          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME ap1
          WHERE
            ap1.#ACCESS_POLICY_TYPE_COLUMN_NAME = 'ACQ_UNIT' AND
            ap1.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ID_MATCH AND
            ap1.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
            ap1.#ACCESS_POLICY_ID_COLUMN_NAME IN (#NON_MEMBER_RESTRICTIVE_UNITS)
          LIMIT 1
        ) OR EXISTS (
          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME ap2
          WHERE
            ap2.#ACCESS_POLICY_TYPE_COLUMN_NAME = 'ACQ_UNIT' AND
            ap2.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ID_MATCH AND
            ap2.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
            ap2.#ACCESS_POLICY_ID_COLUMN_NAME IN (#MEMBER_RESTRICTIVE_UNITS)
          LIMIT 1
        ) OR EXISTS (
          SELECT 1 FROM #ACCESS_POLICY_TABLE_NAME ap3
          WHERE
            ap3.#ACCESS_POLICY_TYPE_COLUMN_NAME = 'ACQ_UNIT' AND
            ap3.#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME = #RESOURCE_ID_MATCH AND
            ap3.#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME = #RESOURCE_CLASS AND
            ap3.#ACCESS_POLICY_ID_COLUMN_NAME IN (#NON_RESTRICTIVE_UNITS)
          LIMIT 1
        )
      )
    """;


  /**
   * Constructs a SQL WHERE clause to filter resources according to acquisition unit policies.
   * <p>
   * <b>Least restrictive wins:</b> If any non-restrictive policy applies to a resource, access is always allowed,
   * even if restrictive policies also apply. Otherwise, access is allowed if the user is a member of any restrictive policy.
   * If only restrictive policies exist and the user is not a member of any, access is denied.
   * </p>
   * <ul>
   *   <li>If no policies exist for the resource &rarr; allow</li>
   *   <li>If any non-restrictive policy exists &rarr; allow (even if restrictive policies also exist)</li>
   *   <li>If any restrictive policy exists for which the user <b>is</b> a member &rarr; allow</li>
   *   <li>If only restrictive policies exist for which the user is <b>not</b> a member &rarr; deny</li>
   * </ul>
   * <table border="1">
   *   <caption>Policy Table</caption>
   *   <tr>
   *     <th>Policy</th><th>Restricts</th><th>Member</th>
   *   </tr>
   *   <tr>
   *     <td>A</td><td>YES</td><td>YES</td>
   *   </tr>
   *   <tr>
   *     <td>B</td><td>YES</td><td>NO</td>
   *   </tr>
   *   <tr>
   *     <td>C</td><td>NO</td><td>(irrelevant)</td>
   *   </tr>
   * </table>
   * <table border="1">
   *   <caption>Resource Access Table</caption>
   *   <tr>
   *     <th>Resource</th><th>Policies</th><th>Can view</th>
   *   </tr>
   *   <tr>
   *     <td>1</td><td></td><td>Yes</td>
   *   </tr>
   *   <tr>
   *     <td>2</td><td>A</td><td>Yes</td>
   *   </tr>
   *   <tr>
   *     <td>3</td><td>AB</td><td>Yes</td>
   *   </tr>
   *   <tr>
   *     <td>4</td><td>C</td><td>Yes</td>
   *   </tr>
   *   <tr>
   *     <td>5</td><td>AC</td><td>Yes</td>
   *   </tr>
   *   <tr>
   *     <td>6</td><td>B</td><td>No</td>
   *   </tr>
   *   <tr>
   *     <td>7</td><td>BC</td><td>Yes</td>
   *   </tr>
   *   <tr>
   *     <td>8</td><td>AB</td><td>Yes</td>
   *   </tr>
   * </table>
   * @param parameters The parameters required to build the SQL query, including resource ID, class, and table/column names.
   * @return An AccessControlSql object containing the generated SQL string and parameters.
   * @throws PolicyEngineException if the restriction is CLAIM or if required parameters are missing.
   */
  public AccessControlSql getSql(PolicySubqueryParameters parameters) {
    // This shouldn't be possible thanks to PolicyEngine checks
    if (restriction == PolicyRestriction.CLAIM) {
      throw new PolicyEngineException("AcquisitionUnitPolicySubquery::getSql is not valid for PolicyRestriction.CLAIM", PolicyEngineException.INVALID_RESTRICTION);
    }

    // Firstly we can handle the "CREATE" logic, since Acq Units never restricts CREATE
    if (restriction == PolicyRestriction.CREATE) {
      return AccessControlSql.builder()
        .sqlString("1")
        .build();
    }

    // Spin up a list of all SQL parameters;
    List<String> allParameters = new ArrayList<>();
    // Keep track of their types as well.
    List<AccessControlSqlType> allTypes = new ArrayList<>();

    // For any other restriction we can set up our SQL subquery
    List<String> memberRestrictiveUnits = userAcquisitionUnits.getMemberRestrictiveUnitIds();
    List<String> nonMemberRestrictiveUnits = userAcquisitionUnits.getNonMemberRestrictiveUnitIds();
     List<String> nonRestrictiveUnits = userAcquisitionUnits.getNonRestrictiveUnitIds();

    if (memberRestrictiveUnits.isEmpty()) memberRestrictiveUnits = List.of("this-is-a-made-up-impossible-value");
    if (nonMemberRestrictiveUnits.isEmpty()) nonMemberRestrictiveUnits = List.of("this-is-a-made-up-impossible-value");
    if (nonRestrictiveUnits.isEmpty()) nonRestrictiveUnits = List.of("this-is-a-made-up-impossible-value");


    // If getQueryType() == LIST then we need #RESOURCEIDMATCH = {alias}.id (for hibernate), IF TYPE SINGLE THEN #RESOURCEIDMATCH = <UUID of resource>
    String resourceIdMatch = parameters.getResourceAlias() + "." + parameters.getResourceIdColumnName();
    if (queryType == AccessPolicyQueryType.SINGLE) {
      if (parameters.getResourceId() == null) {
        throw new PolicyEngineException("PolicySubqueryParameters for AccessPolicyQueryType.SINGLE must include resourceId", PolicyEngineException.INVALID_QUERY_PARAMETERS);
      }
      resourceIdMatch = "?"; // We will bind an extra parameter for these, using parameters.getResourceId().
    }

    // Fill out the SQL parameters with the units, as well as their types (STRING for all)
    List.of(
      nonMemberRestrictiveUnits,
      memberRestrictiveUnits,
      nonRestrictiveUnits
    ).forEach(units -> {
      // Resource id match
      if (queryType == AccessPolicyQueryType.SINGLE) {
        allParameters.add(parameters.getResourceId());
        allTypes.add(AccessControlSqlType.STRING); // Assuming resourceId is a UUID, we use STRING type.
      }

      // Mapping resource class
      allParameters.add(parameters.getResourceClass());
      allTypes.add(AccessControlSqlType.STRING); // Assuming resourceId is a UUID, we use STRING type.

      allParameters.addAll(units);
      allTypes.addAll(Collections.nCopies(units.size(), AccessControlSqlType.STRING));
    });

    return AccessControlSql.builder()
      .sqlString(SQL_TEMPLATE
        .replaceAll("#ACCESS_POLICY_TABLE_NAME", parameters.getAccessPolicyTableName())
        .replaceAll("#ACCESS_POLICY_TYPE_COLUMN_NAME", parameters.getAccessPolicyTypeColumnName())
        .replaceAll("#ACCESS_POLICY_ID_COLUMN_NAME", parameters.getAccessPolicyIdColumnName())
        .replaceAll("#ACCESS_POLICY_RESOURCE_ID_COLUMN_NAME", parameters.getAccessPolicyResourceIdColumnName())
        .replaceAll("#ACCESS_POLICY_RESOURCE_CLASS_COLUMN_NAME", parameters.getAccessPolicyResourceClassColumnName())
        .replaceAll("#RESOURCE_ID_MATCH", resourceIdMatch)
        .replaceAll("#RESOURCE_CLASS", "?") // Map resource class to a parameter
        // Fill out "?" placeholders, one per id
        .replaceAll("#NON_MEMBER_RESTRICTIVE_UNITS", String.join(",", Collections.nCopies(nonMemberRestrictiveUnits.size(), "?")))
        .replaceAll("#MEMBER_RESTRICTIVE_UNITS", String.join(",", Collections.nCopies(memberRestrictiveUnits.size(), "?")))
        .replaceAll("#NON_RESTRICTIVE_UNITS", String.join(",", Collections.nCopies(nonRestrictiveUnits.size(), "?")))
      )
      .parameters(allParameters.toArray())
      .types(allTypes.toArray(new AccessControlSqlType[0]))
      .build();
  }
}

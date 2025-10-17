package com.k_int.accesscontrol.core.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.k_int.accesscontrol.core.AccessPolicyQueryType;
import com.k_int.accesscontrol.core.AccessPolicyType;
import com.k_int.accesscontrol.core.GroupedExternalPolicies;
import com.k_int.accesscontrol.core.http.filters.PoliciesFilter;
import com.k_int.accesscontrol.core.http.responses.BasicPolicy;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class FilterPolicySubqueryTest {
  // Helper method to create the list of filters
  private List<PoliciesFilter> setupPoliciesFilters() {
    return List.of(
      PoliciesFilter
        .builder()
        .filters(
          List.of(
            GroupedExternalPolicies
              .builder()
              .type(AccessPolicyType.ACQ_UNIT)
              .name("Test group 1 ACQ_UNIT")
              .policies(
                List.of(
                  BasicPolicy.builder().id("acq-1234").build(),
                  BasicPolicy.builder().id("acq-2345").build(),
                  BasicPolicy.builder().id("acq-3456").build()
                )
              )
              .build(),
            GroupedExternalPolicies
              .builder()
              .type(AccessPolicyType.KI_GRANT)
              .name("Test group 1 KI_GRANT")
              .policies(
                List.of(
                  BasicPolicy.builder().id("kig-1234").build(),
                  BasicPolicy.builder().id("kig-2345").build()
                )
              )
              .build()
          )
        )
        .build(),
      PoliciesFilter
        .builder()
        .filters(
          List.of(
            GroupedExternalPolicies
              .builder()
              .type(AccessPolicyType.ACQ_UNIT)
              .name("Test group 2 ACQ_UNIT")
              .policies(
                List.of(
                  BasicPolicy.builder().id("acq-abcd").build(),
                  BasicPolicy.builder().id("acq-defg").build()
                )
              )
              .build()
          )
        )
        .build()
    );
  }

  // Helper method to create standard query parameters
  private PolicySubqueryParameters setupPolicySubqueryParams() {
    return PolicySubqueryParameters
      .builder()
      .accessPolicyTableName("access_policy_table")
      .accessPolicyTypeColumnName("access_policy_type_column")
      .resourceClass("org.olf.TestClass")
      .accessPolicyIdColumnName("access_policy_id_column")
      .accessPolicyResourceClassColumnName("access_policy_resource_class_column")
      .accessPolicyResourceIdColumnName("access_policy_resource_id_column")
      .resourceAlias("res_alias")
      .resourceIdColumnName("resource_id_column")
      .build();
  }

  @Test
  @DisplayName("LIST Query: Generates complex SQL with AND/OR logic using resourceAlias")
  void testListQuery() {
    // GIVEN
    List<PoliciesFilter> policiesFilters = setupPoliciesFilters();

    FilterPolicySubquery fps = FilterPolicySubquery
      .builder()
      .policiesFilters(policiesFilters)
      .queryType(AccessPolicyQueryType.LIST) // LIST branch
      .build();
    PolicySubqueryParameters params = setupPolicySubqueryParams();

    // WHEN
    AccessControlSql sqlObject = fps.getSql(params);

    // THEN
    assertEquals("""
          (
          (
            (
              EXISTS (
                SELECT 1 FROM access_policy_table apFilters0_0
                WHERE
                apFilters0_0.access_policy_type_column = 'ACQ_UNIT' AND
                apFilters0_0.access_policy_resource_id_column = res_alias.resource_id_column AND
                apFilters0_0.access_policy_resource_class_column = ? AND
                apFilters0_0.access_policy_id_column IN (?,?,?)
                LIMIT 1
              )
            )
          
           OR\s
            (
              EXISTS (
                SELECT 1 FROM access_policy_table apFilters0_1
                WHERE
                apFilters0_1.access_policy_type_column = 'KI_GRANT' AND
                apFilters0_1.access_policy_resource_id_column = res_alias.resource_id_column AND
                apFilters0_1.access_policy_resource_class_column = ? AND
                apFilters0_1.access_policy_id_column IN (?,?)
                LIMIT 1
              )
            )
          
          )
           AND\s
          (
            (
              EXISTS (
                SELECT 1 FROM access_policy_table apFilters1_0
                WHERE
                apFilters1_0.access_policy_type_column = 'ACQ_UNIT' AND
                apFilters1_0.access_policy_resource_id_column = res_alias.resource_id_column AND
                apFilters1_0.access_policy_resource_class_column = ? AND
                apFilters1_0.access_policy_id_column IN (?,?)
                LIMIT 1
              )
            )
          
          )
          )""", sqlObject.getSqlString(), "Generated SQL should be as expected for LIST query");

    // Assert the parameters and types
    assertIterableEquals(
      List.of(
        "org.olf.TestClass",
        "acq-1234",
        "acq-2345",
        "acq-3456",
        "org.olf.TestClass",
        "kig-1234",
        "kig-2345",
        "org.olf.TestClass",
        "acq-abcd",
        "acq-defg"
      ),
      Arrays.asList(sqlObject.getParameters()),
      "Parameters should be constructed as expected"
    );

    assertIterableEquals(
      List.of(
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING
      ),
      Arrays.asList(sqlObject.getTypes()),
      "Types should be constructed as expected"
    );
  }

  @Test
  @DisplayName("SINGLE Query: Generates SQL using resource ID parameter and throws exception on missing ID")
  void testSingleQuery_Success() {
    // GIVEN
    List<PoliciesFilter> policiesFilters = setupPoliciesFilters(); // Use simplified filters
    String expectedResourceId = "test-resource-id-101";

    FilterPolicySubquery fps = FilterPolicySubquery
      .builder()
      .policiesFilters(policiesFilters)
      .queryType(AccessPolicyQueryType.SINGLE) // SINGLE branch
      .build();

    // We MUST set resourceId for a SINGLE query
    PolicySubqueryParameters params = setupPolicySubqueryParams();
    params.setResourceId(expectedResourceId);

    // WHEN
    AccessControlSql sqlObject = fps.getSql(params);

    // THEN
    // Expected SQL: resourceIdMatch will be '?' and the resource ID will be the first bound parameter.
    assertEquals("""
      (
      (
        (
          EXISTS (
            SELECT 1 FROM access_policy_table apFilters0_0
            WHERE
            apFilters0_0.access_policy_type_column = 'ACQ_UNIT' AND
            apFilters0_0.access_policy_resource_id_column = ? AND
            apFilters0_0.access_policy_resource_class_column = ? AND
            apFilters0_0.access_policy_id_column IN (?,?,?)
            LIMIT 1
          )
        )
      
       OR\s
        (
          EXISTS (
            SELECT 1 FROM access_policy_table apFilters0_1
            WHERE
            apFilters0_1.access_policy_type_column = 'KI_GRANT' AND
            apFilters0_1.access_policy_resource_id_column = ? AND
            apFilters0_1.access_policy_resource_class_column = ? AND
            apFilters0_1.access_policy_id_column IN (?,?)
            LIMIT 1
          )
        )
      
      )
       AND\s
      (
        (
          EXISTS (
            SELECT 1 FROM access_policy_table apFilters1_0
            WHERE
            apFilters1_0.access_policy_type_column = 'ACQ_UNIT' AND
            apFilters1_0.access_policy_resource_id_column = ? AND
            apFilters1_0.access_policy_resource_class_column = ? AND
            apFilters1_0.access_policy_id_column IN (?,?)
            LIMIT 1
          )
        )
      
      )
      )""",
      sqlObject.getSqlString(),
      "Generated SQL should use '?' for resource ID match"
    );

    assertIterableEquals(
      List.of(
        "test-resource-id-101",
        "org.olf.TestClass",
        "acq-1234",
        "acq-2345",
        "acq-3456",
        "test-resource-id-101",
        "org.olf.TestClass",
        "kig-1234",
        "kig-2345",
        "test-resource-id-101",
        "org.olf.TestClass",
        "acq-abcd",
        "acq-defg"
      ),
      Arrays.asList(sqlObject.getParameters()),
      "Parameters should be constructed as expected"
    );

    assertIterableEquals(
      List.of(
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING,
        AccessControlSqlType.STRING
      ),
      Arrays.asList(sqlObject.getTypes()),
      "Types should be constructed as expected"
    );
  }

  @Test
  @DisplayName("SINGLE Query: Throws PolicyEngineException when resourceId is null")
  void testSingleQuery_MissingResourceId() {
    // GIVEN
    List<PoliciesFilter> policiesFilters = setupPoliciesFilters();

    FilterPolicySubquery fps = FilterPolicySubquery
      .builder()
      .policiesFilters(policiesFilters)
      .queryType(AccessPolicyQueryType.SINGLE) // SINGLE branch
      .build();

    // Parameters without resourceId
    PolicySubqueryParameters params = setupPolicySubqueryParams();

    // WHEN / THEN
    // This is the most direct way to check the PolicyEngineException branch.
    PolicyEngineException thrown = assertThrows(
      PolicyEngineException.class,
      () -> fps.getSql(params),
      "Expected getSql() to throw PolicyEngineException, but it didn't"
    );

    assertEquals(
      PolicyEngineException.INVALID_QUERY_PARAMETERS,
      thrown.getCode(),
      "Exception reason should be INVALID_QUERY_PARAMETERS"
    );
    assertEquals(
      "PolicySubqueryParameters for AccessPolicyQueryType.SINGLE must include resourceId",
      thrown.getMessage(),
      "Exception message should indicate missing resourceId"
    );
  }
}
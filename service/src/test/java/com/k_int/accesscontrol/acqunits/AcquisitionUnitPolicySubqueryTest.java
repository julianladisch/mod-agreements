package com.k_int.accesscontrol.acqunits;

import com.k_int.accesscontrol.acqunits.model.AcquisitionUnit;
import com.k_int.accesscontrol.acqunits.useracquisitionunits.UserAcquisitionUnits;
import com.k_int.accesscontrol.core.AccessPolicyQueryType;
import com.k_int.accesscontrol.core.PolicyRestriction;
import com.k_int.accesscontrol.core.policyengine.PolicyEngineException;
import com.k_int.accesscontrol.core.sql.AccessControlSql;
import com.k_int.accesscontrol.core.sql.PolicySubqueryParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


// WARNING This test case was heavily worked on by AI, so take it with a grain of salt
// That said, getting our coverage up is worth it, and the tests do pass so... so far so good
public class AcquisitionUnitPolicySubqueryTest {
  // Helper method to create UserAcquisitionUnits with sample data
  private UserAcquisitionUnits setupSampleUserAcquisitionUnits() {
    return UserAcquisitionUnits.builder()
      // Policies the user IS a member of AND are restrictive
      .memberRestrictiveUnits(List.of(AcquisitionUnit.builder().id("res-A").build(), AcquisitionUnit.builder().id("res-C").build()))
      // Policies the user is NOT a member of BUT are restrictive
      .nonMemberRestrictiveUnits(List.of(AcquisitionUnit.builder().id("res-B").build()))
      // Policies that are non-restrictive (membership irrelevant)
      .nonRestrictiveUnits(List.of(AcquisitionUnit.builder().id("non-D").build(), AcquisitionUnit.builder().id("non-E").build()))
      .build();
  }

  // Helper method to create standard query parameters
  private PolicySubqueryParameters.PolicySubqueryParametersBuilder standardParamsBuilder() {
    return PolicySubqueryParameters
      .builder()
      .accessPolicyTableName("acq_unit_policy_table")
      .accessPolicyTypeColumnName("ap_type")
      .resourceClass("org.olf.TestResource")
      .accessPolicyIdColumnName("ap_id")
      .accessPolicyResourceClassColumnName("ap_res_class")
      .accessPolicyResourceIdColumnName("ap_res_id")
      .resourceAlias("res_alias")
      .resourceIdColumnName("id_col");
  }

  /**
   * Covers the PolicyRestriction.CLAIM branch (expected to throw exception).
   */
  @Test
  @DisplayName("CLAIM Restriction: Should throw PolicyEngineException")
  void testClaimRestriction_ThrowsException() {
    // GIVEN
    AcquisitionUnitPolicySubquery fps = AcquisitionUnitPolicySubquery
      .builder()
      .userAcquisitionUnits(setupSampleUserAcquisitionUnits())
      .queryType(AccessPolicyQueryType.LIST)
      .restriction(PolicyRestriction.CLAIM) // CLAIM branch
      .build();
    PolicySubqueryParameters params = standardParamsBuilder().build();

    // WHEN / THEN
    PolicyEngineException thrown = assertThrows(
      PolicyEngineException.class,
      () -> fps.getSql(params),
      "Expected getSql() to throw PolicyEngineException for CLAIM"
    );

    assertEquals(
      PolicyEngineException.INVALID_RESTRICTION,
      thrown.getCode(),
      "Exception code should be INVALID_RESTRICTION"
    );
  }

  /**
   * Covers the PolicyRestriction.CREATE branch (expected to return "1").
   */
  @Test
  @DisplayName("CREATE Restriction: Should return SQL string '1'")
  void testCreateRestriction_ReturnsOne() {
    // GIVEN
    AcquisitionUnitPolicySubquery fps = AcquisitionUnitPolicySubquery
      .builder()
      .userAcquisitionUnits(setupSampleUserAcquisitionUnits())
      .queryType(AccessPolicyQueryType.LIST)
      .restriction(PolicyRestriction.CREATE) // CREATE branch
      .build();
    PolicySubqueryParameters params = standardParamsBuilder().build();

    // WHEN
    AccessControlSql sqlObject = fps.getSql(params);

    // THEN
    assertEquals("1", sqlObject.getSqlString(), "SQL string for CREATE should be '1'");
    assertNull(sqlObject.getParameters(), "CREATE query should have no parameters");
  }

  /**
   * Covers the PolicyRestriction.LIST branch (uses resourceAlias.resourceIdColumnName).
   */
  @Test
  @DisplayName("LIST Query: Generates SQL using resource alias and all unit types")
  void testListQuery() {
    // GIVEN
    UserAcquisitionUnits units = setupSampleUserAcquisitionUnits();
    AcquisitionUnitPolicySubquery fps = AcquisitionUnitPolicySubquery
      .builder()
      .userAcquisitionUnits(units)
      .queryType(AccessPolicyQueryType.LIST) // LIST branch
      .restriction(PolicyRestriction.READ)
      .build();
    PolicySubqueryParameters params = standardParamsBuilder().build();

    // WHEN
    AccessControlSql sqlObject = fps.getSql(params);

    // THEN
    // Note: The SQL template uses three EXISTS blocks which are OR'd together.
    // The resource ID match is via the alias: 'res_alias.id_col'.
    assertEquals("""
        (
          NOT EXISTS (
            SELECT 1 FROM acq_unit_policy_table ap1
            WHERE
              ap1.ap_type = 'ACQ_UNIT' AND
              ap1.ap_res_id = res_alias.id_col AND
              ap1.ap_res_class = ? AND
              ap1.ap_id IN (?)
            LIMIT 1
          ) OR EXISTS (
            SELECT 1 FROM acq_unit_policy_table ap2
            WHERE
              ap2.ap_type = 'ACQ_UNIT' AND
              ap2.ap_res_id = res_alias.id_col AND
              ap2.ap_res_class = ? AND
              ap2.ap_id IN (?,?)
            LIMIT 1
          ) OR EXISTS (
            SELECT 1 FROM acq_unit_policy_table ap3
            WHERE
              ap3.ap_type = 'ACQ_UNIT' AND
              ap3.ap_res_id = res_alias.id_col AND
              ap3.ap_res_class = ? AND
              ap3.ap_id IN (?,?)
            LIMIT 1
          )
        )
      """, sqlObject.getSqlString(), "Generated SQL should use resource alias and three EXISTS blocks");

    // The parameters are: [resClass, nonMemberUnits...], [resClass, memberUnits...], [resClass, nonRestrictiveUnits...]
    assertIterableEquals(
      List.of(
        // NOT EXISTS params
        "org.olf.TestResource", // Resource Class 1
        "res-B", // nonMemberRestrictiveUnitId 1 (Only 1 in the sample)
        "org.olf.TestResource", // Resource Class 2
        "res-A", // memberRestrictiveUnitId 1
        "res-C", // memberRestrictiveUnitId 2
        // NON-RESTRICTIVE EXISTS params
        "org.olf.TestResource", // Resource Class 3
        "non-D", // nonRestrictiveUnitId 1
        "non-E"  // nonRestrictiveUnitId 2
      ),
      Arrays.asList(sqlObject.getParameters()),
      "Parameters should match resource class followed by unit IDs for each EXISTS block"
    );

    // Total 3 resource class params + 1 (res-B) + 2 (res-A, res-C) + 2 (non-D, non-E) = 8 parameters
    assertEquals(8, sqlObject.getParameters().length);
    assertEquals(8, sqlObject.getTypes().length);
  }

  /**
   * Covers the AccessPolicyQueryType.SINGLE success case (uses resource ID parameter).
   */
  @Test
  @DisplayName("SINGLE Query: Generates SQL using a bound resource ID parameter")
  void testSingleQuery_Success() {
    // GIVEN
    UserAcquisitionUnits units = setupSampleUserAcquisitionUnits();
    String expectedResourceId = "single-resource-uuid-123";
    AcquisitionUnitPolicySubquery fps = AcquisitionUnitPolicySubquery
      .builder()
      .userAcquisitionUnits(units)
      .queryType(AccessPolicyQueryType.SINGLE) // SINGLE branch
      .restriction(PolicyRestriction.READ)
      .build();

    // Set the required resourceId
    PolicySubqueryParameters params = standardParamsBuilder()
      .resourceId(expectedResourceId)
      .build();

    // WHEN
    AccessControlSql sqlObject = fps.getSql(params);

    // THEN
    // SQL uses '?' for the resource ID match in all three EXISTS blocks
    assertEquals("""
        (
          NOT EXISTS (
            SELECT 1 FROM acq_unit_policy_table ap1
            WHERE
              ap1.ap_type = 'ACQ_UNIT' AND
              ap1.ap_res_id = ? AND
              ap1.ap_res_class = ? AND
              ap1.ap_id IN (?)
            LIMIT 1
          ) OR EXISTS (
            SELECT 1 FROM acq_unit_policy_table ap2
            WHERE
              ap2.ap_type = 'ACQ_UNIT' AND
              ap2.ap_res_id = ? AND
              ap2.ap_res_class = ? AND
              ap2.ap_id IN (?,?)
            LIMIT 1
          ) OR EXISTS (
            SELECT 1 FROM acq_unit_policy_table ap3
            WHERE
              ap3.ap_type = 'ACQ_UNIT' AND
              ap3.ap_res_id = ? AND
              ap3.ap_res_class = ? AND
              ap3.ap_id IN (?,?)
            LIMIT 1
          )
        )
      """, sqlObject.getSqlString(), "SINGLE query SQL should use '?' for resource ID match in all blocks");

    // Parameters should be: [resId, resClass, nonMemberUnits...], [resId, resClass, memberUnits...], [resId, resClass, nonRestrictiveUnits...]
    assertIterableEquals(
      List.of(
        // NOT EXISTS params
        expectedResourceId,      // Resource ID 1 (new param)
        "org.olf.TestResource",  // Resource Class 1
        "res-B",
        // MEMBER EXISTS params
        expectedResourceId,      // Resource ID 2 (new param)
        "org.olf.TestResource",  // Resource Class 2
        "res-A",
        "res-C",
        // NON-RESTRICTIVE EXISTS params
        expectedResourceId,      // Resource ID 3 (new param)
        "org.olf.TestResource",  // Resource Class 3
        "non-D",
        "non-E"
      ),
      Arrays.asList(sqlObject.getParameters()),
      "Parameters should include resource ID at the start of each EXISTS block's parameters"
    );

    // Total 3 resource ID params + 3 resource class params + 1 + 2 + 2 = 11 parameters
    assertEquals(11, sqlObject.getParameters().length);
    assertEquals(11, sqlObject.getTypes().length);
  }

  /**
   * Covers the AccessPolicyQueryType.SINGLE failure case (missing resourceId).
   */
  @Test
  @DisplayName("SINGLE Query: Throws PolicyEngineException when resourceId is null")
  void testSingleQuery_MissingResourceId() {
    // GIVEN
    AcquisitionUnitPolicySubquery fps = AcquisitionUnitPolicySubquery
      .builder()
      .userAcquisitionUnits(setupSampleUserAcquisitionUnits())
      .queryType(AccessPolicyQueryType.SINGLE) // SINGLE branch
      .restriction(PolicyRestriction.UPDATE) // Any non-CREATE/CLAIM restriction works
      .build();

    // Parameters without resourceId (it remains null from the builder)
    PolicySubqueryParameters params = standardParamsBuilder().resourceId(null).build();

    // WHEN / THEN
    PolicyEngineException thrown = assertThrows(
      PolicyEngineException.class,
      () -> fps.getSql(params),
      "Expected getSql() to throw PolicyEngineException"
    );

    assertEquals(
      PolicyEngineException.INVALID_QUERY_PARAMETERS,
      thrown.getCode(),
      "Exception code should be INVALID_QUERY_PARAMETERS"
    );
    assertEquals(
      "PolicySubqueryParameters for AccessPolicyQueryType.SINGLE must include resourceId",
      thrown.getMessage(),
      "Exception message should indicate missing resourceId"
    );
  }
}
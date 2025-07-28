package com.k_int.accesscontrol.core.sql;

/**
 * Interface for generating SQL subqueries based on policy restrictions.
 * <p>
 * This interface defines a method to generate SQL subqueries that can be used
 * to filter records based on access policy restrictions.
 * ASSUMPTION made currently that binding sql parameters will use "?"
 * </p>
 */
public interface PolicySubquery {
  /**
   * Generates an SQL subquery based on the provided parameters.
   *
   * @param parameters the parameters required to generate the SQL subquery
   * @return an {@link AccessControlSql} object containing the SQL string, parameters, and their types
   */
  AccessControlSql getSql(PolicySubqueryParameters parameters);
}

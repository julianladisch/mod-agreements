package com.k_int.accesscontrol.core.sql;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents a SQL query string along with its parameters and their types.
 * <p>
 * This class is used to encapsulate the SQL query, the parameters to be bound to the query,
 * and the types of those parameters. It is typically used in access control scenarios where
 * dynamic SQL queries are generated based on access policies.
 * </p>
 */
@Builder
@Getter
@SuppressWarnings("javadoc")
public class AccessControlSql {
  /**
   * The SQL query string to be executed.
   * This string may contain placeholders for parameters that will be bound at runtime. (assumes ? as the placeholder)
   * @param sqlString The SQL query string to be executed.
   * @return The SQL query string to be executed.
   */
  final String sqlString;
  /**
   * An array of parameters to be bound to the SQL query.
   * These parameters correspond to the placeholders in the SQL string.
   * @param parameters An array of parameters to be bound to the SQL query.
   * @return An array of parameters to be bound to the SQL query.
   */
  final Object[] parameters;
  /**
   * An array of types for the parameters.
   * This is used to specify the SQL types of the parameters, which can be important for
   * correct binding and execution of the SQL query.
   * @param types An array of types for the parameters.
   * @return An array of types for the parameters.
   */
  final AccessControlSqlType[] types;
}

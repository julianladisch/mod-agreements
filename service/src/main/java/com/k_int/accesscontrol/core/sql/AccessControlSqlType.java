package com.k_int.accesscontrol.core.sql;

/** * Enum representing the types of SQL parameters used in access control queries.
 * <p>
 * This enum defines various SQL parameter types that can be used in access control scenarios.
 * Each type corresponds to a specific Java type that can be bound to a SQL query.
 * </p>
 */
public enum AccessControlSqlType {

  /**
   * Represents a SQL parameter type for string values.
   * This is used for parameters that are expected to be of type {@link String} in the SQL query.
   */
  STRING,
  /**
   * Represents a SQL parameter type for integer values.
   * This is used for parameters that are expected to be of type {@link Integer} in the SQL query.
   */
  INTEGER,
  /**
   * Represents a SQL parameter type for boolean values.
   * This is used for parameters that are expected to be of type {@link Boolean} in the SQL query.
   */
  BOOLEAN,

  /**
   * Represents a SQL parameter type for UUID objects
   * This is used for parameters that are expected to be of type {@link java.util.UUID} in the SQL query.
   */
  UUID, // If you're passing java.util.UUID objects

  /**
   * Represents a SQL parameter type for date values.
   * This is used for parameters that are expected to be of type {@link java.sql.Date} in the SQL query.
   */
  DATE, // For java.sql.Date

  /**
   * Represents a SQL parameter type for timestamp values.
   * This is used for parameters that are expected to be of type {@link java.sql.Timestamp} in the SQL query.
   */
  TIMESTAMP, // For java.sql.Timestamp

  /**
   * Represents a SQL parameter type for BigDecimal values.
   * This is used for parameters that are expected to be of type {@link java.math.BigDecimal} in the SQL query.
   */
  BIG_DECIMAL, // For java.math.BigDecimal

  /**
   * Represents a SQL parameter type for byte arrays.
   * This is used for parameters that are expected to be an array of type {@link Byte} in the SQL query.
   */
  BYTE_ARRAY // For byte[]
}

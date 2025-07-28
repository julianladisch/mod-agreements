package com.k_int.accesscontrol.grails.criteria;

import com.k_int.accesscontrol.core.sql.AccessControlSqlType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.Type;
import org.hibernate.type.StandardBasicTypes; // Hibernate 5.x
import org.hibernate.type.spi.TypeConfiguration;

import java.util.HashMap;
import java.util.Map;

// Make sure this class is initialized with a SessionFactoryImplementor
// either directly or by unwrapping from EntityManagerFactory.
public class AccessControlHibernateTypeMapper {

  private final Map<AccessControlSqlType, Type> typeMapping = new HashMap<>();
  private final BasicTypeRegistry basicTypeRegistry;

  public AccessControlHibernateTypeMapper(SessionFactoryImplementor sessionFactory) {
    // Essential to get type information from the actual Hibernate context
    TypeConfiguration typeConfiguration = sessionFactory.getMetamodel().getTypeConfiguration();
    this.basicTypeRegistry = typeConfiguration.getBasicTypeRegistry();
    initializeMappings();
  }

  private void initializeMappings() {
    // Map your AccessControlParameterType enum to Hibernate Type instances
    typeMapping.put(AccessControlSqlType.STRING, basicTypeRegistry.getRegisteredType(StandardBasicTypes.STRING.getName()));
    typeMapping.put(AccessControlSqlType.INTEGER, basicTypeRegistry.getRegisteredType(StandardBasicTypes.INTEGER.getName()));
    typeMapping.put(AccessControlSqlType.BOOLEAN, basicTypeRegistry.getRegisteredType(StandardBasicTypes.BOOLEAN.getName()));
    typeMapping.put(AccessControlSqlType.UUID, basicTypeRegistry.getRegisteredType(StandardBasicTypes.UUID_CHAR.getName())); // "uuid" is the name for UUIDType
    typeMapping.put(AccessControlSqlType.DATE, basicTypeRegistry.getRegisteredType(StandardBasicTypes.DATE.getName()));
    typeMapping.put(AccessControlSqlType.TIMESTAMP, basicTypeRegistry.getRegisteredType(StandardBasicTypes.TIMESTAMP.getName()));
    typeMapping.put(AccessControlSqlType.BIG_DECIMAL, basicTypeRegistry.getRegisteredType(StandardBasicTypes.BIG_DECIMAL.getName()));
    typeMapping.put(AccessControlSqlType.BYTE_ARRAY, basicTypeRegistry.getRegisteredType(StandardBasicTypes.BINARY.getName()));
  }

  /**
   * Get the Hibernate Type for a given AccessControlSqlType.
   * @param paramType An AccessControlSqlType.
   * @return The corresponding Hibernate Type.
   * @throws IllegalArgumentException if no mapping is found for the given type.
   */
  public Type getHibernateType(AccessControlSqlType paramType) {
    Type type = typeMapping.get(paramType);
    if (type == null) {
      // This case should ideally not happen if your enum and map are complete
      throw new IllegalArgumentException("No Hibernate Type mapping found for AccessControlParameterType: " + paramType);
    }
    return type;
  }
}
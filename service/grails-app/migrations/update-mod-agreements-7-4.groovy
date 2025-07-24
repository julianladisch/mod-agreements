databaseChangeLog = {
  // EXAMPLE: Replacing any missing refdataValue values where FK constraints were erroneously not present
  // See: ERM-3765
  changeSet(author: "mchaib (manual)", id: "20250716-1620-001") {
    // create the Pkg.LifecycleStatus category if it doesn't already exist
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_category (rdc_id, rdc_version, rdc_description, internal) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'Pkg.LifecycleStatus' as description, false as internal WHERE NOT EXISTS (SELECT rdc_description FROM ${database.defaultSchemaName}.refdata_category WHERE (rdc_description)=('Pkg.LifecycleStatus') LIMIT 1);".toString())
      }
    }

    // Create the "missingLifecycleStatusRefDataValue" refDataValue for LifecycleStatus category
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_value (rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'missingLifecycleStatusRefDataValue' as value, (SELECT rdc_id FROM  ${database.defaultSchemaName}.refdata_category WHERE rdc_description='Pkg.LifecycleStatus') as owner, 'missingLifecycleStatusRefDataValue' as label WHERE NOT EXISTS (SELECT rdv_id FROM ${database.defaultSchemaName}.refdata_value INNER JOIN ${database.defaultSchemaName}.refdata_category ON refdata_value.rdv_owner = refdata_category.rdc_id WHERE rdc_description='Pkg.LifecycleStatus' AND rdv_value='missingLifecycleStatusRefDataValue' LIMIT 1);".toString())
      }
    }

    /*
    Get the newly created 'missingLifecycleStatusRefDataValue' ID from the refDataValue table and set the pkg_lifecycle_status_fk
    column to that ID where the value currently in the pkg_lifecycle_status_fk DOES NOT CURRENTLY EXIST in the refDataValue table (where block below).
    */
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.package
          SET
            pkg_lifecycle_status_fk = (
              SELECT ${database.defaultSchemaName}.refdata_value.rdv_id
              FROM ${database.defaultSchemaName}.refdata_value
              INNER JOIN ${database.defaultSchemaName}.refdata_category ON ${database.defaultSchemaName}.refdata_value.rdv_owner = ${database.defaultSchemaName}.refdata_category.rdc_id
              WHERE ${database.defaultSchemaName}.refdata_category.rdc_description = 'Pkg.LifecycleStatus'
                AND ${database.defaultSchemaName}.refdata_value.rdv_value = 'missingLifecycleStatusRefDataValue'
              LIMIT 1
            )
          WHERE
            NOT EXISTS (
              SELECT 1
              FROM ${database.defaultSchemaName}.refdata_value
              WHERE ${database.defaultSchemaName}.refdata_value.rdv_id = ${database.defaultSchemaName}.package.pkg_lifecycle_status_fk
            )
        """.toString())
      }
    }

    // Create the foreign key constraints on these columns so when a refDataValue is in use by a package, the refDataValue can't be deleted.
    addForeignKeyConstraint(baseColumnNames: "pkg_lifecycle_status_fk", baseTableName: "package", constraintName: "lifecycle_status_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
  }

  changeSet(author: "mchaib (manual)", id: "20250716-1620-002") {
    // create the Pkg.AvailabilityScope category if it doesn't already exist
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_category (rdc_id, rdc_version, rdc_description, internal) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'Pkg.AvailabilityScope' as description, false as internal WHERE NOT EXISTS (SELECT rdc_description FROM ${database.defaultSchemaName}.refdata_category WHERE (rdc_description)=('Pkg.AvailabilityScope') LIMIT 1);".toString())
      }
    }


    // Create the "missingAvailabilityScopeRefDataValue" refDataValue for AvailabilityScope category
    grailsChange {
      change {
        sql.execute("INSERT INTO ${database.defaultSchemaName}.refdata_value (rdv_id, rdv_version, rdv_value, rdv_owner, rdv_label) SELECT md5(random()::text || clock_timestamp()::text) as id, 0 as version, 'missingAvailabilityScopeRefDataValue' as value, (SELECT rdc_id FROM  ${database.defaultSchemaName}.refdata_category WHERE rdc_description='Pkg.AvailabilityScope') as owner, 'missingAvailabilityScopeRefDataValue' as label WHERE NOT EXISTS (SELECT rdv_id FROM ${database.defaultSchemaName}.refdata_value INNER JOIN ${database.defaultSchemaName}.refdata_category ON refdata_value.rdv_owner = refdata_category.rdc_id WHERE rdc_description='Pkg.AvailabilityScope' AND rdv_value='missingAvailabilityScopeRefDataValue' LIMIT 1);".toString())
      }
    }

    /*
    Get the newly created 'missingAvailabilityScopeRefDataValue' ID from the refDataValue table and set the pkg_availability_scope_fk
    column to that ID where the value currently in the pkg_availability_scope_fk DOES NOT CURRENTLY EXIST in the refDataValue table (where block below).
    */
    grailsChange {
      change {
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.package
          SET
            pkg_availability_scope_fk = (
              SELECT ${database.defaultSchemaName}.refdata_value.rdv_id
              FROM ${database.defaultSchemaName}.refdata_value
              INNER JOIN ${database.defaultSchemaName}.refdata_category ON ${database.defaultSchemaName}.refdata_value.rdv_owner = ${database.defaultSchemaName}.refdata_category.rdc_id
              WHERE ${database.defaultSchemaName}.refdata_category.rdc_description = 'Pkg.AvailabilityScope'
                AND ${database.defaultSchemaName}.refdata_value.rdv_value = 'missingAvailabilityScopeRefDataValue'
              LIMIT 1
            )
          WHERE
            NOT EXISTS (
              SELECT 1
              FROM ${database.defaultSchemaName}.refdata_value
              WHERE ${database.defaultSchemaName}.refdata_value.rdv_id = ${database.defaultSchemaName}.package.pkg_availability_scope_fk
            )
        """.toString())
      }
    }

    // Create the foreign key constraints on these columns so when a refDataValue is in use by a package, the refDataValue can't be deleted.
    addForeignKeyConstraint(baseColumnNames: "pkg_availability_scope_fk", baseTableName: "package", constraintName: "availability_scope_to_rdv_fk", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "rdv_id", referencedTableName: "refdata_value")
  }
}
databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "20220311-1009-001") {
    createTable(tableName: "custom_property_local_date") {
      column(name: "id", type: "BIGINT") {
        constraints(nullable: "false")
      }

      column(name: "value", type: "TIMESTAMP") {
        constraints(nullable: "false")
      }
    }
  }
  
  changeSet(author: "efreestone (manual)", id: "20220311-1009-002") {
    addPrimaryKey(columnNames: "id", constraintName: "custom_property_local_datePK", tableName: "custom_property_local_date")
  }

  changeSet(author: "efreestone (manual)", id: "20220311-1416-001") {
    addColumn(tableName: "custom_property_definition") {
      column(name: "pd_retired", type: "BOOLEAN")
    }
    addNotNullConstraint (tableName: "custom_property_definition", columnName: "pd_retired", defaultNullValue: 'FALSE')
  }

  changeSet(author: "efreestone (manual)", id: "20220311-1416-002") {
    createIndex(indexName: "td_retired_idx", tableName: "custom_property_definition") {
      column(name: "pd_retired")
    }
  }


  // Create io_res_fk field on identifierOccurrence
  changeSet(author: "efreestone (manual)", id: "20220420-1631-001") {
    addColumn(tableName: "identifier_occurrence") {
      column(name: "io_res_fk", type: "VARCHAR(36)")
    }
    addForeignKeyConstraint(
      baseColumnNames: "io_res_fk",
      baseTableName: "identifier_occurrence",
      constraintName: "identifier_occurrence_resource_FK",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "id",
      referencedTableName: "erm_resource"
    )
  }

  // Update IdentifierOccurrences to move TI foreign key to res foreign key
  changeSet(author: "efreestone (manual)", id: "20220420-1631-002") {
    grailsChange {
      change {
        // First pre-populate all the referenced ids. 
        sql.execute("""
          UPDATE ${database.defaultSchemaName}.identifier_occurrence
          SET io_res_fk = io_ti_fk
        """.toString())
      }
    }
  }

  // Update IdentifierOccurrences to move TI foreign key to res foreign key
  changeSet(author: "efreestone (manual)", id: "20220420-1631-003") {
    dropColumn(columnName: "io_ti_fk", tableName: "identifier_occurrence")
  }
}
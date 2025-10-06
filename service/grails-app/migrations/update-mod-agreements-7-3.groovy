databaseChangeLog = {
  // Resource Deletion Job changeset
  changeSet(author: "mchaib (manual)", id: "20250624-1307-001") {
    createTable(tableName: "resource_deletion_job") {
      column(name: "id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "resource_inputs", type: "TEXT")
      column(name: "deletion_job_type", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
    }

    addPrimaryKey(
      columnNames: "id",
      constraintName: "resource_deletion_jobPK",
      tableName: "resource_deletion_job"
    )
  }

  changeSet(author: "mchaib (manual)", id: "20250820-1532-001") {
    addColumn(tableName: "entitlement") {
      column (name: "ent_resource_name", type: "VARCHAR(255)")
    }
  }

  // ExternalEntitlementSyncJob
  changeSet(author: "mchaib (manual)", id: "20250915-1458-001") {
    createTable(tableName: "external_entitlement_sync_job") {
      column(name: "id", type: "VARCHAR(255)") {
        constraints(nullable: "false")
      }
    }
  }

  changeSet(author: "mchaib (manual)", id: "20250915-1458-002") {
    addPrimaryKey(
      columnNames: "id",
      constraintName: "external_entitlement_sync_jobPK",
      tableName: "external_entitlement_sync_job"
    )
  }
}
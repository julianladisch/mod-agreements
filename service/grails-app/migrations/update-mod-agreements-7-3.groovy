databaseChangeLog = {
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

}
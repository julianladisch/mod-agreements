databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "20250204-1444-001") {
    addColumn(tableName: "package") {
      column (name: "pkg_sync_contents_from_source", type: "BOOLEAN")
    }
  }

  changeSet(author: "efreestone (manual)", id: "20250217-1445-001") {
    createTable(tableName: "package_ingress_metadata") {
      column(name: "pim_id", type: "VARCHAR(36)")
      column(name: "pim_version", type: "BIGINT")
      column(name: "pim_resource_fk", type: "VARCHAR(36)")
      column(name: "pim_ingress_type", type: "VARCHAR(64)")
      column(name: "pim_ingress_id", type: "VARCHAR(36)")
      column(name: "pim_ingress_url", type: "VARCHAR(255)")
      column(name: "pim_content_ingress_id", type: "VARCHAR(36)")
      column(name: "pim_content_ingress_url", type: "VARCHAR(255)")
      column(name: "pim_date_created", type: "TIMESTAMP")
      column(name: "pim_last_updated", type: "TIMESTAMP")
    }
    addPrimaryKey(
      columnNames: "pim_id",
      constraintName: "package_ingress_metadataPK",
      tableName: "package_ingress_metadata"
    )

    addForeignKeyConstraint(
      baseColumnNames: "pim_resource_fk",
      baseTableName: "package_ingress_metadata",
      constraintName: "pim_resource_erm_resource_fk",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "id",
      referencedTableName: "erm_resource"
    )
  }
}
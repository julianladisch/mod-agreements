databaseChangeLog = {

  changeSet(author: "efreestone (manual)", id: "202409161452-001") {
    createTable(tableName: "entitlement_document_attachment") {
      column(name: "entitlement_docs_id", type: "VARCHAR(36)") {
        constraints(nullable: "false")
      }
      column(name: "document_attachment_id", type: "VARCHAR(36)")
    }
  }

  changeSet(author: "efreestone (manual)", id: "202409161452-004") {
    addForeignKeyConstraint(
      baseColumnNames: "entitlement_docs_id",
      baseTableName: "entitlement_document_attachment",
      constraintName: "entitlement_docs_entitlement_docs_id_fk",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "ent_id",
      referencedTableName: "entitlement"
    )
  }

  changeSet(author: "efreestone (manual)", id: "202409161452-005") {
    addForeignKeyConstraint(
      baseColumnNames: "document_attachment_id",
      baseTableName: "entitlement_document_attachment",
      constraintName: "entitlement_docs_document_attachment_id_fk",
      deferrable: "false",
      initiallyDeferred: "false",
      referencedColumnNames: "da_id",
      referencedTableName: "document_attachment"
    )
  }
}
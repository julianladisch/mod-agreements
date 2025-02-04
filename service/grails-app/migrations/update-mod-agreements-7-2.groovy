databaseChangeLog = {
  changeSet(author: "efreestone (manual)", id: "20250204-1444-001") {
    addColumn(tableName: "package") {
      column (name: "pkg_sync_contents_from_source", type: "BOOLEAN")
    }
  }
}
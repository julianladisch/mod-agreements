databaseChangeLog = {
  /* GIN Indexes have been added in update-mod-agreements-6-0.groovy
   * These can cause issues when the underlying data passes a certain limit
   * due to trigram operator not being used and the postgres token size growing
   *
   * It is a little unorthodox, however these index creations are conditional on
   * "CREATE IF NOT EXISTS". We will make use of that to insert indices on those
   * fields _before_ they are set up by upgrading/new implementors. For any tenants
   * who already managed to upgrade, these new changesets will be ignored, but it
   * it _recommended_ that an operational task be undertaken to bring those schemas
   * in line with the indices in place on new instances of mod-agreements
   */

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-poppy-1") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'alternate_resource_name', columnNames: 'arn_name')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX arn_name_idx ON ${database.defaultSchemaName}.alternate_resource_name USING gin (arn_name gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-poppy-2") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'erm_resource', columnNames: 'res_description')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX erm_resource_res_description_idx ON ${database.defaultSchemaName}.erm_resource USING gin (res_description gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-poppy-3") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'identifier', columnNames: 'id_value')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX identifier_id_value_idx ON ${database.defaultSchemaName}.identifier USING gin (id_value gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-poppy-4") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'identifier_namespace', columnNames: 'idns_value')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX identifier_namespace_value_idx ON ${database.defaultSchemaName}.identifier_namespace USING gin (idns_value gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-poppy-5") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'refdata_category', columnNames: 'rdc_description')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX refdata_category_rdc_description_idx ON ${database.defaultSchemaName}.refdata_category USING gin (rdc_description gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }
}
databaseChangeLog = {
  /*
   * See comment in correct-gin-indices-pre-poppy.groovy. Same applies here to
   * pre-empt GIN indices set up in quesnalia upgrade file(s)
   */

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-quesnalia-1") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'entitlement', columnNames: 'ent_description')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX entitlement_description_idx ON ${database.defaultSchemaName}.entitlement USING gin (ent_description gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-quesnalia-2") {
    preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'entitlement', columnNames: 'ent_note')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX entitlement_note_idx ON ${database.defaultSchemaName}.entitlement USING gin (ent_note gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }
  
  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-quesnalia-3") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'entitlement', columnNames: 'ent_reference')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX entitlement_reference_idx ON ${database.defaultSchemaName}.entitlement USING gin (ent_reference gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-quesnalia-4") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'platform', columnNames: 'pt_name')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX platform_name_idx ON ${database.defaultSchemaName}.platform USING gin (pt_name gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }

  changeSet(author: "EFreestone (manual)", id: "2024-09-06-ERM-3321-quesnalia-5") {
		preConditions (onFail: 'MARK_RAN', onError: 'WARN') {
			not {
				indexExists(tableName: 'subscription_agreement', columnNames: 'sa_description')
			}
		}
		// Gin indexes need to be done via scripting.
		grailsChange {
			change {
				def cmd = "CREATE INDEX subscription_agreement_description_idx ON ${database.defaultSchemaName}.subscription_agreement USING gin (sa_description gin_trgm_ops);".toString()
				sql.execute(cmd);
			}
		}
  }
}
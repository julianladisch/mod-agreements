databaseChangeLog = {
  // From 7-2
  changeSet(author: "mchaib (manual)", id: "20250627-1424-001") {
    preConditions(onFail: 'MARK_RAN') {
      // The precondition checks if a primary key does not exist on the table.
      // The changeSet will only run if this is true.
      not {
        primaryKeyExists(tableName: 'package_trigger_resync_job')
      }
    }

    addPrimaryKey(
      columnNames: "id",
      constraintName: "package_trigger_resync_jobPK",
      tableName: "package_trigger_resync_job"
    )
  }

  //7-1
  changeSet(author: "mchaib (manual)", id: "20250627-1424-018") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'entitlement_document_attachment') }
    }
    addPrimaryKey(tableName: "entitlement_document_attachment", columnNames: "entitlement_docs_id", constraintName: "entitlement_document_attachmentPK")
  }

  // From 5-5
  changeSet(author: "mchaib (manual)", id: "20250627-1424-002") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'push_kb_session')
      }
    }

    addPrimaryKey(
      columnNames: "pkbs_id",
      constraintName: "push_kb_sessionPK",
      tableName: "push_kb_session"
    )
  }

  // From 5-5
  changeSet(author: "mchaib (manual)", id: "20250627-1424-003") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'push_kb_chunk')
      }
    }

    addPrimaryKey(
      columnNames: "pkbc_id",
      constraintName: "push_kb_chunkPK",
      tableName: "push_kb_chunk"
    )
  }

  // From 5-3
  changeSet(author: "mchaib (manual)", id: "20250627-1424-004") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'availability_constraint')
      }
    }

    addPrimaryKey(
      columnNames: "avc_id",
      constraintName: "availability_constraintPK",
      tableName: "availability_constraint"
    )
  }

  // From 5-2
  changeSet(author: "mchaib (manual)", id: "20250627-1424-005") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'alternate_resource_name')
      }
    }

    addPrimaryKey(
      columnNames: "arn_id",
      constraintName: "alternate_resource_namePK",
      tableName: "alternate_resource_name"
    )
  }

  changeSet(author: "mchaib (manual)", id: "20250627-1424-006") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'content_type')
      }
    }

    addPrimaryKey(
      columnNames: "ct_id",
      constraintName: "content_typePK",
      tableName: "content_type"
    )
  }

  changeSet(author: "mchaib (manual)", id: "20250627-1424-007") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'package_description_url')
      }
    }

    addPrimaryKey(
      columnNames: "pdu_id",
      constraintName: "package_description_urlPK",
      tableName: "package_description_url"
    )
  }

  // From 5-1
  changeSet(author: "mchaib (manual)", id: "20250627-1424-009") {
    preConditions(onFail: 'MARK_RAN') {
      not {
        primaryKeyExists(tableName: 'resource_rematch_job')
      }
    }

    addPrimaryKey(
      columnNames: "id",
      constraintName: "resource_rematch_jobPK",
      tableName: "resource_rematch_job"
    )
  }

  // Changes above go back to update-mod-agreements-5-0.groovy

  // Remaining tables from other changelogs

  //2-3
  changeSet(author: "mchaib (manual)", id: "20250627-1424-010") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'alternate_name') }
    }
    addPrimaryKey(tableName: "alternate_name", columnNames: "an_id", constraintName: "alternate_namePK")
  }

  //4-0
  changeSet(author: "mchaib (manual)", id: "20250627-1424-011") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'app_setting') }
    }
    addPrimaryKey(tableName: "app_setting", columnNames: "st_id", constraintName: "app_settingPK")
  }

  //2-3
  changeSet(author: "mchaib (manual)", id: "20250627-1424-019") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'entitlement_tag') }
    }
    addPrimaryKey(tableName: "entitlement_tag", columnNames: "entitlement_tags_id", constraintName: "entitlement_tagPK")
  }

  //2-3
  changeSet(author: "mchaib (manual)", id: "20250627-1424-020") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'erm_resource_tag') }
    }
    addPrimaryKey(tableName: "erm_resource_tag", columnNames: "erm_resource_tags_id", constraintName: "erm_resource_tagPK")
  }

  //4-0
  changeSet(author: "mchaib (manual)", id: "20250627-1424-022") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'string_template') }
    }
    addPrimaryKey(tableName: "string_template", columnNames: "strt_id", constraintName: "string_templatePK")
  }

  //5-5
  changeSet(author: "mchaib (manual)", id: "20250627-1424-024") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'subscription_agreement_content_type') }
    }
    addPrimaryKey(tableName: "subscription_agreement_content_type", columnNames: "sact_id", constraintName: "subscription_agreement_content_typePK")
  }

  //docs changelog
  changeSet(author: "mchaib (manual)", id: "20250627-1424-025") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'subscription_agreement_document_attachment') }
    }
    addPrimaryKey(tableName: "subscription_agreement_document_attachment", columnNames: "subscription_agreement_docs_id", constraintName: "subscription_agreement_document_attachmentPK")
  }

  // initial model changelog
  changeSet(author: "mchaib (manual)", id: "20250627-1424-028") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'subscription_agreement_tag') }
    }
    addPrimaryKey(tableName: "subscription_agreement_tag", columnNames: "subscription_agreement_tags_id", constraintName: "subscription_agreement_tagPK")
  }

  //4-0
  changeSet(author: "mchaib (manual)", id: "20250627-1424-029") {
    preConditions(onFail: 'MARK_RAN') {
      not { primaryKeyExists(tableName: 'templated_url') }
    }
    addPrimaryKey(tableName: "templated_url", columnNames: "tu_id", constraintName: "templated_urlPK")
  }
}
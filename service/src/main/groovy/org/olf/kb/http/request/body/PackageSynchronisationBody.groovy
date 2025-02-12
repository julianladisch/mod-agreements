package org.olf.kb.http.request.body;

import grails.validation.Validateable

public class PackageSynchronisationBody implements Validateable {
  public enum SyncState {
    PAUSED,
    SYNCHRONIZING
  }

  SyncState syncState
  List<String> packageIds

  static constraints = {
    syncState nullable: false
    packageIds nullable: false, minSize: 1
  }
}
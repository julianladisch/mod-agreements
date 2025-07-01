package org.olf.kb.http.request.body;

import grails.validation.Validateable

public class DeleteBody implements Validateable {
  List<String> resources

  static constraints = {
    resources nullable: false, minSize: 1
  }

  @Override
  public String toString() {
    return "DeleteBody{ resources=${resources} }"
  }
}
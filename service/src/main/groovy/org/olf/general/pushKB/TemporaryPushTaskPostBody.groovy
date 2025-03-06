package org.olf.general.pushKB

import grails.validation.Validateable

public class TemporaryPushTaskPostBody implements Validateable {
  String pushTaskId;
  String filterContext;

  static constraints = {
    pushTaskId nullable: false
    filterContext nullable: true, blank: false
  }
}

package org.olf.dataimport.erm

import java.time.LocalDate

import org.olf.dataimport.internal.PackageSchema.CoverageStatementSchema
import org.olf.kb.AbstractCoverageStatement

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString

@ToString(includePackage=false)
@GrailsCompileStatic
class CoverageStatement extends AbstractCoverageStatement implements CoverageStatementSchema, Validateable {
  LocalDate startDate
  String startVolume
  String startIssue
  
  LocalDate endDate
  String endVolume
  String endIssue
  
  Long version = 0
  
  static constraints = {
    startDate(nullable:false, validator: STATEMENT_START_VALIDATOR)
    endDate(nullable:true)
    startVolume(nullable:true, blank:false)
    startIssue(nullable:true, blank:false)
    endVolume(nullable:true, blank:false)
    endIssue(nullable:true, blank:false)
  }

  // Reflected by internal CoverageStatement toString...
  String toString() {
    "v${startVolume ?: '*'}/i${startIssue ?: '*'}/${startDate ?: '*'} - v${endVolume ?: '*'}/i${endIssue ?: '*'}/${endDate ?: '*'}"
  }
}
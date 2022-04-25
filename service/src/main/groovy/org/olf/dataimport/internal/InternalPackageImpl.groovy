package org.olf.dataimport.internal

import grails.compiler.GrailsCompileStatic
import org.olf.dataimport.erm.Identifier

import grails.validation.Validateable

@GrailsCompileStatic
class InternalPackageImpl implements PackageSchema, Validateable {
  
  HeaderImpl header
  List<Identifier> identifiers
  List<PackageContentImpl> packageContents = []
  
  static constraints = {
    header            nullable: false
    packageContents   minSize: 1
  }
}



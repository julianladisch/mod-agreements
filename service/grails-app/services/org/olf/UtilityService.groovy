package org.olf

import org.slf4j.MDC

import java.util.regex.Matcher

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.validation.ObjectError

import org.springframework.validation.Errors
import org.grails.datastore.gorm.GormEntity
import grails.validation.Validateable

import grails.util.Holders

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * This service works at the module level, it's often called without a tenant context.
 * --- I think.... see coverage service
 */
@CompileStatic
@Slf4j
class UtilityService {
  private static MessageSource getMessageSource() {
    Holders.grailsApplication.mainContext.getBean('messageSource') as MessageSource
  }

  // Small utility method to log out any errors
  private void logErrors(Errors errors, String extraContext = '') {
    errors.allErrors.each { ObjectError error ->
      log.error "${ messageSource.getMessage(error, LocaleContextHolder.locale) }. ${extraContext}"
    }
  }

  // Small utility method to systematically check binding and log out any errors
  // Provide separate checkValidBinding for Validateable vs GormEntity objects
  public boolean checkValidBinding(GormEntity obj, String extraContext = '') {
    boolean validBinding = false;
    // Check for binding errors.
    if (!obj.errors.hasErrors()) {
      // Validate the actual values now. And check for constraint violations
      obj.validate()
      if (!obj.errors.hasErrors()) {
      
        validBinding = true;
      } else {
        // Log the errors.
        logErrors(obj.errors, extraContext)
      }
    } else {
      // Log the errors.
      logErrors(obj.errors, extraContext)
    }

    validBinding
  }

  // Small utility method to systematically check binding and log out any errors
  // Provide separate checkValidBinding for Validateable vs GormEntity objects
  public boolean checkValidBinding(Validateable obj, String extraContext = '') {
    boolean validBinding = false;
    // Check for binding errors.
    if (!obj.errors.hasErrors()) {
      // Validate the actual values now. And check for constraint violations
      obj.validate()
      if (!obj.errors.hasErrors()) {
      
        validBinding = true;
      } else {
        // Log the errors.
        logErrors(obj.errors, extraContext)
      }
    } else {
      // Log the errors.
      logErrors(obj.errors, extraContext)
    }

    validBinding
  }

  // TODO do we want to allow just "1" as a version?
  // Attempt to match versions of the form "x.y", not full semver
  public Matcher versionMatcher (String version) {
    return version =~ /(?<MAJOR>0|(?:[1-9]\d*))\.(?<MINOR>0|(?:[1-9]\d*))/
  }

  // Will return true if incomingVersion is compatible with comparisonVersion
  public boolean compatibleVersion (String incomingVersion, String comparisonVersion) {

    def incomingMatcher = versionMatcher(incomingVersion)
    def comparisonMatcher = versionMatcher(comparisonVersion)

    def result = false;
    if (incomingMatcher.matches() && comparisonMatcher.matches()) {
      // If both matches succeed we have valid versioning. Else return false
      def incomingMajor = incomingMatcher.group('MAJOR')
      def comparisonMajor = comparisonMatcher.group('MAJOR')

      if (incomingMajor == comparisonMajor) {
        // If majors are equal, continue, else we can discard this as being compatible

        // Should be able to parse these to ints because the regex has already matched them as digits
        def incomingMinor = incomingMatcher.group('MINOR') as Integer
        def comparisonMinor = comparisonMatcher.group('MINOR') as Integer

        if (incomingMinor >= comparisonMinor) {
          result = true;
        }
      }
    } else {
      log.warn("Semver version match error for ${incomingVersion} and/or ${comparisonVersion}")
    }

    return result;
  }
}
package org.olf.tirs

import org.olf.BaseSpec

import spock.lang.Stepwise
import spock.lang.Ignore

import spock.lang.*

import groovy.util.logging.Slf4j

@Slf4j
@Stepwise
abstract class TIRSSpec extends BaseSpec {
  // Place to house any shared TIRS testing methods etc
  @Ignore
  Boolean isWorkSourceTIRS() {
    injectedTIRS() == WORK_SOURCE_TIRS
  }

  @Ignore
  Boolean isIdTIRS() {
    injectedTIRS() == ID_TIRS
  }

  @Ignore
  Boolean isTitleTIRS() {
    injectedTIRS() == TITLE_TIRS
  }
}

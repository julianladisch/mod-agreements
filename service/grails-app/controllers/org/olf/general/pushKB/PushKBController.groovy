package org.olf.general.pushKB

import org.springframework.http.HttpStatus
import java.time.Instant

import org.olf.general.pushKB.PushKBService
import org.olf.general.jobs.JobContext

import org.olf.kb.metadata.ResourceIngressType

import grails.gorm.multitenancy.Tenants
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

@Slf4j
@CurrentTenant
class PushKBController {
  PushKBService pushKBService
  /*
   * Accept a list of packages of the form packageSchema -- but ignore ALL contentItems
   * (those will be handled later)
   * 
   * This will be a synchronous process. It will only return 200 OK when the process has finished
   * At that point the caller can POST to the endpoint again with the next set of packages
   */
  public pushPkg() {
    log.debug("PushKBController::pushPkg")
    String tenantId = ensureTenant()
    final bindObj = request.JSON as Map
    // Handle PushKBSession and PushKBChunk
    handleSessionAndChunk(bindObj, tenantId);
    try {
      Map ingressMetadata = [
          ingressType: ResourceIngressType.PUSHKB,
          'ingressId': bindObj.pushableId,
          'ingressUrl': bindObj.pushKbUrl,
      ]

      Map pushPkgResult = pushKBService.pushPackages(bindObj.records, ingressMetadata)
      if (pushPkgResult.success == false) {
        String messageString = pushPkgResult?.errorMessage ?: 'Something went wrong'
        respond ([message: messageString, statusCode: HttpStatus.INTERNAL_SERVER_ERROR.value(), pushPkgResult: pushPkgResult], status: HttpStatus.INTERNAL_SERVER_ERROR.value())
      } else {
        respond ([message: "pushPkg successful", statusCode: HttpStatus.OK.value(), pushPkgResult: pushPkgResult], status: HttpStatus.OK.value())
      }
    } catch ( Exception e ) {
      log.error("Error: Something went wrong with pushPkg", e);
      respond ([message: "Something went wrong", statusCode: HttpStatus.INTERNAL_SERVER_ERROR.value(), error: e], status: HttpStatus.INTERNAL_SERVER_ERROR.value())
    }

    // Ensure we close chunk process to end logging on this chunk
    endChunk()
  }

  public pushPci() {
    log.debug("PushKBController::pushPci")
    final bindObj = request.JSON as Map

    // Handle PushKBSession and PushKBChunk
    handleSessionAndChunk(bindObj, tenantId);
    Map packageIngressMetadata = [
        ingressType: ResourceIngressType.PUSHKB,
        'contentIngressId': bindObj.pushableId,
        'contentIngressUrl': bindObj.pushKbUrl,
    ]

    // If we want to do metadata on PCI as well at some point we'd set up a separate Map for that.

    try {
      Map pushPCIResult = pushKBService.pushPCIs(bindObj.records, packageIngressMetadata)
      if (pushPCIResult.success == false) {
        String messageString = pushPCIResult?.errorMessage ?: 'Something went wrong'
        respond ([message: messageString, statusCode: HttpStatus.INTERNAL_SERVER_ERROR.value(), pushPCIResult: pushPCIResult], status: HttpStatus.INTERNAL_SERVER_ERROR.value())
      } else {
        respond ([message: "pushPci successful", statusCode: HttpStatus.OK.value(), pushPCIResult: pushPCIResult], status: HttpStatus.OK.value())
      }
    } catch ( Exception e ) {
      log.error("Error: Something went wrong with pushPci", e);
      respond ([message: "Something went wrong", statusCode: HttpStatus.INTERNAL_SERVER_ERROR.value(), error: e], status: HttpStatus.INTERNAL_SERVER_ERROR.value())
    }

    // Ensure we close chunk process to end logging on this chunk
    endChunk()
  }

  private String ensureTenant () throws IllegalStateException {
    final String tenantId = Tenants.currentId()
    
    if (!tenantId) {
      throw new IllegalStateException('Could not determine the tenant ID')
    }
    
    tenantId
  }

  private void handleSessionAndChunk(Map bindObj, String tenantId) {
    PushKBSession session = null;
    PushKBChunk chunk = null;

    // Check for passed sessionId
    if (bindObj.sessionId) {
      session = PushKBSession.findBySessionId(bindObj.sessionId)
      if (!session) {
        session = new PushKBSession([
          sessionId: bindObj.sessionId
        ]).save(flush: true, failOnError: true)
      }
    } else {
      // If no passed sessionId, set up new session with id based on current time
      session = new PushKBSession([
        sessionId: "PushKBSession - ${Instant.now()}"
      ]).save(flush: true, failOnError: true)
    }

    // Then check for passed chunkId
    // Each incoming is a NEW chunk object, but may have the same chunkId if it is repeated
    if (bindObj.chunkId) {
      chunk = new PushKBChunk([
        chunkId: bindObj.chunkId,
      ])
    } else {
      // If no passed chunkId, set up new chunk with id based on current time
      chunk = new PushKBChunk([
        chunkId: "PushKBChunk - ${Instant.now()}"
      ])
    }

    session.addToChunks(chunk)
    session.save(flush: true, failOnError: true)

    // Use the same setup as Jobs to append logs to this new Chunk object
    JobContext.current.set(new JobContext( jobId: chunk.id, tenantId: tenantId ))
  }

  private void endChunk() {
    JobContext.current.remove()
    org.slf4j.MDC.clear()
  }
}


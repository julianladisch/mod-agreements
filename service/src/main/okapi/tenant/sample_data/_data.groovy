log.info "Running default tenant data file"

import org.olf.kb.RemoteKB

// For the generic setup - we configure GOKB_TEST but set ACTIVE=FALSE

/* RemoteKB.findByName('GOKb_TEST') ?: (new RemoteKB(
    name:'GOKb_TEST',
    type:'org.olf.kb.adapters.GOKbOAIAdapter',
    uri:'https://gokbt.gbv.de/gokb/oai/index',
    fullPrefix:'gokb',
    rectype: RemoteKB.RECTYPE_PACKAGE,
    active:Boolean.TRUE,
    supportsHarvesting:true,
    activationEnabled:false,
    //cursor: "2022-08-09T19:34:42Z"
).save(failOnError:true)) */

RemoteKB.findByName('GOKb') ?: (new RemoteKB(
    name:'GOKb',
    type:'org.olf.kb.adapters.GOKbOAIAdapter',
    uri:'https://gokb.org/gokb/oai/index',
    fullPrefix:'gokb',
    rectype: RemoteKB.RECTYPE_PACKAGE,
    active:Boolean.TRUE,
    supportsHarvesting:true,
    activationEnabled:false
).save(failOnError:true))

/* RemoteKB.findByName('DEBUG') ?: (new RemoteKB(
    name:'DEBUG',
    type:'org.olf.kb.adapters.DebugGoKbAdapter',
    // uri can be used to directly force a package from the resources folder
    // uri: 'src/integration-test/resources/DebugGoKbAdapter/borked_ids.xml'
    rectype: RemoteKB.RECTYPE_PACKAGE,
    active:Boolean.TRUE,
    supportsHarvesting:true,
    activationEnabled:false
).save(failOnError:true)) */
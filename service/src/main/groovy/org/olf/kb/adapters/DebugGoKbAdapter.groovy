package org.olf.kb.adapters

import java.text.*

import org.olf.dataimport.internal.PackageSchema
import org.olf.kb.KBCache

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import groovyx.net.http.*

/**
 * A debug adapter to treat some XML file as a GoKB package harvest
 */

@Slf4j
@CompileStatic
public class DebugGoKbAdapter extends GOKbOAIAdapter {
  private static final String XML_FILE_LOCATION = "src/integration-test/resources/DebugGoKbAdapter"

  private String getXMLFile() {
    // Full override path to file eg "src/integration-test/resources/DebugGoKbAdapter/exampleXMLPage.xml"
    String filePath = System.getenv("XML_FILE_PATH");

    if (filePath) {
      return filePath;
    }

    // Split directory/fileName envs
    String fileDirectory = System.getenv("XML_FILE_DIRECTORY");
    String fileName = System.getenv("XML_FILE");

    return "${fileDirectory ?: XML_FILE_LOCATION}/${fileName ?: 'exampleXMLPage.xml'}";
  }

  public void freshenPackageData(final String source_name,
                                 final String base_url,
                                 final String current_cursor,
                                 final KBCache cache,
                                 final boolean trustedSourceTI = false) {

    GPathResult xml

    def pageXml = new XmlSlurper().parse(new File(base_url ?: getXMLFile()))

    if (( pageXml instanceof GPathResult ) ) {
      xml = (GPathResult) pageXml;
      Tuple2<List<Tuple2<PackageSchema, String>>, String> transformedPage = transformPackagePage(xml, cache, trustedSourceTI);

      Map page_result = processPackagePage('', transformedPage, source_name, cache)
    } else {
      log.warn("Not a GPathResult... skipping");
    }
  }

  public void freshenTitleData(String source_name,
                                 String base_url,
                                 String current_cursor,
                                 KBCache cache,
                                 boolean trustedSourceTI = false) {
    throw new RuntimeException("Title data not suported by DebugGoKbAdapter")
  }

  public void freshenHoldingsData(String cursor,
                                  String source_name,
                                  KBCache cache) {
    throw new RuntimeException("Holdings data not suported by DebugGoKbAdapter")
  }
}

package com.k_int.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains utilities relating to json
 *
 * @author Chas
 *
 */
public class Json {
  static final Logger log = LoggerFactory.getLogger(Json.class);

  /**
   * Converts object to a json string
   * @param object The json to be converted
   * @return The string it has turned into
   */
  static public String toJson(Object object) {
    String json = null;

    // Do we have an object
    if (object != null) {
      // We have an object
      try {
        // We have an object, so we need to generate the json as a string
        JsonMapper jsonMapper = (JsonMapper) new JsonMapper().enable(SerializationFeature.INDENT_OUTPUT);
        jsonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        jsonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        json = jsonMapper.writeValueAsString(object);
      } catch (Exception e) {
        log.error("Exception thrown while converting object to json", e);
      }
    }

    // Return the json to the caller
    return(json);
  }
}


package com.codrite.ruleaudit.json;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Factory for converting raw JSON data into Map-based structures suitable for SpEL evaluation.
 * <p>
 * This class ensures that incoming data is parsed into a {@link java.util.LinkedHashMap} 
 * to preserve property order which can be useful for debugging.
 */
public class JsonContextFactory {

    private final ObjectMapper mapper;

    /**
     * Default constructor creating a new ObjectMapper.
     */
    public JsonContextFactory() {
        this.mapper = new ObjectMapper();
    }

    /**
     * Constructor using a shared ObjectMapper instance.
     * @param mapper The Jackson ObjectMapper to use.
     */
    public JsonContextFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Parses a JSON string into a Map root object.
     * 
     * @param json The JSON string to parse.
     * @return A Map containing the parsed JSON data.
     * @throws JsonParseException if the JSON is malformed.
     */
    public Map<String, Object> toRoot(String json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            throw new JsonParseException("Cannot parse event JSON into a Map root", e);
        }
    }

    /**
     * Parses JSON bytes into a Map root object.
     * 
     * @param json The JSON byte array to parse.
     * @return A Map containing the parsed JSON data.
     * @throws JsonParseException if the JSON is malformed.
     */
    public Map<String, Object> toRoot(byte[] json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            throw new JsonParseException("Cannot parse event JSON into a Map root", e);
        }
    }
}

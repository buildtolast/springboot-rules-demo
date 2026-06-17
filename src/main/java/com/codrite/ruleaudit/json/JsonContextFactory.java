package com.codrite.ruleaudit.json;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class JsonContextFactory {

    private final ObjectMapper mapper;

    public JsonContextFactory() {
        this.mapper = new ObjectMapper();
    }

    public JsonContextFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> toRoot(String json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            throw new JsonParseException("Cannot parse event JSON into a Map root", e);
        }
    }

    public Map<String, Object> toRoot(byte[] json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (IOException e) {
            throw new JsonParseException("Cannot parse event JSON into a Map root", e);
        }
    }
}

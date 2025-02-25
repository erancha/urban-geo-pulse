package com.urbangeopulse.utils.serialization;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.TimeZone;

public class JavaSerializer {
    private static ObjectMapper objectMapper  = new ObjectMapper();

    static {
        objectMapper.setTimeZone(TimeZone.getDefault());
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY);
    }

    public static String write(Object a) throws JsonException{
        try {
            return objectMapper.writeValueAsString(a);
        } catch (JsonProcessingException e) {
            throw new JsonException("Failed to parse object " + a + " to a json.The reason:" + e.getMessage());
        }
    }

    public static <T> T read(String json, Class<T>  clazz) throws JsonException{
        try {
            String replacedJson = json.replaceAll("_class", "jsonClass");
            return objectMapper.readValue(replacedJson,clazz);
        }  catch(JsonParseException e) {
            throw new JsonException("Failed to parse json " + json + " to an object.The reason:" + e.getMessage());
        } catch(JsonMappingException e) {
            throw new JsonException("Failed to parse json " + json + " to an object.The reason:" + e.getMessage());
        } catch(JsonProcessingException e) {
            throw new JsonException("Failed to parse json " + json + " to an object.The reason:" + e.getMessage());
        }
    }
}

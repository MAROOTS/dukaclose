package com.dukaclose.api.common;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The two JSON jobs the audit code needs. If your project is on Jackson 2, use com.fasterxml's ObjectMapper instead. */
@Component
@RequiredArgsConstructor
public class JsonText {

    private final JsonMapper mapper;

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readMap(String json) {
        return json == null ? null : mapper.readValue(json, Map.class);
    }
}
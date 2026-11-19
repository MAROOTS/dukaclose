// SnapshotJson.java
package com.dukaclose.api.closing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
class SnapshotJson {

    private final JsonMapper mapper;

    String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    <T> T read(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }
}
package com.urbangeopulse.receiver.controllers;

import java.util.List;
import java.util.Map;

/**
 * response ..
 */
class MyHttpResponse {
    private final String description;
    private final List<Map<String, Object>> rows;

    public MyHttpResponse(String description, List<Map<String, Object>> rows) {
        this.description = description;
        this.rows = rows;
    }

    public String getDescription() {
        return description;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }
}

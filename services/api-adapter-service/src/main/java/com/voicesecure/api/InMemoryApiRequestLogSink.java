package com.voicesecure.api;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryApiRequestLogSink implements ApiRequestLogSink {
    private final List<ApiRequestLogEntry> entries = new ArrayList<>();

    @Override
    public synchronized void record(ApiRequestLogEntry entry) {
        entries.add(entry);
    }

    public synchronized List<ApiRequestLogEntry> entries() {
        return List.copyOf(entries);
    }
}

package com.voicesecure.api;

public interface ApiRequestLogSink {
    void record(ApiRequestLogEntry entry);
}

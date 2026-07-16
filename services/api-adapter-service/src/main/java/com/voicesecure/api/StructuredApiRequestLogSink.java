package com.voicesecure.api;

import java.io.PrintStream;
import java.util.Objects;

public final class StructuredApiRequestLogSink implements ApiRequestLogSink {
    private final PrintStream output;
    public StructuredApiRequestLogSink(PrintStream output) { this.output = Objects.requireNonNull(output); }

    @Override
    public void record(ApiRequestLogEntry entry) {
        output.println("{" +
                "\"type\":\"api_request\"," +
                "\"occurredAt\":" + ApiJson.quote(entry.occurredAt().toString()) + "," +
                "\"principalId\":" + ApiJson.quote(entry.principalId()) + "," +
                "\"traceId\":" + ApiJson.quote(entry.traceId()) + "," +
                "\"method\":" + ApiJson.quote(entry.method()) + "," +
                "\"path\":" + ApiJson.quote(entry.path()) + "," +
                "\"status\":" + entry.status() + "}");
    }
}

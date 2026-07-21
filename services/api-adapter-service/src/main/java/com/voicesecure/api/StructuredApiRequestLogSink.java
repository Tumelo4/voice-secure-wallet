package com.voicesecure.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintStream;
import java.util.Objects;

public final class StructuredApiRequestLogSink implements ApiRequestLogSink {
    private final PrintStream output;
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "PrintStream is an injected process-owned telemetry sink")
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

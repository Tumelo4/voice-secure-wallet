package com.voicesecure.payments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

public final class PaymentLoadProbe {
    private PaymentLoadProbe() { }

    public static void main(String[] args) throws Exception {
        int operations = 10_000;
        long[] latencies = new long[operations];
        PaymentRequest[] requests = new PaymentRequest[operations];
        InMemoryPaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
        PaymentSagaService service = new PaymentSagaService(repository);
        long started = System.nanoTime();
        for (int index = 0; index < operations; index++) {
            long operationStarted = System.nanoTime();
            requests[index] = new PaymentRequest(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    100, "ZAR", "load-probe-" + index);
            service.start(requests[index],
                    new FraudDecision(0.1, AuthPolicy.VOICE_OTP, true, ""));
            latencies[index] = System.nanoTime() - operationStarted;
        }
        long elapsed = System.nanoTime() - started;
        for (PaymentRequest request : requests) {
            if (repository.findBySagaId(request.sagaId()).isEmpty()) {
                throw new AssertionError("load probe lost accepted saga " + request.sagaId());
            }
        }
        PaymentSaga retried = service.start(requests[0], new FraudDecision(0.1, AuthPolicy.VOICE_OTP, true, ""));
        if (!retried.sagaId().equals(requests[0].sagaId())) {
            throw new AssertionError("idempotent retry created a different saga");
        }
        Arrays.sort(latencies);
        double p95Millis = latencies[(int) (operations * 0.95)] / 1_000_000.0;
        double throughput = operations / (elapsed / 1_000_000_000.0);
        String json = "{\"operations\":" + operations + ",\"p95Millis\":"
                + String.format(java.util.Locale.ROOT, "%.3f", p95Millis) + ",\"throughputPerSecond\":"
                + String.format(java.util.Locale.ROOT, "%.1f", throughput)
                + ",\"correctnessVerified\":true}\n";
        Path output = Path.of(args.length == 0 ? "target/performance/payment-load.json" : args[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, json);
        if (p95Millis >= 5.0 || throughput < 1_000.0) {
            throw new AssertionError("payment load objective failed: " + json);
        }
        System.out.print(json);
    }
}

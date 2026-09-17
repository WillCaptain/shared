package org.twelve.aipp.invocation;

import org.twelve.aipp.evidence.PassiveEvidence;
import org.twelve.aipp.identity.AippInvocationIdentityContract;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Sends only an already signed exact request. No authority, redirects, retries or credential inheritance. */
public final class BoundedInvocationSender {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public record Response(int status,HttpHeaders headers,byte[] body) {
        public Response { body=body.clone(); }
        @Override public byte[] body() { return body.clone(); }
        @Override public String toString() { return "InvocationResponse[redacted]"; }
    }
    public static final class Failure extends RuntimeException {
        private final String code;
        public Failure(String code) { super(code); this.code=code; }
        public String code() { return code; }
    }
    public Response send(HttpRequest request,String origin,String operation,byte[] expected,Instant expiresAt,Clock clock) {
        try {
            PassiveEvidence.validateOrigin(origin); URI target=URI.create(origin);
            if(!"https".equals(target.getScheme()) && !("http".equals(target.getScheme()) && Set.of("127.0.0.1","[::1]").contains(target.getHost()))) throw new Failure("recipient_transport_untrusted");
            if(operation==null || !operation.matches("[a-z][a-z0-9_]{0,127}") || expected==null || expected.length>PassiveEvidence.MAX_BODY_BYTES
                    || !URI.create(origin+"/api/tools/"+operation).equals(request.uri()) || !"POST".equals(request.method())) throw new Failure("request_mismatch");
            String proofHeader=AippInvocationIdentityContract.EVIDENCE_HEADER;
            if(request.headers().map().keySet().stream().anyMatch(k -> !Set.of("content-type","accept",proofHeader.toLowerCase(Locale.ROOT)).contains(k.toLowerCase(Locale.ROOT)))
                    || request.headers().allValues(proofHeader).size()!=1 || request.headers().firstValue(proofHeader).orElse("").isBlank()
                    || request.headers().firstValue(proofHeader).orElse("").length()>16384) throw new Failure("request_mismatch");
            Duration timeout=request.timeout().orElseThrow();
            if(timeout.compareTo(Duration.ofSeconds(1))<0 || timeout.compareTo(Duration.ofSeconds(10))>0
                    || expiresAt==null || expiresAt.isBefore(clock.instant().plus(timeout).plusSeconds(1))) throw new Failure("proof_expired");
            byte[] actual=body(request,timeout);
            if(!Arrays.equals(actual,expected)) throw new Failure("request_mismatch");
            if(Thread.currentThread().isInterrupted()) throw new Failure("interrupted");
            if(expiresAt.isBefore(clock.instant().plus(timeout).plusSeconds(1))) throw new Failure("proof_expired");
            // Freeze the validated bytes again; a custom BodyPublisher cannot change bytes between subscriptions.
            var outbound=HttpRequest.newBuilder(request.uri()).timeout(timeout).POST(HttpRequest.BodyPublishers.ofByteArray(expected.clone()));
            request.headers().map().forEach((name,values) -> values.forEach(value -> outbound.header(name,value)));
            var future=http.sendAsync(outbound.build(),info -> new LimitedBody(4096));
            try {
                var result=future.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
                return new Response(result.statusCode(),result.headers(),result.body());
            } catch(InterruptedException e) { future.cancel(true); Thread.currentThread().interrupt(); throw new Failure("interrupted"); }
            catch(ExecutionException e) { future.cancel(true); Throwable cause=e.getCause(); while(cause instanceof CompletionException && cause.getCause()!=null) cause=cause.getCause(); if(cause instanceof Failure f) throw f; throw new Failure("transport_unavailable"); }
            catch(Exception e) { future.cancel(true); throw new Failure("transport_unavailable"); }
        } catch(Failure failure) { throw failure; }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new Failure("interrupted"); }
        catch(Exception invalid) { throw new Failure("request_mismatch"); }
    }
    private static byte[] body(HttpRequest request,Duration timeout) throws Exception {
        var collector=new LimitedBody(PassiveEvidence.MAX_BODY_BYTES);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { collector.onSubscribe(s); }
            public void onNext(ByteBuffer b) { collector.onNext(List.of(b)); }
            public void onError(Throwable t) { collector.onError(t); }
            public void onComplete() { collector.onComplete(); }
        });
        try { return collector.result.get(timeout.toMillis(),TimeUnit.MILLISECONDS); }
        finally { if(!collector.result.isDone() && collector.subscription!=null) collector.subscription.cancel(); }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>(); final ByteArrayOutputStream bytes=new ByteArrayOutputStream(); final int max;
        Flow.Subscription subscription;
        LimitedBody(int max) { this.max=max; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription=s; s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var b:buffers) {
                if(b.remaining()>max-bytes.size()) { subscription.cancel(); result.completeExceptionally(new Failure("response_too_large")); return; }
                byte[] chunk=new byte[b.remaining()]; b.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { result.completeExceptionally(new Failure("transport_unavailable")); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

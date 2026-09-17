package org.twelve.aipp.invocation;

import org.twelve.aipp.evidence.PassiveEvidence;
import org.twelve.aipp.evidence.EvidenceWorkerProtocol;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Small authority messages only. Pinned origin, no redirects, bounded complete-response deadline. */
public final class BoundedAuthorityHttp {
    private final HttpClient http;
    private final String origin;
    private final Duration timeout;
    private final int max;
    public BoundedAuthorityHttp(String origin,Duration timeout,int max) {
        PassiveEvidence.validateOrigin(origin); URI uri=URI.create(origin);
        if(!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme())
                && Set.of("127.0.0.1","[::1]").contains(uri.getHost()))) throw new IllegalArgumentException("authority_requires_https");
        if(timeout==null || timeout.toMillis()<1 || timeout.compareTo(Duration.ofSeconds(10))>0 || max<1 || max>65536)
            throw new IllegalArgumentException("invalid_authority_bounds");
        this.origin=origin; this.timeout=timeout; this.max=max;
        http=HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public record Response(int status,HttpHeaders headers,byte[] body) {
        public Response { body=body.clone(); }
        @Override public byte[] body() { return body.clone(); }
        @Override public String toString() { return "AuthorityResponse[redacted]"; }
    }
    public Response send(String method,String path,Map<String,String> headers,byte[] body) {
        if(path==null || body==null || body.length>max) throw new IllegalArgumentException("invalid_authority_request");
        String[] parts=path.split("\\?",2);
        EvidenceWorkerProtocol.route(method,parts[0],parts.length==2?parts[1]:null);
        var builder=HttpRequest.newBuilder(URI.create(origin+path)).timeout(timeout)
                .header("Content-Type","application/json").header("Accept","application/json");
        headers.forEach(builder::header);
        builder.method(method,HttpRequest.BodyPublishers.ofByteArray(body));
        var pending=http.sendAsync(builder.build(),info -> new LimitedBody(max));
        try {
            var result=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            return new Response(result.statusCode(),result.headers(),result.body());
        } catch(InterruptedException e) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw new SecurityException("authority_unavailable");
        } catch(Exception e) { pending.cancel(true); throw new SecurityException("authority_unavailable"); }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream(); final int max;
        Flow.Subscription subscription;
        LimitedBody(int max) { this.max=max; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription s) { subscription=s; s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var b:buffers) {
                if(b.remaining()>max-bytes.size()) { subscription.cancel(); result.completeExceptionally(new IllegalStateException("authority_body_too_large")); return; }
                byte[] part=new byte[b.remaining()]; b.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable t) { result.completeExceptionally(new SecurityException("authority_unavailable")); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}

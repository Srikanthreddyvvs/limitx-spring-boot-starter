package com.limitx.filter;

import com.limitx.model.RequestContext;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Extracts the attributes needed by {@code RuleResolver} from the raw
 * {@link ServerWebExchange} provided by Spring Cloud Gateway.
 * <p>
 * <b>userId</b> — read from the {@code X-User-Id} request header. In a real
 * system this header is set by an upstream auth filter (e.g. a JWT validation
 * filter that runs before LimitX) after it verifies the token. LimitX itself
 * does not do authentication — it trusts whatever the auth layer puts in the
 * header. If the header is absent the request is treated as anonymous
 * (userId = null) and the USER scope is skipped in rule resolution.
 * <p>
 * <b>IP</b> — resolved in this order:
 * <ol>
 *   <li>{@code X-Forwarded-For} header (first value) — set by load balancers
 *       and reverse proxies; gives the original client IP even when the
 *       gateway itself sits behind an ALB/Nginx.</li>
 *   <li>Remote address from the connection — fallback when there is no proxy.</li>
 * </ol>
 * <b>path</b> — the raw request URI path (e.g. {@code /api/orders}).
 * Query strings are excluded so that {@code /api/orders?page=2} and
 * {@code /api/orders?page=3} both resolve to the same endpoint rule.
 */
public class RequestContextExtractor {

    private static final String USER_ID_HEADER      = "X-User-Id";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /**
     * Builds a {@link RequestContext} from the current exchange.
     * Called once per request at the start of {@code LimitXFilter}.
     */
    public RequestContext extract(ServerWebExchange exchange) {
        return RequestContext.builder()
                .userId(extractUserId(exchange))
                .ip(extractIp(exchange))
                .path(extractPath(exchange))
                .httpMethod(exchange.getRequest().getMethod().name())
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String extractUserId(ServerWebExchange exchange) {
        // Trusts the auth layer to set X-User-Id after token validation
        return exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
    }

    private String extractIp(ServerWebExchange exchange) {
        // Prefer X-Forwarded-For (set by load balancer) over remote address
        String forwarded = exchange.getRequest().getHeaders().getFirst(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated list: "client, proxy1, proxy2"
            // The first value is always the original client IP
            return forwarded.split(",")[0].trim();
        }

        // Fallback to raw remote address (works in local/non-proxied environments)
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getHostString)
                .orElse("unknown");
    }

    private String extractPath(ServerWebExchange exchange) {
        // getRawPath() gives just the path, no query string
        // e.g. /api/orders?page=2  →  /api/orders
        return exchange.getRequest().getURI().getRawPath();
    }
}

package com.limitx.model;

/**
 * The attributes of an incoming request that the {@code RuleResolver} needs
 * to figure out which {@link RateLimitRule} applies and which Redis keys to
 * touch.
 * <p>
 * Built by {@code RequestContextExtractor} from the {@code ServerWebExchange}
 * inside the gateway filter — e.g. {@code userId} typically comes from a JWT
 * claim or an {@code X-User-Id} header set by an upstream auth filter, and
 * {@code ip} comes from the remote address (or {@code X-Forwarded-For}).
 * <p>
 * This is a plain immutable value object; it carries no Spring or Reactor
 * types so it stays trivial to unit test.
 */
public final class RequestContext {

    private final String userId;
    private final String ip;
    private final String path;
    private final String httpMethod;
    private final String group;

    private RequestContext(Builder builder) {
        this.userId = builder.userId;
        this.ip = builder.ip;
        this.path = builder.path;
        this.httpMethod = builder.httpMethod;
        this.group = builder.group;
    }

    /** The authenticated user's ID, or {@code null} for anonymous requests. */
    public String getUserId() {
        return userId;
    }

    /** Client IP address, resolved from {@code X-Forwarded-For} or the remote address. */
    public String getIp() {
        return ip;
    }

    /** Request path, e.g. {@code /api/orders}. */
    public String getPath() {
        return path;
    }

    /** HTTP method, e.g. {@code GET}, {@code POST}. */
    public String getHttpMethod() {
        return httpMethod;
    }

    /**
     * The business domain group this path belongs to (e.g. {@code "ORDERING"},
     * {@code "PAYMENTS"}), or {@code null} if the path isn't mapped to a group.
     * Resolved via {@code limitx.groups.*} configuration.
     */
    public String getGroup() {
        return group;
    }

    @Override
    public String toString() {
        return "RequestContext{" +
                "userId='" + userId + '\'' +
                ", ip='" + ip + '\'' +
                ", path='" + path + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", group='" + group + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String userId;
        private String ip;
        private String path;
        private String httpMethod;
        private String group;

        private Builder() {
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }
}

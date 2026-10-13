package com.liveseat.gateway.filter;

import com.liveseat.common.security.JwtPayload;
import com.liveseat.common.security.JwtSupport;
import com.liveseat.common.security.UserHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    private final ReactiveStringRedisTemplate redisTemplate;
    private final String jwtSecret;

    public AuthenticationFilter(ReactiveStringRedisTemplate redisTemplate,
                                @Value("${app.jwt.secret}") String jwtSecret) {
        this.redisTemplate = redisTemplate;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS || isPublic(path, exchange.getRequest().getMethod())) {
            return chain.filter(cleanIdentityHeaders(exchange));
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange, "请先登录后再操作");
        }

        String token = authorization.substring(7);
        final JwtPayload payload;
        try {
            payload = JwtSupport.parse(jwtSecret, token);
        } catch (Exception exception) {
            return unauthorized(exchange, "登录状态已失效，请重新登录");
        }

        return redisTemplate.hasKey("login:token:" + token)
                .flatMap(exists -> {
                    if (!exists) {
                        return unauthorized(exchange, "会话已退出或过期");
                    }
                    if (isInternalPath(path)) {
                        return forbidden(exchange, "该接口仅允许服务间调用");
                    }
                    var request = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(UserHeaders.USER_ID);
                                headers.remove(UserHeaders.USER_NAME);
                                headers.remove(UserHeaders.USER_ROLE);
                            })
                            .header(UserHeaders.USER_ID, payload.userId().toString())
                            .header(UserHeaders.USER_NAME, payload.username())
                            .header(UserHeaders.USER_ROLE, payload.role())
                            .build();
                    return chain.filter(exchange.mutate().request(request).build());
                });
    }

    private ServerWebExchange cleanIdentityHeaders(ServerWebExchange exchange) {
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(UserHeaders.USER_ID);
            headers.remove(UserHeaders.USER_NAME);
            headers.remove(UserHeaders.USER_ROLE);
        }).build();
        return exchange.mutate().request(request).build();
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.startsWith("/actuator")) {
            return true;
        }
        if (isWechatMockPath(path, method)) return true;
        return method == HttpMethod.GET && (isPublicShowPath(path) || path.matches("/api/seats/session/\\d+(/recommend)?"));
    }

    private boolean isPublicShowPath(String path) {
        return path.equals("/api/shows")
                || path.matches("/api/shows/\\d+")
                || path.matches("/api/shows/sessions/\\d+");
    }

    private boolean isWechatMockPath(String path, HttpMethod method) {
        String prefix = "/api/payments/wechat/mock/";
        if (!path.startsWith(prefix)) return false;
        String suffix = path.substring(prefix.length());
        if (method == HttpMethod.GET) return !suffix.isBlank() && !suffix.contains("/");
        if (method == HttpMethod.POST && suffix.endsWith("/confirm")) {
            String token = suffix.substring(0, suffix.length() - "/confirm".length());
            return !token.isBlank() && !token.contains("/");
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return jsonError(exchange, HttpStatus.UNAUTHORIZED, 401, message);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return jsonError(exchange, HttpStatus.FORBIDDEN, 403, message);
    }

    private Mono<Void> jsonError(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(
                MediaType.parseMediaType("application/json;charset=UTF-8"));
        byte[] bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isInternalPath(String path) {
        return path.startsWith("/api/orders/internal/")
                || path.equals("/api/seats/lock")
                || path.equals("/api/seats/release")
                || path.equals("/api/seats/confirm")
                || path.equals("/api/seats/refund")
                || path.startsWith("/api/payments/internal/");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

package com.liveseat.payment.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.dto.OrderPaymentCommand;
import com.liveseat.common.dto.OrderSummary;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.payment.client.OrderClient;
import com.liveseat.payment.domain.PaymentRow;
import com.liveseat.payment.mapper.PaymentMapper;
import com.liveseat.payment.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@Service
public class WechatMockPaymentService {
    private static final String TOKEN_KEY = "wechat:mock:";
    private static final String ORDER_KEY = "wechat:mock:order:";
    private static final String LOCK_KEY = "wechat:mock:lock:";
    private static final Duration MAX_WAIT = Duration.ofMinutes(5);
    private static final Duration SUCCESS_RETENTION = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final PaymentMapper mapper;
    private final OrderClient orderClient;
    private final PaymentService paymentService;
    private final SecureRandom secureRandom = new SecureRandom();

    public WechatMockPaymentService(StringRedisTemplate redis, ObjectMapper objectMapper,
                                    PaymentMapper mapper, OrderClient orderClient, PaymentService paymentService) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.orderClient = orderClient;
        this.paymentService = paymentService;
    }

    public Map<String, Object> create(Long userId, String orderNo) {
        OrderSummary order = requireData(orderClient.summary(orderNo, userId), "订单服务不可用");
        PaymentRow existing = mapper.findByOrder(orderNo);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            Map<String, Object> result = paymentResult(existing);
            result.put("alreadyPaid", true);
            return result;
        }
        if (existing != null && "PROCESSING".equals(existing.getStatus()) && "ALIPAY".equals(existing.getChannel())) {
            Map<String, Object> alipay = paymentService.syncAlipay(userId, orderNo);
            if ("SUCCESS".equals(alipay.get("status"))) {
                alipay.put("alreadyPaid", true);
                return alipay;
            }
            if ("FAILED".equals(alipay.get("status"))) existing = mapper.findByOrder(orderNo);
            else throw new BusinessException(409, "支付宝支付结果仍在确认中，不能切换到微信支付");
        }
        if (!"PENDING_PAYMENT".equals(order.status())) throw new BusinessException("当前订单不可支付");

        LocalDateTime now = LocalDateTime.now();
        if (order.expireTime() == null || !order.expireTime().isAfter(now)) {
            throw new BusinessException(409, "订单已超时，请重新选座");
        }
        Duration ttl = min(MAX_WAIT, Duration.between(now, order.expireTime()));
        if (ttl.toSeconds() < 2) throw new BusinessException(409, "订单即将超时，请重新选座");

        preparePayment(existing, userId, order);
        invalidatePreviousToken(orderNo);

        String token = nextToken();
        MockPayment state = new MockPayment(token, orderNo, userId, order.totalAmount().toPlainString(),
                "WAITING", now, now.plus(ttl), null, null);
        writeState(state, ttl);
        redis.opsForValue().set(ORDER_KEY + orderNo, token, ttl);

        Map<String, Object> result = publicResult(state);
        result.put("token", token);
        return result;
    }

    public Map<String, Object> status(Long userId, String orderNo) {
        PaymentRow row = mapper.findByOrder(orderNo);
        if (row == null || !row.getUserId().equals(userId) || !"WECHAT".equals(row.getChannel())) {
            throw new BusinessException(404, "微信支付记录不存在");
        }
        Map<String, Object> result = paymentResult(row);
        String token = redis.opsForValue().get(ORDER_KEY + orderNo);
        MockPayment state = token == null ? null : readState(token);
        if (state != null) {
            result.put("mockStatus", state.status());
            result.put("expiresAt", state.expiresAt());
            result.put("failureMessage", state.failureMessage());
        }
        return result;
    }

    public Map<String, Object> detail(String token) {
        MockPayment state = requireState(token);
        if (isExpired(state) && !"SUCCESS".equals(state.status())) {
            throw new BusinessException(410, "支付二维码已过期");
        }
        return publicResult(state);
    }

    public Map<String, Object> confirm(String token) {
        MockPayment state = requireState(token);
        if ("SUCCESS".equals(state.status())) return publicResult(state);
        if (isExpired(state)) throw new BusinessException(410, "支付二维码已过期");

        Boolean locked = redis.opsForValue().setIfAbsent(LOCK_KEY + token, "1", Duration.ofSeconds(15));
        if (!Boolean.TRUE.equals(locked)) {
            MockPayment current = requireState(token);
            if ("SUCCESS".equals(current.status())) return publicResult(current);
            throw new BusinessException(409, "支付正在确认，请稍后查看结果");
        }

        try {
            MockPayment current = requireState(token);
            if ("SUCCESS".equals(current.status())) return publicResult(current);
            MockPayment confirming = current.withStatus("CONFIRMING", null, null);
            writeState(confirming, remainingTtl(confirming));

            ApiResponse<OrderSummary> response = orderClient.confirm(
                    new OrderPaymentCommand(current.userId(), current.orderNo(), "WECHAT"));
            requireData(response, "订单确认失败");
            mapper.markSuccess(current.orderNo(), "WECHAT", null, "MOCK_SUCCESS");

            MockPayment success = confirming.withStatus("SUCCESS", LocalDateTime.now(), null);
            writeState(success, SUCCESS_RETENTION);
            redis.opsForValue().set(ORDER_KEY + current.orderNo(), token, SUCCESS_RETENTION);
            return publicResult(success);
        } catch (RuntimeException exception) {
            MockPayment failed = state.withStatus("FAILED", null, exception.getMessage());
            writeState(failed, remainingTtl(failed));
            throw exception;
        } finally {
            redis.delete(LOCK_KEY + token);
        }
    }

    private void preparePayment(PaymentRow existing, Long userId, OrderSummary order) {
        if (existing == null) {
            PaymentRow row = new PaymentRow();
            row.setPaymentNo(nextPaymentNo());
            row.setOrderNo(order.orderNo());
            row.setUserId(userId);
            row.setAmount(order.totalAmount());
            row.setChannel("WECHAT");
            row.setStatus("PROCESSING");
            mapper.insert(row);
        } else {
            if ("PROCESSING".equals(existing.getStatus()) && "WECHAT".equals(existing.getChannel())) return;
            if (!"FAILED".equals(existing.getStatus()) || mapper.retry(order.orderNo(), "WECHAT") != 1) {
                throw new BusinessException(409, "其他支付正在处理，不能切换到微信支付");
            }
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void expireWechatPayments() {
        for (PaymentRow row : mapper.findProcessing("WECHAT", 50)) {
            String token = redis.opsForValue().get(ORDER_KEY + row.getOrderNo());
            MockPayment state = token == null ? null : readState(token);
            if (state == null || (isExpired(state) && !"SUCCESS".equals(state.status()))) {
                mapper.markFailed(row.getOrderNo(), "WECHAT", "MOCK_EXPIRED");
            }
        }
    }

    private void invalidatePreviousToken(String orderNo) {
        String previous = redis.opsForValue().get(ORDER_KEY + orderNo);
        if (previous != null) redis.delete(TOKEN_KEY + previous);
        redis.delete(ORDER_KEY + orderNo);
    }

    private MockPayment requireState(String token) {
        if (token == null || token.length() < 32) throw new BusinessException(404, "支付二维码不存在");
        MockPayment state = readState(token);
        if (state == null) throw new BusinessException(410, "支付二维码不存在或已过期");
        return state;
    }

    private MockPayment readState(String token) {
        String json = redis.opsForValue().get(TOKEN_KEY + token);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, MockPayment.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "微信模拟支付状态读取失败");
        }
    }

    private void writeState(MockPayment state, Duration ttl) {
        try {
            redis.opsForValue().set(TOKEN_KEY + state.token(), objectMapper.writeValueAsString(state), ttl);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "微信模拟支付状态保存失败");
        }
    }

    private Map<String, Object> publicResult(MockPayment state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchant", "LiveSeat");
        result.put("orderNo", state.orderNo());
        result.put("amount", state.amount());
        result.put("status", state.status());
        result.put("expiresAt", state.expiresAt());
        result.put("paidAt", state.paidAt());
        result.put("failureMessage", state.failureMessage());
        return result;
    }

    private Map<String, Object> paymentResult(PaymentRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentNo", row.getPaymentNo());
        result.put("orderNo", row.getOrderNo());
        result.put("amount", row.getAmount());
        result.put("channel", row.getChannel());
        result.put("status", row.getStatus());
        result.put("paidAt", row.getPaidAt());
        return result;
    }

    private <T> T requireData(ApiResponse<T> response, String fallback) {
        if (response == null || response.code() != 200 || response.data() == null) {
            throw new BusinessException(response == null ? 502 : response.code(),
                    response == null || response.message() == null ? fallback : response.message());
        }
        return response.data();
    }

    private boolean isExpired(MockPayment state) {
        return !state.expiresAt().isAfter(LocalDateTime.now());
    }

    private Duration remainingTtl(MockPayment state) {
        Duration remaining = Duration.between(LocalDateTime.now(), state.expiresAt());
        return remaining.isNegative() || remaining.isZero() ? Duration.ofSeconds(1) : remaining;
    }

    private Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private String nextToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", new Random().nextInt(1_000_000));
    }

    public record MockPayment(String token, String orderNo, Long userId, String amount, String status,
                              LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime paidAt,
                              String failureMessage) {
        MockPayment withStatus(String nextStatus, LocalDateTime nextPaidAt, String nextFailureMessage) {
            return new MockPayment(token, orderNo, userId, amount, nextStatus, createdAt, expiresAt,
                    nextPaidAt, nextFailureMessage);
        }
    }
}

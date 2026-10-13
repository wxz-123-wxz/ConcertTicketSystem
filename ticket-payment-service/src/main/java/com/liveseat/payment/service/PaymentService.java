package com.liveseat.payment.service;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.dto.OrderPaymentCommand;
import com.liveseat.common.dto.OrderSummary;
import com.liveseat.common.dto.PaymentRefundCommand;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.payment.client.OrderClient;
import com.liveseat.payment.domain.PaymentRow;
import com.liveseat.payment.gateway.AlipaySandboxClient;
import com.liveseat.payment.mapper.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentMapper mapper;
    private final OrderClient orderClient;
    private final AlipaySandboxClient alipayClient;

    public PaymentService(PaymentMapper mapper, OrderClient orderClient, AlipaySandboxClient alipayClient) {
        this.mapper = mapper;
        this.orderClient = orderClient;
        this.alipayClient = alipayClient;
    }

    public Map<String, Object> createAlipayPagePay(Long userId, String orderNo) {
        OrderSummary order = requireData(orderClient.summary(orderNo, userId), "订单服务不可用");
        if (!List.of("PENDING_PAYMENT", "PAID").contains(order.status())) throw new BusinessException("当前订单不可支付");
        PaymentRow existing = mapper.findByOrder(orderNo);
        if ("PAID".equals(order.status())) {
            Map<String, Object> result = existing == null ? new LinkedHashMap<>() : toResult(existing);
            result.put("orderNo", orderNo);
            result.put("alreadyPaid", true);
            return result;
        }
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            Map<String, Object> result = toResult(existing);
            result.put("alreadyPaid", true);
            return result;
        }
        if (existing != null && "PROCESSING".equals(existing.getStatus())) {
            if (!"ALIPAY".equals(existing.getChannel())) {
                throw new BusinessException(409, "微信支付正在处理中，不能切换到支付宝");
            }
            try {
                Map<String, Object> reconciled = reconcileAlipay(existing);
                if ("SUCCESS".equals(reconciled.get("status"))) {
                    reconciled.put("alreadyPaid", true);
                    return reconciled;
                }
                existing = mapper.findByOrder(orderNo);
            } catch (BusinessException exception) {
                if (exception.getCode() != 502) throw exception;
                Map<String, Object> pending = toResult(existing);
                pending.put("reconciliationPending", true);
                pending.put("message", "支付宝支付结果确认中，请勿重复支付或切换支付方式");
                return pending;
            }
        }
        if (existing == null) {
            PaymentRow row = new PaymentRow();
            row.setPaymentNo(nextPaymentNo()); row.setOrderNo(orderNo); row.setUserId(userId);
            row.setAmount(order.totalAmount()); row.setChannel("ALIPAY"); row.setStatus("PROCESSING");
            mapper.insert(row);
        } else if ("FAILED".equals(existing.getStatus())) {
            if (mapper.retry(orderNo, "ALIPAY") != 1) throw new BusinessException(409, "支付状态发生变化，请刷新后重试");
        } else if (!"PROCESSING".equals(existing.getStatus())) {
            throw new BusinessException(409, "当前支付记录不可重新支付");
        }
        AlipaySandboxClient.PagePayRequest request = alipayClient.createPagePay(orderNo, order.totalAmount());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gatewayUrl", request.gatewayUrl());
        result.put("fields", request.fields());
        result.put("orderNo", orderNo);
        return result;
    }

    public Map<String, Object> syncAlipay(Long userId, String orderNo) {
        PaymentRow row = mapper.findByOrder(orderNo);
        if (row == null || !row.getUserId().equals(userId)) {
            throw new BusinessException(404, "支付宝支付记录不存在");
        }
        if ("SUCCESS".equals(row.getStatus()) && "ALIPAY".equals(row.getChannel())) return toResult(row);
        return reconcileAlipay(row);
    }

    private Map<String, Object> reconcileAlipay(PaymentRow row) {
        String orderNo = row.getOrderNo();
        AlipaySandboxClient.QueryResult provider = alipayClient.query(orderNo);
        if (provider.paid()) {
            if (!orderNo.equals(provider.outTradeNo()) || provider.totalAmount() == null || row.getAmount().compareTo(provider.totalAmount()) != 0) {
                throw new BusinessException(409, "支付宝交易订单号或金额校验失败");
            }
            requireData(orderClient.confirm(new OrderPaymentCommand(row.getUserId(), orderNo, "ALIPAY")), "订单确认失败");
            mapper.markSuccess(orderNo, "ALIPAY", provider.tradeNo(), provider.tradeStatus());
        } else if ("ALIPAY".equals(row.getChannel())) {
            mapper.recordAlipayState(orderNo, provider.tradeNo(), provider.tradeStatus());
            if (provider.closed()) mapper.markFailed(orderNo, "ALIPAY", provider.tradeStatus());
            if ("TRADE_NOT_EXIST".equals(provider.tradeStatus()) && row.getCreatedAt() != null
                    && row.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(15))) {
                mapper.markFailed(orderNo, "ALIPAY", provider.tradeStatus());
            }
        }
        Map<String, Object> result = toResult(mapper.findByOrder(orderNo));
        result.put("providerStatus", provider.tradeStatus());
        result.put("providerTradeNo", provider.tradeNo());
        result.put("providerMessage", provider.message());
        return result;
    }

    @Scheduled(fixedDelayString = "${alipay.reconcile-interval-ms:10000}", initialDelayString = "${alipay.reconcile-initial-delay-ms:5000}")
    public void reconcilePendingAlipayPayments() {
        for (PaymentRow row : mapper.findProcessing("ALIPAY", 50)) {
            try {
                reconcileAlipay(row);
            } catch (BusinessException exception) {
                log.warn("支付宝订单 {} 对账暂未完成: {}", row.getOrderNo(), exception.getMessage());
            } catch (RuntimeException exception) {
                log.error("支付宝订单 {} 对账异常", row.getOrderNo(), exception);
            }
        }
    }

    public Map<String, Object> settlement(String orderNo) {
        PaymentRow row = mapper.findByOrder(orderNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", orderNo);
        result.put("exists", row != null);
        result.put("canClose", row == null || "FAILED".equals(row.getStatus()));
        result.put("unresolved", row != null && "PROCESSING".equals(row.getStatus()));
        if (row != null) {
            result.put("status", row.getStatus());
            result.put("channel", row.getChannel());
        }
        return result;
    }

    public Map<String, Object> query(Long userId, String orderNo) {
        PaymentRow row = mapper.findByOrder(orderNo);
        if (row == null || !row.getUserId().equals(userId)) throw new BusinessException(404, "支付记录不存在");
        return toResult(row);
    }

    public Map<String, Object> adminList(String status, String channel, String keyword, int page, int size) {
        if (status != null && !status.isBlank() && !List.of("PROCESSING", "SUCCESS", "FAILED", "REFUNDED").contains(status)) {
            throw new BusinessException("不支持的支付状态");
        }
        if (channel != null && !channel.isBlank() && !List.of("ALIPAY", "WECHAT").contains(channel)) {
            throw new BusinessException("不支持的支付渠道");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", mapper.findAdminPayments(status, channel, normalizedKeyword, (safePage - 1) * safeSize, safeSize));
        result.put("total", mapper.countAdminPayments(status, channel, normalizedKeyword));
        result.put("page", safePage); result.put("size", safeSize);
        return result;
    }

    public Map<String, Object> adminDetail(String paymentNo) {
        Map<String, Object> result = mapper.findAdminDetail(paymentNo);
        if (result == null) throw new BusinessException(404, "支付流水不存在");
        return result;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>(mapper.metrics());
        result.put("channels", mapper.channelMetrics());
        return result;
    }

    public Map<String, Object> userSummary(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>(mapper.userSummary(userId));
        result.put("recent", mapper.recentByUser(userId));
        return result;
    }

    public Map<String, Object> refund(PaymentRefundCommand command) {
        PaymentRow row = mapper.findByOrder(command.orderNo());
        if (row == null || !row.getUserId().equals(command.userId())) {
            throw new BusinessException(404, "支付流水不存在");
        }
        if ("REFUNDED".equals(row.getStatus())) return toResult(row);
        if (!"SUCCESS".equals(row.getStatus())) throw new BusinessException(409, "当前支付流水不可退款");
        if (mapper.markRefunded(command.orderNo(), command.userId()) != 1) {
            throw new BusinessException(409, "支付流水状态发生变化");
        }
        return toResult(mapper.findByOrder(command.orderNo()));
    }

    private Map<String, Object> toResult(PaymentRow row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentNo", row.getPaymentNo()); result.put("orderNo", row.getOrderNo());
        result.put("amount", row.getAmount()); result.put("channel", row.getChannel());
        result.put("status", row.getStatus()); result.put("paidAt", row.getPaidAt());
        result.put("providerTradeNo", row.getProviderTradeNo());
        result.put("providerStatus", row.getProviderStatus());
        result.put("lastCheckedAt", row.getLastCheckedAt());
        result.put("reconciliationPending", "PROCESSING".equals(row.getStatus()) && "ALIPAY".equals(row.getChannel()));
        return result;
    }

    private <T> T requireData(ApiResponse<T> response, String fallback) {
        if (response == null || response.code() != 200 || response.data() == null) {
            throw new BusinessException(502, response == null ? fallback : response.message());
        }
        return response.data();
    }

    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", new Random().nextInt(1_000_000));
    }
}

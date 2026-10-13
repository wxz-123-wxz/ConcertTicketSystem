package com.liveseat.order.service;

import com.liveseat.common.dto.*;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.order.client.SeatClient;
import com.liveseat.order.client.PaymentClient;
import com.liveseat.order.domain.OrderRow;
import com.liveseat.order.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OrderService {
    private final OrderMapper mapper;
    private final SeatClient seatClient;
    private final PaymentClient paymentClient;
    private final long timeoutMinutes;

    public OrderService(OrderMapper mapper, SeatClient seatClient, PaymentClient paymentClient,
                        @Value("${app.order.timeout-minutes:10}") long timeoutMinutes) {
        this.mapper = mapper;
        this.seatClient = seatClient;
        this.paymentClient = paymentClient;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Transactional
    public Map<String, Object> create(Long userId, Long sessionId, List<Long> seatIds) {
        SeatLockResult lock = requireData(seatClient.lock(new SeatLockCommand(userId, sessionId, seatIds)));
        String orderNo = nextOrderNo();
        OrderRow order = new OrderRow();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setSessionId(sessionId);
        order.setHoldToken(lock.holdToken());
        order.setTotalAmount(lock.totalAmount());
        order.setStatus("PENDING_PAYMENT");
        order.setExpireTime(lock.expireTime());
        try {
            mapper.insertOrder(order);
            lock.seats().forEach(seat -> mapper.insertItem(orderNo, seat));
            mapper.insertLog(orderNo, null, "PENDING_PAYMENT", "订单创建并完成锁座");
            return detail(userId, orderNo);
        } catch (RuntimeException exception) {
            seatClient.release(new SeatActionCommand(userId, sessionId, seatIds, lock.holdToken()));
            throw exception;
        }
    }

    public List<Map<String, Object>> list(Long userId) {
        return mapper.findByUser(userId).stream().map(this::toDetail).toList();
    }

    public Map<String, Object> adminList(String status, String keyword, int page, int size) {
        if (status != null && !status.isBlank() && !List.of("PENDING_PAYMENT", "PAID", "REFUND_PENDING", "REFUNDED", "CANCELLED", "CLOSED").contains(status)) {
            throw new BusinessException("不支持的订单状态");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        List<Map<String, Object>> items = mapper.findAdminOrders(status, normalizedKeyword, (safePage - 1) * safeSize, safeSize);
        items.forEach(item -> item.put("items", mapper.findItems(String.valueOf(item.get("order_no")))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items); result.put("total", mapper.countAdminOrders(status, normalizedKeyword));
        result.put("page", safePage); result.put("size", safeSize);
        return result;
    }

    public Map<String, Object> adminDetail(String orderNo) {
        OrderRow order = mapper.findByNo(orderNo);
        if (order == null) throw new BusinessException(404, "订单不存在");
        Map<String, Object> result = new LinkedHashMap<>(toDetail(order));
        result.put("userId", order.getUserId());
        result.put("logs", mapper.findLogs(orderNo));
        return result;
    }

    public Map<String, Object> detail(Long userId, String orderNo) {
        OrderRow order = mapper.findByNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) throw new BusinessException(404, "订单不存在");
        return toDetail(order);
    }

    @Transactional
    public void cancel(Long userId, String orderNo) {
        OrderRow order = mapper.findByNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) throw new BusinessException(404, "订单不存在");
        if ("CANCELLED".equals(order.getStatus()) || "CLOSED".equals(order.getStatus())) return;
        if (!"PENDING_PAYMENT".equals(order.getStatus())) throw new BusinessException("当前订单不可取消");
        if (!paymentAllowsClosure(orderNo)) {
            throw new BusinessException(409, "支付结果正在确认中，暂不能取消订单或释放座位");
        }
        if (mapper.cancel(orderNo, userId) == 1) {
            release(order);
            mapper.insertLog(orderNo, "PENDING_PAYMENT", "CANCELLED", "用户取消订单");
        }
    }

    public OrderSummary summary(Long userId, String orderNo) {
        OrderRow order = mapper.findByNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) throw new BusinessException(404, "订单不存在");
        return new OrderSummary(orderNo, userId, order.getSessionId(), order.getStatus(), order.getTotalAmount(),
                order.getHoldToken(), mapper.findSeatIds(orderNo), order.getExpireTime());
    }

    @Transactional
    public OrderSummary confirmPayment(OrderPaymentCommand command) {
        OrderRow order = mapper.findByNoForUpdate(command.orderNo());
        if (order == null || !Objects.equals(order.getUserId(), command.userId())) throw new BusinessException(404, "订单不存在");
        if ("PAID".equals(order.getStatus())) return summary(command.userId(), command.orderNo());
        if (!"PENDING_PAYMENT".equals(order.getStatus())) throw new BusinessException("当前订单不可支付");
        if (order.getExpireTime().isBefore(LocalDateTime.now()) && !"ALIPAY".equals(command.channel())) {
            throw new BusinessException(409, "订单已超时，请重新选座");
        }
        List<Long> seatIds = mapper.findSeatIds(order.getOrderNo());
        requireSuccess(seatClient.confirm(order.getOrderNo(),
                new SeatActionCommand(order.getUserId(), order.getSessionId(), seatIds, order.getHoldToken())), "座位确认失败");
        if (mapper.markPaid(order.getOrderNo(), order.getUserId()) != 1) {
            OrderRow current = mapper.findByNo(order.getOrderNo());
            if (current != null && "PAID".equals(current.getStatus())) return summary(command.userId(), command.orderNo());
            throw new BusinessException(409, "订单状态发生变化");
        }
        String note = "ALIPAY".equals(command.channel()) ? "支付宝沙箱交易确认成功" : "微信模拟支付成功";
        mapper.insertLog(order.getOrderNo(), "PENDING_PAYMENT", "PAID", note);
        for (Map<String, Object> item : mapper.findItems(order.getOrderNo())) {
            mapper.insertTicket(nextTicketCode(), order.getOrderNo(), order.getUserId(),
                    ((Number) item.get("id")).longValue(), String.valueOf(item.get("seat_code")));
        }
        return summary(command.userId(), command.orderNo());
    }

    @Transactional
    public Map<String, Object> requestRefund(Long userId, String orderNo, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 300) {
            throw new BusinessException("退款原因需要填写且不能超过 300 个字符");
        }
        OrderRow order = mapper.findByNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) throw new BusinessException(404, "订单不存在");
        Map<String, Object> existing = mapper.findRefundByOrder(orderNo);
        if (existing != null) return existing;
        if (!"PAID".equals(order.getStatus())) throw new BusinessException("只有已支付订单可以申请退款");
        if (mapper.markRefundPending(orderNo, userId) != 1) throw new BusinessException(409, "订单状态发生变化");
        String refundNo = nextRefundNo();
        mapper.insertRefund(refundNo, orderNo, userId, reason.trim());
        mapper.insertLog(orderNo, "PAID", "REFUND_PENDING", "用户提交退款申请");
        return mapper.findRefundByNo(refundNo);
    }

    public Map<String, Object> refunds(String status, int page, int size) {
        if (status != null && !status.isBlank() && !List.of("PENDING", "APPROVED", "REJECTED").contains(status)) {
            throw new BusinessException("不支持的退款状态");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", mapper.findRefunds(status, (safePage - 1) * safeSize, safeSize));
        result.put("total", mapper.countRefunds(status));
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    @Transactional
    public Map<String, Object> reviewRefund(Long operatorId, String operatorName, String refundNo,
                                            String action, String note) {
        if (!List.of("APPROVE", "REJECT").contains(action)) throw new BusinessException("不支持的审核操作");
        Map<String, Object> refund = mapper.findRefundByNo(refundNo);
        if (refund == null) throw new BusinessException(404, "退款申请不存在");
        String current = String.valueOf(refund.get("status"));
        if (!"PENDING".equals(current)) return refund;
        String orderNo = String.valueOf(refund.get("order_no"));
        Long userId = ((Number) refund.get("user_id")).longValue();
        OrderRow order = mapper.findByNo(orderNo);
        if (order == null) throw new BusinessException(404, "关联订单不存在");
        String normalizedNote = note == null || note.isBlank() ? ("APPROVE".equals(action) ? "退款审核通过" : "退款申请未通过") : note.trim();
        if (normalizedNote.length() > 300) throw new BusinessException("审核说明不能超过 300 个字符");
        if ("REJECT".equals(action)) {
            if (mapper.restorePaid(orderNo) != 1) throw new BusinessException(409, "订单状态发生变化");
            mapper.reviewRefund(refundNo, "REJECTED", normalizedNote, operatorId);
            mapper.insertLog(orderNo, "REFUND_PENDING", "PAID", "管理员驳回退款：" + normalizedNote);
            mapper.insertAudit(operatorId, operatorName, "REFUND_REJECT", "REFUND", refundNo, normalizedNote);
            return mapper.findRefundByNo(refundNo);
        }
        requireData(paymentClient.refund(new PaymentRefundCommand(userId, orderNo)));
        requireSuccess(seatClient.refund(new RefundSeatCommand(userId, orderNo, order.getSessionId(), mapper.findSeatIds(orderNo))), "退款座位释放失败");
        if (mapper.markRefunded(orderNo) != 1) throw new BusinessException(409, "订单退款状态发生变化");
        mapper.voidTickets(orderNo);
        mapper.reviewRefund(refundNo, "APPROVED", normalizedNote, operatorId);
        mapper.insertLog(orderNo, "REFUND_PENDING", "REFUNDED", "管理员批准退款：" + normalizedNote);
        mapper.insertAudit(operatorId, operatorName, "REFUND_APPROVE", "REFUND", refundNo, normalizedNote);
        return mapper.findRefundByNo(refundNo);
    }

    public List<Map<String, Object>> tickets(Long userId, String orderNo) {
        OrderRow order = mapper.findByNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) throw new BusinessException(404, "订单不存在");
        return mapper.findTickets(orderNo, userId);
    }

    public Map<String, Object> ticket(String ticketCode) {
        Map<String, Object> ticket = mapper.findTicket(ticketCode);
        if (ticket == null) throw new BusinessException(404, "电子票不存在");
        return ticket;
    }

    @Transactional
    public Map<String, Object> checkIn(Long operatorId, String operatorName, String ticketCode) {
        Map<String, Object> ticket = ticket(ticketCode);
        String status = String.valueOf(ticket.get("status"));
        if ("USED".equals(status)) return ticket;
        if (!"VALID".equals(status)) throw new BusinessException(409, "电子票已作废，不能核销");
        if (mapper.checkIn(ticketCode, operatorId) != 1) throw new BusinessException(409, "电子票状态发生变化");
        mapper.insertAudit(operatorId, operatorName, "TICKET_CHECK_IN", "TICKET", ticketCode,
                "核销订单 " + ticket.get("order_no") + " 座位 " + ticket.get("seat_code"));
        return mapper.findTicket(ticketCode);
    }

    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>(mapper.metrics());
        result.put("popularConcerts", mapper.popularConcerts());
        return result;
    }

    public Map<String, Object> userSummary(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>(mapper.userSummary(userId));
        List<Map<String, Object>> recent = mapper.findRecentByUser(userId).stream().map(this::toDetail).toList();
        result.put("recent", recent);
        return result;
    }

    @Scheduled(fixedDelay = 30000)
    public void closeExpiredOrders() {
        for (OrderRow order : mapper.findExpired()) {
            if (!paymentAllowsClosure(order.getOrderNo())) continue;
            if (mapper.closeExpired(order.getOrderNo()) == 1) {
                release(order);
                mapper.insertLog(order.getOrderNo(), "PENDING_PAYMENT", "CLOSED", "订单超时自动关闭");
            }
        }
    }

    private boolean paymentAllowsClosure(String orderNo) {
        try {
            Map<String, Object> settlement = requireData(paymentClient.settlement(orderNo));
            return Boolean.TRUE.equals(settlement.get("canClose"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void release(OrderRow order) {
        seatClient.release(new SeatActionCommand(order.getUserId(), order.getSessionId(), mapper.findSeatIds(order.getOrderNo()), order.getHoldToken()));
    }

    private Map<String, Object> toDetail(OrderRow order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo()); result.put("sessionId", order.getSessionId());
        result.put("status", order.getStatus()); result.put("totalAmount", order.getTotalAmount());
        result.put("expireTime", order.getExpireTime()); result.put("createdAt", order.getCreatedAt());
        result.put("paidAt", order.getPaidAt()); result.put("items", mapper.findItems(order.getOrderNo()));
        result.put("refund", mapper.findRefundByOrder(order.getOrderNo()));
        result.put("tickets", mapper.findTickets(order.getOrderNo(), order.getUserId()));
        return result;
    }

    private <T> T requireData(com.liveseat.common.api.ApiResponse<T> response) {
        if (response == null || response.code() != 200 || response.data() == null) {
            throw new BusinessException(502, response == null ? "座位服务不可用" : response.message());
        }
        return response.data();
    }

    private void requireSuccess(com.liveseat.common.api.ApiResponse<?> response, String fallback) {
        if (response == null || response.code() != 200) {
            throw new BusinessException(response == null ? fallback : response.message());
        }
    }

    private String nextOrderNo() {
        return "LS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", new Random().nextInt(1_000_000));
    }

    private String nextRefundNo() {
        return "RF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", new Random().nextInt(1_000_000));
    }

    private String nextTicketCode() {
        return "TKT" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}

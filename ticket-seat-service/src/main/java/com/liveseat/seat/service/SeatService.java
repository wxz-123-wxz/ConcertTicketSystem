package com.liveseat.seat.service;

import com.liveseat.common.dto.SeatActionCommand;
import com.liveseat.common.dto.SeatInfo;
import com.liveseat.common.dto.SeatLockCommand;
import com.liveseat.common.dto.SeatLockResult;
import com.liveseat.common.dto.RefundSeatCommand;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.seat.domain.SeatRow;
import com.liveseat.seat.mapper.SeatMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SeatService {
    private static final long HOLD_SECONDS = 600;
    private static final DefaultRedisScript<Long> LOCK_SCRIPT = new DefaultRedisScript<>("""
            for i = 1, #KEYS do
              if redis.call('exists', KEYS[i]) == 1 then return 0 end
            end
            for i = 1, #KEYS do redis.call('set', KEYS[i], ARGV[1], 'EX', ARGV[2]) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local released = 0
            for i = 1, #KEYS do
              if redis.call('get', KEYS[i]) == ARGV[1] then redis.call('del', KEYS[i]); released = released + 1 end
            end
            return released
            """, Long.class);

    private final SeatMapper mapper;
    private final StringRedisTemplate redis;

    public SeatService(SeatMapper mapper, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.redis = redis;
    }

    @Transactional
    public List<SeatInfo> list(Long sessionId) {
        mapper.releaseExpired(sessionId);
        List<SeatInfo> seats = mapper.findBySession(sessionId).stream().map(this::toInfo).toList();
        if (seats.isEmpty()) throw new BusinessException(404, "场次不存在或尚未生成座位");
        return seats;
    }

    public List<SeatInfo> recommend(Long sessionId, int count, String areaName) {
        if (count < 2 || count > 6) throw new BusinessException("连座数量必须在 2–6 之间");
        List<SeatInfo> available = list(sessionId).stream()
                .filter(seat -> "AVAILABLE".equals(seat.status()))
                .filter(seat -> areaName == null || areaName.isBlank() || areaName.equals(seat.areaName()))
                .sorted(Comparator.comparing(SeatInfo::areaName)
                        .thenComparing(SeatInfo::rowNo).thenComparing(SeatInfo::colNo))
                .toList();
        Map<String, List<SeatInfo>> rows = new LinkedHashMap<>();
        for (SeatInfo seat : available) {
            rows.computeIfAbsent(seat.areaName() + ":" + seat.rowNo(), ignored -> new ArrayList<>()).add(seat);
        }
        for (List<SeatInfo> row : rows.values()) {
            for (int start = 0; start <= row.size() - count; start++) {
                List<SeatInfo> candidate = row.subList(start, start + count);
                boolean consecutive = true;
                for (int index = 1; index < candidate.size(); index++) {
                    if (candidate.get(index).colNo() != candidate.get(index - 1).colNo() + 1) {
                        consecutive = false;
                        break;
                    }
                }
                if (consecutive) return List.copyOf(candidate);
            }
        }
        throw new BusinessException(404, "当前区域没有符合条件的连续座位");
    }

    @Transactional
    public SeatLockResult lock(SeatLockCommand command) {
        List<Long> seatIds = command.seatIds().stream().distinct().sorted().toList();
        if (seatIds.size() != command.seatIds().size()) throw new BusinessException("座位不能重复选择");
        if (seatIds.size() > 6) throw new BusinessException("单个订单最多选择 6 个座位");
        mapper.releaseExpired(command.sessionId());
        List<SeatRow> rows = mapper.lockRows(command.sessionId(), seatIds);
        if (rows.size() != seatIds.size()) throw new BusinessException(404, "部分座位不存在");
        if (rows.stream().anyMatch(row -> !"AVAILABLE".equals(row.getStatus()))) {
            throw new BusinessException(409, "所选座位刚刚已被占用，请重新选择");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        List<String> keys = lockKeys(command.sessionId(), seatIds);
        Long locked = redis.execute(LOCK_SCRIPT, keys, token, String.valueOf(HOLD_SECONDS));
        if (!Long.valueOf(1).equals(locked)) throw new BusinessException(409, "所选座位刚刚已被锁定，请重新选择");

        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(HOLD_SECONDS);
        try {
            if (mapper.markLocked(command.sessionId(), seatIds, token, expireTime) != seatIds.size()) {
                throw new BusinessException(409, "座位状态发生变化，请重新选择");
            }
            List<SeatInfo> seats = rows.stream().map(this::toInfo).toList();
            BigDecimal amount = rows.stream().map(SeatRow::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new SeatLockResult(token, expireTime, amount, seats);
        } catch (RuntimeException exception) {
            redis.execute(RELEASE_SCRIPT, keys, token);
            throw exception;
        }
    }

    @Transactional
    public void release(SeatActionCommand command) {
        List<Long> seatIds = command.seatIds().stream().distinct().toList();
        mapper.release(command.sessionId(), seatIds, command.holdToken());
        afterCommit(() -> redis.execute(RELEASE_SCRIPT, lockKeys(command.sessionId(), seatIds), command.holdToken()));
    }

    @Transactional
    public void confirm(SeatActionCommand command, String orderNo) {
        List<Long> seatIds = command.seatIds().stream().distinct().toList();
        List<String> keys = lockKeys(command.sessionId(), seatIds);
        int marked = mapper.markSold(command.sessionId(), seatIds, command.holdToken(), orderNo);
        if (marked != seatIds.size()
                && mapper.countSoldForOrder(command.sessionId(), seatIds, orderNo) != seatIds.size()) {
            throw new BusinessException(409, "座位状态校验失败");
        }
        afterCommit(() -> redis.execute(RELEASE_SCRIPT, keys, command.holdToken()));
    }

    @Transactional
    public void refund(RefundSeatCommand command) {
        List<Long> seatIds = command.seatIds().stream().distinct().sorted().toList();
        if (seatIds.isEmpty()) throw new BusinessException("退款座位不能为空");
        mapper.refundSold(command.sessionId(), seatIds, command.orderNo());
    }

    public Map<String, Object> metrics() {
        return mapper.metrics();
    }

    private List<String> lockKeys(Long sessionId, List<Long> seatIds) {
        return seatIds.stream().map(id -> "seat:lock:" + sessionId + ":" + id).toList();
    }

    private SeatInfo toInfo(SeatRow row) {
        return new SeatInfo(row.getId(), row.getSeatCode(), row.getAreaName(), row.getRowNo(), row.getColNo(), row.getPrice(), row.getStatus());
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { action.run(); }
        });
    }
}

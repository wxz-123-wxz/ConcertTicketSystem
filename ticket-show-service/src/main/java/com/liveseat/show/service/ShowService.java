package com.liveseat.show.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.show.mapper.ShowMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShowService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private final ShowMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public ShowService(ShowMapper mapper, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list() {
        return cached("show:list", new TypeReference<>() {}, mapper::findPublished);
    }

    public List<Map<String, Object>> adminShows() { return mapper.findAllConcerts(); }

    public List<Map<String, Object>> venues() { return mapper.findVenues(); }

    public List<Map<String, Object>> adminSessions(Long concertId) {
        List<Map<String, Object>> sessions = mapper.findAdminSessions(concertId);
        sessions.forEach(item -> item.put("categories", mapper.findCategories(((Number) item.get("id")).longValue())));
        return sessions;
    }

    public Map<String, Object> detail(Long id) {
        return cached("show:detail:" + id, new TypeReference<>() {}, () -> loadDetail(id));
    }

    public Map<String, Object> session(Long sessionId) {
        Map<String, Object> session = mapper.findSession(sessionId);
        if (session == null) {
            throw new BusinessException(404, "场次不存在");
        }
        session.put("categories", mapper.findCategories(sessionId));
        return session;
    }

    @Transactional
    public Map<String, Object> create(ShowMapper.ConcertRow row) {
        validateConcert(row);
        mapper.insertConcert(row);
        evict();
        return loadDetail(row.id);
    }

    @Transactional
    public Map<String, Object> update(Long id, ShowMapper.ConcertRow row) {
        if (mapper.findConcert(id) == null) throw new BusinessException(404, "演出不存在");
        row.id = id;
        validateConcert(row);
        if (mapper.updateConcert(row) != 1) throw new BusinessException(409, "演出资料更新失败");
        evict();
        redis.delete("show:detail:" + id);
        return loadDetail(id);
    }

    @Transactional
    public void changeStatus(Long id, String status) {
        if (!List.of("DRAFT", "ON_SALE", "OFF_SALE").contains(status)) {
            throw new BusinessException("不支持的演出状态");
        }
        if (mapper.updateStatus(id, status) == 0) {
            throw new BusinessException(404, "演出不存在");
        }
        evict();
        redis.delete("show:detail:" + id);
        if ("ON_SALE".equals(status)) {
            Map<String, Object> concert = mapper.findConcert(id);
            mapper.notifySubscribers(id, "SALE", "关注的演出已开售",
                    "“" + concert.get("title") + "”现已公开售票，可以进入演出详情选择场次。");
        }
    }

    @Transactional
    public Map<String, Object> createSession(ShowMapper.SessionRow row, List<CategoryInput> categories) {
        validateSession(row, categories, true);
        mapper.insertSession(row);
        int rowNo = 1;
        for (CategoryInput input : categories) {
            ShowMapper.CategoryRow category = new ShowMapper.CategoryRow();
            category.sessionId = row.id;
            category.name = input.name();
            category.areaName = input.areaName();
            category.price = input.price();
            category.color = input.color();
            mapper.insertCategory(category);
            String prefix = seatPrefix(input.areaName());
            for (int localRow = 0; localRow < input.rowCount(); localRow++, rowNo++) {
                for (int col = 1; col <= input.seatsPerRow(); col++) {
                    String seatCode = "%s-%02d-%02d".formatted(prefix, rowNo, col);
                    mapper.insertSeat(row.id, category.id, seatCode, input.areaName(), rowNo, col, input.price());
                }
            }
        }
        evictSessionCaches(row.concertId);
        Map<String, Object> concert = mapper.findConcert(row.concertId);
        mapper.notifySubscribers(row.concertId, "SESSION_CHANGE", "关注的演出新增场次",
                "“" + concert.get("title") + "”新增了 " + row.startTime + " 场次。");
        return session(row.id);
    }

    @Transactional
    public Map<String, Object> updateSession(Long id, ShowMapper.SessionRow row, List<CategoryUpdate> categories) {
        Map<String, Object> existing = mapper.findSession(id);
        if (existing == null) throw new BusinessException(404, "场次不存在");
        row.id = id;
        row.concertId = ((Number) existing.get("concert_id")).longValue();
        validateSession(row, List.of(), false);
        if (mapper.updateSession(row) == 0) throw new BusinessException(404, "场次不存在");
        for (CategoryUpdate input : categories == null ? List.<CategoryUpdate>of() : categories) {
            ShowMapper.CategoryRow category = new ShowMapper.CategoryRow();
            category.id = input.id(); category.sessionId = id; category.name = input.name();
            category.price = input.price(); category.color = input.color();
            if (mapper.updateCategory(category) == 0) throw new BusinessException(404, "票档不存在");
            mapper.updateUnsoldSeatPrice(id, input.id(), input.price());
        }
        evictSessionCaches(row.concertId);
        Map<String, Object> concert = mapper.findConcert(row.concertId);
        mapper.notifySubscribers(row.concertId, "SESSION_CHANGE", "关注的演出场次有变更",
                "“" + concert.get("title") + "”的场次时间或售票状态已更新，请重新确认行程。");
        return session(id);
    }

    public Map<String, Object> engagement(Long userId, Long concertId) {
        if (mapper.findConcert(concertId) == null) throw new BusinessException(404, "演出不存在");
        Map<String, Object> subscription = mapper.findSubscription(userId, concertId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("favorite", mapper.isFavorite(userId, concertId) > 0);
        result.put("saleReminder", subscription != null && enabled(subscription.get("sale_reminder")));
        result.put("sessionReminder", subscription != null && enabled(subscription.get("session_reminder")));
        return result;
    }

    public List<Map<String, Object>> favorites(Long userId) {
        return mapper.findFavorites(userId);
    }

    @Transactional
    public Map<String, Object> favorite(Long userId, Long concertId, boolean favorite) {
        if (mapper.findConcert(concertId) == null) throw new BusinessException(404, "演出不存在");
        if (favorite) mapper.addFavorite(userId, concertId); else mapper.removeFavorite(userId, concertId);
        return engagement(userId, concertId);
    }

    @Transactional
    public Map<String, Object> subscribe(Long userId, Long concertId, boolean saleReminder, boolean sessionReminder) {
        if (mapper.findConcert(concertId) == null) throw new BusinessException(404, "演出不存在");
        mapper.saveSubscription(userId, concertId, saleReminder, sessionReminder);
        return engagement(userId, concertId);
    }

    public List<Map<String, Object>> notifications(Long userId) {
        return mapper.findNotifications(userId);
    }

    public void readNotification(Long userId, Long id) {
        if (mapper.readNotification(id, userId) == 0) throw new BusinessException(404, "通知不存在");
    }

    public Map<String, Object> metrics() {
        return mapper.metrics();
    }

    public void audit(Long operatorId, String operatorName, String action,
                      String targetType, String targetId, String detail) {
        mapper.insertAudit(operatorId, operatorName, action, targetType, targetId, detail);
    }

    private void validateSession(ShowMapper.SessionRow row, List<CategoryInput> categories, boolean creating) {
        if (mapper.findConcert(row.concertId) == null) throw new BusinessException(404, "演出不存在");
        if (mapper.findVenue(row.venueId) == null) throw new BusinessException(404, "场馆不存在");
        if (row.startTime == null || row.endTime == null || !row.endTime.isAfter(row.startTime)) {
            throw new BusinessException("结束时间必须晚于开场时间");
        }
        if (!List.of("ON_SALE", "OFF_SALE").contains(row.status)) throw new BusinessException("不支持的场次状态");
        if (creating && (categories == null || categories.isEmpty())) throw new BusinessException("至少配置一个票档");
        if (creating) {
            int total = 0;
            for (CategoryInput category : categories) {
                if (category.price() == null || category.price().signum() <= 0) throw new BusinessException("票价必须大于 0");
                if (category.rowCount() < 1 || category.rowCount() > 20 || category.seatsPerRow() < 1 || category.seatsPerRow() > 30) {
                    throw new BusinessException("每个票档支持 1–20 排、每排 1–30 座");
                }
                total += category.rowCount() * category.seatsPerRow();
            }
            if (total > 600) throw new BusinessException("单场最多生成 600 个座位");
        }
    }

    private void validateConcert(ShowMapper.ConcertRow row) {
        if (row.saleStartTime == null || row.saleEndTime == null || !row.saleEndTime.isAfter(row.saleStartTime)) {
            throw new BusinessException("停售时间必须晚于开售时间");
        }
    }

    private boolean enabled(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value instanceof Number number && number.intValue() == 1;
    }

    private String seatPrefix(String areaName) {
        String normalized = areaName == null ? "" : areaName.replace("区", "").trim();
        return normalized.isEmpty() ? "S" : normalized.substring(0, 1).toUpperCase();
    }

    private void evictSessionCaches(Long concertId) {
        evict();
        redis.delete("show:detail:" + concertId);
    }

    public record CategoryInput(String name, String areaName, BigDecimal price, String color,
                                int rowCount, int seatsPerRow) {}
    public record CategoryUpdate(Long id, String name, BigDecimal price, String color) {}

    private Map<String, Object> loadDetail(Long id) {
        Map<String, Object> concert = mapper.findConcert(id);
        if (concert == null || (!"ON_SALE".equals(concert.get("status")) && !"DRAFT".equals(concert.get("status")))) {
            throw new BusinessException(404, "演出不存在或已下架");
        }
        Map<String, Object> result = new LinkedHashMap<>(concert);
        List<Map<String, Object>> sessions = mapper.findSessions(id);
        sessions.forEach(session -> session.put("categories", mapper.findCategories(((Number) session.get("id")).longValue())));
        result.put("sessions", sessions);
        return result;
    }

    private void evict() {
        redis.delete("show:list");
    }

    private <T> T cached(String key, TypeReference<T> type, SupplierWithValue<T> loader) {
        String value = redis.opsForValue().get(key);
        try {
            if (value != null) return objectMapper.readValue(value, type);
            T loaded = loader.get();
            redis.opsForValue().set(key, objectMapper.writeValueAsString(loaded), CACHE_TTL);
            return loaded;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "演出缓存处理失败");
        }
    }

    @FunctionalInterface
    private interface SupplierWithValue<T> { T get(); }
}

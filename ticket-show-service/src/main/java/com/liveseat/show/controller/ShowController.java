package com.liveseat.show.controller;

import com.liveseat.common.api.ApiResponse;
import com.liveseat.common.exception.BusinessException;
import com.liveseat.common.security.UserHeaders;
import com.liveseat.show.mapper.ShowMapper;
import com.liveseat.show.service.ShowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
public class ShowController {
    private final ShowService service;

    public ShowController(ShowService service) { this.service = service; }

    @GetMapping("/shows")
    public ApiResponse<List<Map<String, Object>>> list() { return ApiResponse.ok(service.list()); }

    @GetMapping("/shows/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) { return ApiResponse.ok(service.detail(id)); }

    @GetMapping("/shows/sessions/{sessionId}")
    public ApiResponse<Map<String, Object>> session(@PathVariable Long sessionId) { return ApiResponse.ok(service.session(sessionId)); }

    @GetMapping("/shows/{id}/engagement")
    public ApiResponse<Map<String, Object>> engagement(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                       @PathVariable Long id) {
        return ApiResponse.ok(service.engagement(userId, id));
    }

    @PutMapping("/shows/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                     @PathVariable Long id,
                                                     @RequestBody FavoriteRequest request) {
        return ApiResponse.ok(request.favorite() ? "已收藏演出" : "已取消收藏",
                service.favorite(userId, id, request.favorite()));
    }

    @GetMapping("/shows/favorites")
    public ApiResponse<List<Map<String, Object>>> favorites(@RequestHeader(UserHeaders.USER_ID) Long userId) {
        return ApiResponse.ok(service.favorites(userId));
    }

    @PutMapping("/shows/{id}/subscription")
    public ApiResponse<Map<String, Object>> subscribe(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                                      @PathVariable Long id,
                                                      @RequestBody SubscriptionRequest request) {
        return ApiResponse.ok("提醒设置已更新",
                service.subscribe(userId, id, request.saleReminder(), request.sessionReminder()));
    }

    @GetMapping("/shows/notifications")
    public ApiResponse<List<Map<String, Object>>> notifications(@RequestHeader(UserHeaders.USER_ID) Long userId) {
        return ApiResponse.ok(service.notifications(userId));
    }

    @PatchMapping("/shows/notifications/{id}/read")
    public ApiResponse<Void> readNotification(@RequestHeader(UserHeaders.USER_ID) Long userId,
                                              @PathVariable Long id) {
        service.readNotification(userId, id);
        return ApiResponse.ok("通知已读", null);
    }

    @PostMapping("/admin/shows")
    public ApiResponse<Map<String, Object>> create(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                                   @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                                   @RequestHeader(UserHeaders.USER_ROLE) String role,
                                                   @Valid @RequestBody CreateShowRequest request) {
        requireAdmin(role);
        ShowMapper.ConcertRow row = new ShowMapper.ConcertRow();
        row.title = request.title(); row.artist = request.artist(); row.description = request.description();
        row.coverUrl = request.coverUrl(); row.saleStartTime = request.saleStartTime(); row.saleEndTime = request.saleEndTime();
        Map<String, Object> created = service.create(row);
        service.audit(operatorId, operatorName, "SHOW_CREATE", "CONCERT", String.valueOf(created.get("id")),
                "创建演出草稿：" + request.title());
        return ApiResponse.ok("演出已保存为草稿", created);
    }

    @PutMapping("/admin/shows/{id}")
    public ApiResponse<Map<String, Object>> update(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                                   @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                                   @RequestHeader(UserHeaders.USER_ROLE) String role,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody CreateShowRequest request) {
        requireAdmin(role);
        ShowMapper.ConcertRow row = new ShowMapper.ConcertRow();
        row.title = request.title(); row.artist = request.artist(); row.description = request.description();
        row.coverUrl = request.coverUrl(); row.saleStartTime = request.saleStartTime(); row.saleEndTime = request.saleEndTime();
        Map<String, Object> updated = service.update(id, row);
        service.audit(operatorId, operatorName, "SHOW_UPDATE", "CONCERT", id.toString(), "更新演出资料：" + request.title());
        return ApiResponse.ok("演出资料已更新", updated);
    }

    @GetMapping("/admin/shows")
    public ApiResponse<List<Map<String, Object>>> adminShows(@RequestHeader(UserHeaders.USER_ROLE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.adminShows());
    }

    @GetMapping("/admin/venues")
    public ApiResponse<List<Map<String, Object>>> venues(@RequestHeader(UserHeaders.USER_ROLE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.venues());
    }

    @GetMapping("/admin/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(@RequestHeader(UserHeaders.USER_ROLE) String role,
                                                           @RequestParam(required = false) Long concertId) {
        requireAdmin(role);
        return ApiResponse.ok(service.adminSessions(concertId));
    }

    @PostMapping("/admin/sessions")
    public ApiResponse<Map<String, Object>> createSession(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                                          @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                                          @RequestHeader(UserHeaders.USER_ROLE) String role,
                                                          @Valid @RequestBody CreateSessionRequest request) {
        requireAdmin(role);
        ShowMapper.SessionRow row = new ShowMapper.SessionRow();
        row.concertId = request.concertId(); row.venueId = request.venueId();
        row.startTime = request.startTime(); row.endTime = request.endTime(); row.status = request.status();
        List<ShowService.CategoryInput> categories = request.categories().stream()
                .map(item -> new ShowService.CategoryInput(item.name(), item.areaName(), item.price(), item.color(), item.rowCount(), item.seatsPerRow()))
                .toList();
        Map<String, Object> created = service.createSession(row, categories);
        service.audit(operatorId, operatorName, "SESSION_CREATE", "SESSION", String.valueOf(created.get("id")),
                "创建场次并生成座位");
        return ApiResponse.ok("场次与座位已创建", created);
    }

    @PutMapping("/admin/sessions/{id}")
    public ApiResponse<Map<String, Object>> updateSession(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                                          @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                                          @RequestHeader(UserHeaders.USER_ROLE) String role,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody UpdateSessionRequest request) {
        requireAdmin(role);
        ShowMapper.SessionRow row = new ShowMapper.SessionRow();
        row.venueId = request.venueId(); row.startTime = request.startTime();
        row.endTime = request.endTime(); row.status = request.status();
        List<ShowService.CategoryUpdate> categories = request.categories() == null ? List.of() : request.categories().stream()
                .map(item -> new ShowService.CategoryUpdate(item.id(), item.name(), item.price(), item.color())).toList();
        Map<String, Object> updated = service.updateSession(id, row, categories);
        service.audit(operatorId, operatorName, "SESSION_UPDATE", "SESSION", id.toString(), "更新场次时间、状态或票档");
        return ApiResponse.ok("场次已更新", updated);
    }

    @PatchMapping("/admin/shows/{id}/status")
    public ApiResponse<Void> changeStatus(@RequestHeader(UserHeaders.USER_ID) Long operatorId,
                                          @RequestHeader(UserHeaders.USER_NAME) String operatorName,
                                          @RequestHeader(UserHeaders.USER_ROLE) String role, @PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        requireAdmin(role);
        service.changeStatus(id, body.get("status"));
        service.audit(operatorId, operatorName, "SHOW_STATUS", "CONCERT", id.toString(), "演出状态修改为 " + body.get("status"));
        return ApiResponse.ok("演出状态已更新", null);
    }

    @GetMapping("/admin/shows/metrics")
    public ApiResponse<Map<String, Object>> metrics(@RequestHeader(UserHeaders.USER_ROLE) String role) {
        requireAdmin(role);
        return ApiResponse.ok(service.metrics());
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) throw new BusinessException(403, "仅管理员可执行此操作");
    }

    public record CreateShowRequest(@NotBlank String title, @NotBlank String artist, @NotBlank String description,
                                    @NotBlank String coverUrl, @NotNull LocalDateTime saleStartTime,
                                    @NotNull LocalDateTime saleEndTime) {}

    public record CreateSessionRequest(@NotNull Long concertId, @NotNull Long venueId,
                                       @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                                       @NotBlank String status, @NotNull List<@Valid CategoryRequest> categories) {}
    public record CategoryRequest(@NotBlank String name, @NotBlank String areaName,
                                  @NotNull @DecimalMin("0.01") BigDecimal price, @NotBlank String color,
                                  @Min(1) @Max(20) int rowCount, @Min(1) @Max(30) int seatsPerRow) {}
    public record UpdateSessionRequest(@NotNull Long venueId, @NotNull LocalDateTime startTime,
                                       @NotNull LocalDateTime endTime, @NotBlank String status,
                                       List<@Valid CategoryUpdateRequest> categories) {}
    public record CategoryUpdateRequest(@NotNull Long id, @NotBlank String name,
                                        @NotNull @DecimalMin("0.01") BigDecimal price, @NotBlank String color) {}
    public record FavoriteRequest(boolean favorite) {}
    public record SubscriptionRequest(boolean saleReminder, boolean sessionReminder) {}
}

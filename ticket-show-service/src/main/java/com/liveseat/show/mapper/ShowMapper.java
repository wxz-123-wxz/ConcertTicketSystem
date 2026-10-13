package com.liveseat.show.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ShowMapper {
    @Select("""
            select c.id, c.title, c.artist, c.description, c.cover_url, c.status,
                   c.sale_start_time, c.sale_end_time,
                   min(s.start_time) next_session_time,
                   min(tc.price) min_price, v.city, v.name venue_name
            from concert c
            left join show_session s on s.concert_id = c.id and s.status = 'ON_SALE'
            left join venue v on v.id = s.venue_id
            left join ticket_category tc on tc.session_id = s.id
            where c.status = 'ON_SALE'
            group by c.id, c.title, c.artist, c.description, c.cover_url, c.status,
                     c.sale_start_time, c.sale_end_time, v.city, v.name
            order by c.sale_start_time desc
            """)
    List<Map<String, Object>> findPublished();

    @Select("""
            select c.id, c.title, c.artist, c.description, c.cover_url, c.status, c.sale_start_time, c.sale_end_time,
                   min(s.start_time) next_session_time, count(distinct s.id) session_count
            from concert c left join show_session s on s.concert_id = c.id
            group by c.id, c.title, c.artist, c.description, c.cover_url, c.status, c.sale_start_time, c.sale_end_time
            order by c.created_at desc
            """)
    List<Map<String, Object>> findAllConcerts();

    @Select("select id, name, city, address, capacity from venue order by city, name")
    List<Map<String, Object>> findVenues();

    @Select("select id, name, city, address, capacity from venue where id = #{id}")
    Map<String, Object> findVenue(Long id);

    @Select("select * from concert where id = #{id}")
    Map<String, Object> findConcert(Long id);

    @Select("""
            select s.id, s.concert_id, s.start_time, s.end_time, s.status,
                   v.id venue_id, v.name venue_name, v.city, v.address
            from show_session s join venue v on v.id = s.venue_id
            where s.concert_id = #{concertId} order by s.start_time
            """)
    List<Map<String, Object>> findSessions(Long concertId);

    @Select("""
            <script>
            select s.id, s.concert_id, c.title concert_title, c.artist, s.venue_id,
                   v.name venue_name, v.city, v.address, s.start_time, s.end_time, s.status,
                   count(ss.id) seat_count,
                   sum(case when ss.status = 'AVAILABLE' then 1 else 0 end) available_count,
                   sum(case when ss.status = 'SOLD' then 1 else 0 end) sold_count
            from show_session s
            join concert c on c.id = s.concert_id
            join venue v on v.id = s.venue_id
            left join session_seat ss on ss.session_id = s.id
            <where><if test="concertId != null">s.concert_id = #{concertId}</if></where>
            group by s.id, s.concert_id, c.title, c.artist, s.venue_id, v.name, v.city, v.address,
                     s.start_time, s.end_time, s.status
            order by s.start_time desc
            </script>
            """)
    List<Map<String, Object>> findAdminSessions(Long concertId);

    @Select("select id, session_id, name, area_name, price, color from ticket_category where session_id = #{sessionId} order by price desc")
    List<Map<String, Object>> findCategories(Long sessionId);

    @Select("""
            select s.id, s.concert_id, c.title concert_title, c.artist, s.start_time, s.end_time,
                   s.status, v.name venue_name, v.city, v.address
            from show_session s join concert c on c.id = s.concert_id join venue v on v.id = s.venue_id
            where s.id = #{sessionId}
            """)
    Map<String, Object> findSession(Long sessionId);

    @Insert("""
            insert into concert(title, artist, description, cover_url, status, sale_start_time, sale_end_time)
            values(#{title}, #{artist}, #{description}, #{coverUrl}, 'DRAFT', #{saleStartTime}, #{saleEndTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConcert(ConcertRow row);

    @Update("update concert set status = #{status} where id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("""
            update concert set title = #{title}, artist = #{artist}, description = #{description},
                   cover_url = #{coverUrl}, sale_start_time = #{saleStartTime}, sale_end_time = #{saleEndTime}
            where id = #{id}
            """)
    int updateConcert(ConcertRow row);

    @Insert("""
            insert into show_session(concert_id, venue_id, start_time, end_time, status)
            values(#{concertId}, #{venueId}, #{startTime}, #{endTime}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSession(SessionRow row);

    @Update("""
            update show_session set venue_id = #{venueId}, start_time = #{startTime},
                   end_time = #{endTime}, status = #{status} where id = #{id}
            """)
    int updateSession(SessionRow row);

    @Insert("""
            insert into ticket_category(session_id, name, area_name, price, color)
            values(#{sessionId}, #{name}, #{areaName}, #{price}, #{color})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCategory(CategoryRow row);

    @Insert("""
            insert into session_seat(session_id, category_id, seat_code, area_name, row_no, col_no, price, status)
            values(#{sessionId}, #{categoryId}, #{seatCode}, #{areaName}, #{rowNo}, #{colNo}, #{price}, 'AVAILABLE')
            """)
    int insertSeat(@Param("sessionId") Long sessionId, @Param("categoryId") Long categoryId,
                   @Param("seatCode") String seatCode, @Param("areaName") String areaName,
                   @Param("rowNo") int rowNo, @Param("colNo") int colNo, @Param("price") BigDecimal price);

    @Update("""
            update ticket_category set price = #{price}, name = #{name}, color = #{color}
            where id = #{id} and session_id = #{sessionId}
            """)
    int updateCategory(CategoryRow row);

    @Update("""
            update session_seat set price = #{price}
            where session_id = #{sessionId} and category_id = #{categoryId} and status != 'SOLD'
            """)
    int updateUnsoldSeatPrice(@Param("sessionId") Long sessionId, @Param("categoryId") Long categoryId,
                              @Param("price") BigDecimal price);

    @Insert("insert ignore into show_favorite(user_id, concert_id) values(#{userId}, #{concertId})")
    int addFavorite(@Param("userId") Long userId, @Param("concertId") Long concertId);

    @Delete("delete from show_favorite where user_id = #{userId} and concert_id = #{concertId}")
    int removeFavorite(@Param("userId") Long userId, @Param("concertId") Long concertId);

    @Select("select count(*) from show_favorite where user_id = #{userId} and concert_id = #{concertId}")
    int isFavorite(@Param("userId") Long userId, @Param("concertId") Long concertId);

    @Select("""
            select c.id, c.title, c.artist, c.description, c.cover_url, c.status,
                   min(s.start_time) next_session_time, min(tc.price) min_price,
                   min(v.city) city, min(v.name) venue_name, f.created_at favorited_at
            from show_favorite f join concert c on c.id = f.concert_id
            left join show_session s on s.concert_id = c.id and s.status = 'ON_SALE'
            left join venue v on v.id = s.venue_id left join ticket_category tc on tc.session_id = s.id
            where f.user_id = #{userId}
            group by c.id, c.title, c.artist, c.description, c.cover_url, c.status, f.created_at
            order by f.created_at desc
            """)
    List<Map<String, Object>> findFavorites(Long userId);

    @Insert("""
            insert into show_subscription(user_id, concert_id, sale_reminder, session_reminder)
            values(#{userId}, #{concertId}, #{saleReminder}, #{sessionReminder})
            on duplicate key update sale_reminder = values(sale_reminder), session_reminder = values(session_reminder)
            """)
    int saveSubscription(@Param("userId") Long userId, @Param("concertId") Long concertId,
                         @Param("saleReminder") boolean saleReminder,
                         @Param("sessionReminder") boolean sessionReminder);

    @Select("select sale_reminder, session_reminder from show_subscription where user_id = #{userId} and concert_id = #{concertId}")
    Map<String, Object> findSubscription(@Param("userId") Long userId, @Param("concertId") Long concertId);

    @Insert("""
            insert into user_notification(user_id, concert_id, type, title, content)
            select user_id, #{concertId}, #{type}, #{title}, #{content}
            from show_subscription where concert_id = #{concertId}
              and ((#{type} = 'SALE' and sale_reminder = 1) or (#{type} = 'SESSION_CHANGE' and session_reminder = 1))
            """)
    int notifySubscribers(@Param("concertId") Long concertId, @Param("type") String type,
                          @Param("title") String title, @Param("content") String content);

    @Select("select id, concert_id, type, title, content, is_read, created_at from user_notification where user_id = #{userId} order by created_at desc limit 100")
    List<Map<String, Object>> findNotifications(Long userId);

    @Update("update user_notification set is_read = 1 where id = #{id} and user_id = #{userId}")
    int readNotification(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            select count(*) concert_count,
                   sum(case when status = 'ON_SALE' then 1 else 0 end) on_sale_count,
                   sum(case when status = 'DRAFT' then 1 else 0 end) draft_count,
                   sum(case when status = 'OFF_SALE' then 1 else 0 end) off_sale_count,
                   (select count(*) from show_session) session_count
            from concert
            """)
    Map<String, Object> metrics();

    @Insert("insert into admin_audit_log(operator_id, operator_name, action, target_type, target_id, detail) values(#{operatorId}, #{operatorName}, #{action}, #{targetType}, #{targetId}, #{detail})")
    int insertAudit(@Param("operatorId") Long operatorId, @Param("operatorName") String operatorName,
                    @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") String targetId, @Param("detail") String detail);

    class ConcertRow {
        public Long id;
        public String title;
        public String artist;
        public String description;
        public String coverUrl;
        public LocalDateTime saleStartTime;
        public LocalDateTime saleEndTime;
    }

    class SessionRow {
        public Long id;
        public Long concertId;
        public Long venueId;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public String status;
    }

    class CategoryRow {
        public Long id;
        public Long sessionId;
        public String name;
        public String areaName;
        public BigDecimal price;
        public String color;
    }
}

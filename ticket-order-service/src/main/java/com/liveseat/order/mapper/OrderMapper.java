package com.liveseat.order.mapper;

import com.liveseat.common.dto.SeatInfo;
import com.liveseat.order.domain.OrderRow;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    @Insert("""
            insert into ticket_order(order_no, user_id, session_id, hold_token, total_amount, status, expire_time)
            values(#{orderNo}, #{userId}, #{sessionId}, #{holdToken}, #{totalAmount}, #{status}, #{expireTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrder(OrderRow order);

    @Insert("insert into order_item(order_no, seat_id, seat_code, area_name, price) values(#{orderNo}, #{seat.id}, #{seat.seatCode}, #{seat.areaName}, #{seat.price})")
    int insertItem(@Param("orderNo") String orderNo, @Param("seat") SeatInfo seat);

    @Insert("insert into order_log(order_no, from_status, to_status, note) values(#{orderNo}, #{fromStatus}, #{toStatus}, #{note})")
    int insertLog(@Param("orderNo") String orderNo, @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus, @Param("note") String note);

    @Select("select * from ticket_order where order_no = #{orderNo}")
    OrderRow findByNo(String orderNo);

    @Select("select * from ticket_order where order_no = #{orderNo} for update")
    OrderRow findByNoForUpdate(String orderNo);

    @Select("select * from ticket_order where user_id = #{userId} order by created_at desc")
    List<OrderRow> findByUser(Long userId);

    @Select("""
            <script>
            select o.id, o.order_no, o.user_id, u.username, u.nickname, o.session_id,
                   c.title concert_title, v.name venue_name, v.city, s.start_time,
                   o.total_amount, o.status, o.expire_time, o.paid_at, o.cancelled_at,
                   o.created_at, o.updated_at
            from ticket_order o
            left join sys_user u on u.id = o.user_id
            left join show_session s on s.id = o.session_id
            left join concert c on c.id = s.concert_id
            left join venue v on v.id = s.venue_id
            <where>
              <if test="status != null and status != ''">o.status = #{status}</if>
              <if test="keyword != null and keyword != ''">
                and (o.order_no like concat('%', #{keyword}, '%')
                     or u.username like concat('%', #{keyword}, '%')
                     or u.nickname like concat('%', #{keyword}, '%'))
              </if>
            </where>
            order by o.created_at desc limit #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> findAdminOrders(@Param("status") String status, @Param("keyword") String keyword,
                                               @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            select count(*) from ticket_order o left join sys_user u on u.id = o.user_id
            <where>
              <if test="status != null and status != ''">o.status = #{status}</if>
              <if test="keyword != null and keyword != ''">
                and (o.order_no like concat('%', #{keyword}, '%')
                     or u.username like concat('%', #{keyword}, '%')
                     or u.nickname like concat('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long countAdminOrders(@Param("status") String status, @Param("keyword") String keyword);

    @Select("select seat_id id, seat_code, area_name, price from order_item where order_no = #{orderNo} order by id")
    List<Map<String, Object>> findItems(String orderNo);

    @Select("select seat_id from order_item where order_no = #{orderNo} order by id")
    List<Long> findSeatIds(String orderNo);

    @Select("select from_status, to_status, note, created_at from order_log where order_no = #{orderNo} order by created_at")
    List<Map<String, Object>> findLogs(String orderNo);

    @Update("""
            update ticket_order set status = 'CANCELLED', cancelled_at = now()
            where order_no = #{orderNo} and user_id = #{userId} and status = 'PENDING_PAYMENT'
            """)
    int cancel(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Update("update ticket_order set status = 'CLOSED', cancelled_at = now() where order_no = #{orderNo} and status = 'PENDING_PAYMENT'")
    int closeExpired(String orderNo);

    @Select("select * from ticket_order where status = 'PENDING_PAYMENT' and expire_time < now() order by expire_time limit 100")
    List<OrderRow> findExpired();

    @Update("update ticket_order set status = 'PAID', paid_at = now() where order_no = #{orderNo} and user_id = #{userId} and status = 'PENDING_PAYMENT'")
    int markPaid(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Update("update ticket_order set status = 'REFUND_PENDING' where order_no = #{orderNo} and user_id = #{userId} and status = 'PAID'")
    int markRefundPending(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Update("update ticket_order set status = 'PAID' where order_no = #{orderNo} and status = 'REFUND_PENDING'")
    int restorePaid(String orderNo);

    @Update("update ticket_order set status = 'REFUNDED' where order_no = #{orderNo} and status = 'REFUND_PENDING'")
    int markRefunded(String orderNo);

    @Insert("insert into refund_request(refund_no, order_no, user_id, reason, status) values(#{refundNo}, #{orderNo}, #{userId}, #{reason}, 'PENDING')")
    int insertRefund(@Param("refundNo") String refundNo, @Param("orderNo") String orderNo,
                     @Param("userId") Long userId, @Param("reason") String reason);

    @Select("select * from refund_request where order_no = #{orderNo}")
    Map<String, Object> findRefundByOrder(String orderNo);

    @Select("select * from refund_request where refund_no = #{refundNo}")
    Map<String, Object> findRefundByNo(String refundNo);

    @Select("""
            <script>
            select r.*, u.username, u.nickname, o.total_amount, o.status order_status
            from refund_request r
            left join sys_user u on u.id = r.user_id
            left join ticket_order o on o.order_no = r.order_no
            <where><if test="status != null and status != ''">r.status = #{status}</if></where>
            order by r.created_at desc limit #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> findRefunds(@Param("status") String status, @Param("offset") int offset,
                                          @Param("size") int size);

    @Select("<script>select count(*) from refund_request <where><if test=\"status != null and status != ''\">status = #{status}</if></where></script>")
    long countRefunds(String status);

    @Update("""
            update refund_request set status = #{status}, admin_note = #{note}, reviewed_by = #{reviewedBy}, reviewed_at = now()
            where refund_no = #{refundNo} and status = 'PENDING'
            """)
    int reviewRefund(@Param("refundNo") String refundNo, @Param("status") String status,
                     @Param("note") String note, @Param("reviewedBy") Long reviewedBy);

    @Insert("""
            insert ignore into electronic_ticket(ticket_code, order_no, user_id, seat_id, seat_code, status)
            values(#{ticketCode}, #{orderNo}, #{userId}, #{seatId}, #{seatCode}, 'VALID')
            """)
    int insertTicket(@Param("ticketCode") String ticketCode, @Param("orderNo") String orderNo,
                     @Param("userId") Long userId, @Param("seatId") Long seatId,
                     @Param("seatCode") String seatCode);

    @Select("select ticket_code, order_no, user_id, seat_id, seat_code, status, checked_in_at, created_at from electronic_ticket where order_no = #{orderNo} and user_id = #{userId} order by seat_code")
    List<Map<String, Object>> findTickets(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Select("select * from electronic_ticket where ticket_code = #{ticketCode}")
    Map<String, Object> findTicket(String ticketCode);

    @Update("update electronic_ticket set status = 'USED', checked_in_at = now(), checked_in_by = #{operatorId} where ticket_code = #{ticketCode} and status = 'VALID'")
    int checkIn(@Param("ticketCode") String ticketCode, @Param("operatorId") Long operatorId);

    @Update("update electronic_ticket set status = 'VOID' where order_no = #{orderNo} and status = 'VALID'")
    int voidTickets(String orderNo);

    @Select("""
            select count(*) total_count,
                   sum(case when status = 'PENDING_PAYMENT' then 1 else 0 end) pending_count,
                   sum(case when status = 'PAID' then 1 else 0 end) paid_count,
                   sum(case when status = 'REFUND_PENDING' then 1 else 0 end) refund_pending_count,
                   sum(case when status = 'REFUNDED' then 1 else 0 end) refunded_count,
                   sum(case when status in ('CANCELLED','CLOSED') then 1 else 0 end) closed_count,
                   coalesce(sum(case when status = 'PAID' then total_amount else 0 end), 0) paid_amount
            from ticket_order
            """)
    Map<String, Object> metrics();

    @Select("""
            select c.id concert_id, c.title, count(*) order_count,
                   coalesce(sum(case when o.status = 'PAID' then o.total_amount else 0 end), 0) paid_amount
            from ticket_order o join show_session s on s.id = o.session_id join concert c on c.id = s.concert_id
            group by c.id, c.title order by order_count desc, paid_amount desc limit 5
            """)
    List<Map<String, Object>> popularConcerts();

    @Select("""
            select count(*) total_count,
                   sum(case when status = 'PAID' then 1 else 0 end) paid_count,
                   sum(case when status in ('CANCELLED','CLOSED') then 1 else 0 end) cancelled_count,
                   sum(case when status = 'REFUNDED' then 1 else 0 end) refunded_count,
                   coalesce(sum(case when status = 'PAID' then total_amount else 0 end), 0) paid_amount
            from ticket_order where user_id = #{userId}
            """)
    Map<String, Object> userSummary(Long userId);

    @Select("select * from ticket_order where user_id = #{userId} order by created_at desc limit 5")
    List<OrderRow> findRecentByUser(Long userId);

    @Insert("insert into admin_audit_log(operator_id, operator_name, action, target_type, target_id, detail) values(#{operatorId}, #{operatorName}, #{action}, #{targetType}, #{targetId}, #{detail})")
    int insertAudit(@Param("operatorId") Long operatorId, @Param("operatorName") String operatorName,
                    @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") String targetId, @Param("detail") String detail);
}

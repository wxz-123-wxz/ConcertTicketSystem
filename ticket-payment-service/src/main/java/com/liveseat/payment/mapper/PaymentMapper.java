package com.liveseat.payment.mapper;

import com.liveseat.payment.domain.PaymentRow;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface PaymentMapper {
    @Select("select * from payment_record where order_no = #{orderNo}")
    PaymentRow findByOrder(String orderNo);

    @Insert("""
            insert ignore into payment_record(payment_no, order_no, user_id, amount, channel, status)
            values(#{paymentNo}, #{orderNo}, #{userId}, #{amount}, #{channel}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaymentRow row);

    @Update("""
            update payment_record
            set status = 'PROCESSING', channel = #{channel}, provider_trade_no = null,
                provider_status = null, last_checked_at = null, paid_at = null
            where order_no = #{orderNo} and status = 'FAILED'
            """)
    int retry(@Param("orderNo") String orderNo, @Param("channel") String channel);

    @Update("""
            update payment_record
            set status = 'SUCCESS', channel = #{channel}, provider_trade_no = #{providerTradeNo},
                provider_status = #{providerStatus}, last_checked_at = now(), paid_at = coalesce(paid_at, now())
            where order_no = #{orderNo} and status in ('PROCESSING', 'FAILED', 'SUCCESS')
            """)
    int markSuccess(@Param("orderNo") String orderNo, @Param("channel") String channel,
                    @Param("providerTradeNo") String providerTradeNo,
                    @Param("providerStatus") String providerStatus);

    @Update("""
            update payment_record set provider_trade_no = #{providerTradeNo},
                provider_status = #{providerStatus}, last_checked_at = now()
            where order_no = #{orderNo} and channel = 'ALIPAY' and status = 'PROCESSING'
            """)
    int recordAlipayState(@Param("orderNo") String orderNo,
                          @Param("providerTradeNo") String providerTradeNo,
                          @Param("providerStatus") String providerStatus);

    @Update("""
            update payment_record set status = 'FAILED', provider_status = #{providerStatus}, last_checked_at = now()
            where order_no = #{orderNo} and channel = #{channel} and status = 'PROCESSING'
            """)
    int markFailed(@Param("orderNo") String orderNo, @Param("channel") String channel,
                   @Param("providerStatus") String providerStatus);

    @Select("select * from payment_record where channel = #{channel} and status = 'PROCESSING' order by updated_at limit #{limit}")
    List<PaymentRow> findProcessing(@Param("channel") String channel, @Param("limit") int limit);

    @Update("update payment_record set status = 'REFUNDED' where order_no = #{orderNo} and user_id = #{userId} and status = 'SUCCESS'")
    int markRefunded(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Select("""
            <script>
            select p.id, p.payment_no, p.order_no, p.user_id, u.username, u.nickname,
                   p.amount, p.channel, p.status, p.paid_at, p.created_at, p.updated_at,
                   o.status order_status
            from payment_record p
            left join sys_user u on u.id = p.user_id
            left join ticket_order o on o.order_no = p.order_no
            <where>
              <if test="status != null and status != ''">p.status = #{status}</if>
              <if test="channel != null and channel != ''">and p.channel = #{channel}</if>
              <if test="keyword != null and keyword != ''">
                and (p.payment_no like concat('%', #{keyword}, '%')
                     or p.order_no like concat('%', #{keyword}, '%')
                     or u.username like concat('%', #{keyword}, '%'))
              </if>
            </where>
            order by p.created_at desc limit #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> findAdminPayments(@Param("status") String status, @Param("channel") String channel,
                                                 @Param("keyword") String keyword, @Param("offset") int offset,
                                                 @Param("size") int size);

    @Select("""
            <script>
            select count(*) from payment_record p left join sys_user u on u.id = p.user_id
            <where>
              <if test="status != null and status != ''">p.status = #{status}</if>
              <if test="channel != null and channel != ''">and p.channel = #{channel}</if>
              <if test="keyword != null and keyword != ''">
                and (p.payment_no like concat('%', #{keyword}, '%')
                     or p.order_no like concat('%', #{keyword}, '%')
                     or u.username like concat('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long countAdminPayments(@Param("status") String status, @Param("channel") String channel,
                            @Param("keyword") String keyword);

    @Select("""
            select p.*, u.username, u.nickname, o.status order_status
            from payment_record p left join sys_user u on u.id = p.user_id
            left join ticket_order o on o.order_no = p.order_no
            where p.payment_no = #{paymentNo}
            """)
    Map<String, Object> findAdminDetail(String paymentNo);

    @Select("""
            select count(*) total_count,
                   sum(case when status = 'SUCCESS' then 1 else 0 end) success_count,
                   sum(case when status = 'REFUNDED' then 1 else 0 end) refunded_count,
                   coalesce(sum(case when status = 'SUCCESS' then amount else 0 end), 0) success_amount,
                   coalesce(sum(case when status = 'REFUNDED' then amount else 0 end), 0) refunded_amount
            from payment_record
            """)
    Map<String, Object> metrics();

    @Select("""
            select channel, count(*) payment_count,
                   coalesce(sum(case when status = 'SUCCESS' then amount else 0 end), 0) success_amount
            from payment_record group by channel order by channel
            """)
    List<Map<String, Object>> channelMetrics();

    @Select("""
            select count(*) total_count,
                   sum(case when status = 'SUCCESS' then 1 else 0 end) success_count,
                   sum(case when status = 'REFUNDED' then 1 else 0 end) refunded_count,
                   coalesce(sum(case when status = 'SUCCESS' then amount else 0 end), 0) success_amount,
                   coalesce(sum(case when status = 'REFUNDED' then amount else 0 end), 0) refunded_amount
            from payment_record where user_id = #{userId}
            """)
    Map<String, Object> userSummary(Long userId);

    @Select("""
            select payment_no, order_no, amount, channel, status, paid_at, created_at
            from payment_record where user_id = #{userId} order by created_at desc limit 5
            """)
    List<Map<String, Object>> recentByUser(Long userId);
}

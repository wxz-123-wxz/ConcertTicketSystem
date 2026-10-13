package com.liveseat.seat.mapper;

import com.liveseat.seat.domain.SeatRow;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SeatMapper {
    @Update("""
            update session_seat set status = 'AVAILABLE', lock_token = null, lock_expire_time = null
            where session_id = #{sessionId} and status = 'LOCKED' and lock_expire_time < now()
            """)
    int releaseExpired(Long sessionId);

    @Select("select * from session_seat where session_id = #{sessionId} order by row_no, col_no")
    List<SeatRow> findBySession(Long sessionId);

    @Select("""
            <script>
            select * from session_seat where session_id = #{sessionId} and id in
            <foreach collection='seatIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            for update
            </script>
            """)
    List<SeatRow> lockRows(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds);

    @Update("""
            <script>
            update session_seat set status = 'LOCKED', lock_token = #{holdToken}, lock_expire_time = #{expireTime}
            where session_id = #{sessionId} and status = 'AVAILABLE' and id in
            <foreach collection='seatIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int markLocked(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds,
                   @Param("holdToken") String holdToken, @Param("expireTime") LocalDateTime expireTime);

    @Update("""
            <script>
            update session_seat set status = 'AVAILABLE', lock_token = null, lock_expire_time = null
            where session_id = #{sessionId} and lock_token = #{holdToken} and status = 'LOCKED' and id in
            <foreach collection='seatIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int release(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds,
                @Param("holdToken") String holdToken);

    @Update("""
            <script>
            update session_seat set status = 'SOLD', sold_order_no = #{orderNo}, lock_token = null, lock_expire_time = null
            where session_id = #{sessionId} and lock_token = #{holdToken} and status = 'LOCKED' and id in
            <foreach collection='seatIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int markSold(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds,
                 @Param("holdToken") String holdToken, @Param("orderNo") String orderNo);

    @Select("""
            <script>
            select count(*) from session_seat where session_id = #{sessionId} and status = 'SOLD'
            and sold_order_no = #{orderNo} and id in
            <foreach collection='seatIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int countSoldForOrder(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds,
                          @Param("orderNo") String orderNo);

    @Update("""
            <script>
            update session_seat set status = 'AVAILABLE', sold_order_no = null,
                   lock_token = null, lock_expire_time = null
            where session_id = #{sessionId} and sold_order_no = #{orderNo} and status = 'SOLD' and id in
            <foreach collection='seatIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int refundSold(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds,
                   @Param("orderNo") String orderNo);

    @Select("""
            select count(*) total_seats,
                   sum(case when status = 'AVAILABLE' then 1 else 0 end) available_seats,
                   sum(case when status = 'LOCKED' then 1 else 0 end) locked_seats,
                   sum(case when status = 'SOLD' then 1 else 0 end) sold_seats
            from session_seat
            """)
    Map<String, Object> metrics();
}

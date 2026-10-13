package com.liveseat.auth.mapper;

import com.liveseat.auth.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("select * from sys_user where username = #{account} or phone = #{account} limit 1")
    User findByAccount(String account);

    @Select("select * from sys_user where id = #{id}")
    User findById(Long id);

    @Select("select count(*) from sys_user where username = #{username} or phone = #{phone}")
    int countExisting(String username, String phone);

    @Insert("insert into sys_user(username, phone, password_hash, nickname, role, status) " +
            "values(#{username}, #{phone}, #{passwordHash}, #{nickname}, #{role}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("""
            <script>
            select id, username, phone, nickname, role, status, created_at
            from sys_user
            <where>
              <if test="role != null and role != ''">role = #{role}</if>
              <if test="status != null and status != ''">and status = #{status}</if>
              <if test="keyword != null and keyword != ''">
                and (username like concat('%', #{keyword}, '%')
                     or nickname like concat('%', #{keyword}, '%')
                     or phone like concat('%', #{keyword}, '%'))
              </if>
            </where>
            order by created_at desc, id desc limit #{offset}, #{size}
            </script>
            """)
    List<User> findAdminUsers(@Param("role") String role, @Param("status") String status,
                              @Param("keyword") String keyword, @Param("offset") int offset,
                              @Param("size") int size);

    @Select("""
            <script>
            select count(*) from sys_user
            <where>
              <if test="role != null and role != ''">role = #{role}</if>
              <if test="status != null and status != ''">and status = #{status}</if>
              <if test="keyword != null and keyword != ''">
                and (username like concat('%', #{keyword}, '%')
                     or nickname like concat('%', #{keyword}, '%')
                     or phone like concat('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long countAdminUsers(@Param("role") String role, @Param("status") String status,
                         @Param("keyword") String keyword);

    @Update("update sys_user set status = #{status} where id = #{id} and role = 'USER'")
    int updateUserStatus(@Param("id") Long id, @Param("status") String status);

    @Select("select count(*) from sys_user where phone = #{phone} and id != #{id}")
    int countPhoneExcept(@Param("phone") String phone, @Param("id") Long id);

    @Update("update sys_user set nickname = #{nickname}, phone = #{phone} where id = #{id}")
    int updateProfile(@Param("id") Long id, @Param("nickname") String nickname, @Param("phone") String phone);

    @Update("update sys_user set password_hash = #{passwordHash} where id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Insert("insert into admin_audit_log(operator_id, operator_name, action, target_type, target_id, detail) values(#{operatorId}, #{operatorName}, #{action}, #{targetType}, #{targetId}, #{detail})")
    int insertAudit(@Param("operatorId") Long operatorId, @Param("operatorName") String operatorName,
                    @Param("action") String action, @Param("targetType") String targetType,
                    @Param("targetId") String targetId, @Param("detail") String detail);

    @Select("""
            <script>
            select id, operator_id, operator_name, action, target_type, target_id, detail, created_at
            from admin_audit_log
            <where>
              <if test="action != null and action != ''">action = #{action}</if>
              <if test="keyword != null and keyword != ''">
                and (operator_name like concat('%', #{keyword}, '%')
                     or target_id like concat('%', #{keyword}, '%')
                     or detail like concat('%', #{keyword}, '%'))
              </if>
            </where>
            order by created_at desc limit #{offset}, #{size}
            </script>
            """)
    List<java.util.Map<String, Object>> findAudits(@Param("action") String action,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset, @Param("size") int size);

    @Select("""
            <script>
            select count(*) from admin_audit_log
            <where>
              <if test="action != null and action != ''">action = #{action}</if>
              <if test="keyword != null and keyword != ''">
                and (operator_name like concat('%', #{keyword}, '%')
                     or target_id like concat('%', #{keyword}, '%')
                     or detail like concat('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long countAudits(@Param("action") String action, @Param("keyword") String keyword);
}

package com.yangkangling.library.repository;

import com.yangkangling.library.entity.User;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

// 用户账号数据访问。
public interface UserRepository extends JpaRepository<User, Long> {
    // 判断账号是否已经存在。
    boolean existsByUsername(String username);

    // 判断某手机号是否已经绑定指定角色账号。
    boolean existsByPhoneAndRole(String phone, String role);

    // 按账号查询用户。
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndRole(String username, String role);

    // 借书时锁定读者行，避免并发请求绕过借阅上限。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    // 按手机号和角色查询用户。
    Optional<User> findByPhoneAndRole(String phone, String role);

    // 查询某种角色的用户列表。
    List<User> findByRole(String role);

    // 查询某个借阅证号前缀下的读者，用于生成下一个借阅证号。
    List<User> findByRoleAndUsernameStartingWithOrderByUsernameDesc(String role, String usernamePrefix);

    // 兼容旧读者查询：按借阅证号、姓名、手机号模糊搜索。
    List<User> findByRoleAndUsernameContainingOrRoleAndRealNameContainingOrRoleAndPhoneContaining(
            String role1,
            String username,
            String role2,
            String realName,
            String role3,
            String phone
    );

    // 新读者分页查询，按关键字和状态过滤，并按借阅证号升序排列。
    @Query("select u from User u " +
            "where u.role = 'reader' " +
            "and (:keyword is null or :keyword = '' " +
            "or u.username like concat('%', :keyword, '%') " +
            "or u.realName like concat('%', :keyword, '%') " +
            "or u.phone like concat('%', :keyword, '%')) " +
            "and (:status is null or :status = '' or u.status = :status) " +
            "order by u.username asc, u.id asc")
    Page<User> searchReaders(
            // 借阅证号、姓名或手机号关键字。
            @Param("keyword") String keyword,
            // 读者状态。
            @Param("status") String status,
            // 分页参数。
            Pageable pageable
    );

    // 统计指定角色用户数量。
    long countByRole(String role);
}

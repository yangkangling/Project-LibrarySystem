package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    boolean existsByPhoneAndRole(String phone, String role);

    Optional<User> findByUsername(String username);

    Optional<User> findByPhoneAndRole(String phone, String role);

    Optional<User> findByUsernameAndPasswordAndRole(String username, String password, String role);

    List<User> findByRole(String role);

    List<User> findByRoleAndUsernameStartingWithOrderByUsernameDesc(String role, String usernamePrefix);

    List<User> findByRoleAndUsernameContainingOrRoleAndRealNameContainingOrRoleAndPhoneContaining(
            String role1,
            String username,
            String role2,
            String realName,
            String role3,
            String phone
    );

    @Query("""
            select u from User u
            where u.role = 'reader'
            and (:keyword is null or :keyword = ''
                or u.username like concat('%', :keyword, '%')
                or u.realName like concat('%', :keyword, '%')
                or u.phone like concat('%', :keyword, '%'))
            and (:status is null or :status = '' or u.status = :status)
            order by u.id desc
            """)
    Page<User> searchReaders(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    long countByRole(String role);
}

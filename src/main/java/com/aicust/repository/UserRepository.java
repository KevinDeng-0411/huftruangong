package com.aicust.repository;

import com.aicust.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    /**
     * 原子扣减用户余额
     * 使用 @Modifying 和 @Query 直接在数据库执行更新，避免并发读取旧值的问题
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.balance = u.balance - :amount WHERE u.id = :userId")
    void decreaseBalance(@Param("userId") Long userId, @Param("amount") Integer amount);
}

package com.trekkfin.repository;

import com.trekkfin.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.phone = :phone")
    Optional<Account> findByPhoneWithLock(@Param("phone") String phone);

    @Transactional
    @Modifying
    @Query("UPDATE Account a SET a.balance = :balance WHERE a.phone = :phone")
    int updateBalanceByPhone(@Param("phone") String phone, @Param("balance") BigDecimal balance);
}


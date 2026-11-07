package org.ikigaidigital.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TimeDepositJpaRepository extends JpaRepository<TimeDepositEntity, Integer> {

    /** Deposits and their withdrawals in one query, avoiding one query per deposit (N+1). */
    @Query("select distinct d from TimeDepositEntity d left join fetch d.withdrawals order by d.id")
    List<TimeDepositEntity> findAllWithWithdrawals();

    /**
     * {@code SELECT ... FOR UPDATE} on every deposit. Ordered by id so concurrent callers take the locks
     * in the same order and cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TimeDepositEntity d order by d.id")
    List<TimeDepositEntity> findAllForUpdate();
}

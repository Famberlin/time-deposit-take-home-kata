package org.ikigaidigital.adapter.out.persistence;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.TimeDepositView;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@link TimeDepositRepository} on PostgreSQL through Spring Data JPA. */
@Component
class TimeDepositPersistenceAdapter implements TimeDepositRepository {

    private final TimeDepositJpaRepository repository;

    TimeDepositPersistenceAdapter(TimeDepositJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeDepositView> loadAll() {
        return repository.findAllWithWithdrawals().stream()
                .map(TimeDepositMapper::toView)
                .toList();
    }

    /** Requires an active transaction; the locks are released when it ends. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TimeDeposit> lockBatchForUpdate(int afterId, int limit) {
        return repository.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit)).stream()
                .map(TimeDepositMapper::toTimeDeposit)
                .toList();
    }

    /** Ids that are not stored are ignored. Changes are flushed when the transaction commits. */
    @Override
    @Transactional
    public void saveBalances(List<TimeDeposit> deposits) {
        Map<Integer, TimeDepositEntity> entities =
                repository.findAllById(deposits.stream().map(TimeDeposit::getId).toList()).stream()
                        .collect(Collectors.toMap(TimeDepositEntity::getId, Function.identity()));
        for (TimeDeposit deposit : deposits) {
            TimeDepositEntity entity = entities.get(deposit.getId());
            if (entity != null) {
                entity.setBalance(TimeDepositMapper.toMoney(deposit.getBalance()));
            }
        }
    }
}

package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.ListTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;

/**
 * Implements the time deposit use cases on top of the existing {@link TimeDepositCalculator}.
 * Framework-free; transactions are added around it in {@code org.ikigaidigital.config}.
 */
public class TimeDepositService implements UpdateAllBalancesUseCase, ListTimeDepositsUseCase {

    private final TimeDepositRepository repository;
    private final TimeDepositCalculator calculator;

    public TimeDepositService(TimeDepositRepository repository, TimeDepositCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    /**
     * Withdrawals are not part of the interest calculation: the requirements and the existing
     * calculator only use plan type, days and balance. Must run in one transaction: the rows stay
     * locked from {@code lockAllForUpdate} until the balances are saved.
     */
    @Override
    public void updateAllBalances() {
        List<TimeDeposit> deposits = repository.lockAllForUpdate();
        calculator.updateBalance(deposits);
        repository.saveBalances(deposits);
    }

    @Override
    public List<TimeDepositView> listAll() {
        return repository.loadAll();
    }
}

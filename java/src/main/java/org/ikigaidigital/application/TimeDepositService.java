package org.ikigaidigital.application;

import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.ListTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.port.out.TransactionRunner;

/**
 * Implements the time deposit use cases on top of the existing {@link TimeDepositCalculator}.
 * Framework-free: transactions come through the {@link TransactionRunner} port.
 */
public class TimeDepositService implements UpdateAllBalancesUseCase, ListTimeDepositsUseCase {

    private final TimeDepositRepository repository;
    private final TransactionRunner transactions;
    private final TimeDepositCalculator calculator;
    private final int batchSize;

    /**
     * @param batchSize deposits locked and updated per transaction
     * @throws IllegalArgumentException if {@code batchSize} is not positive
     */
    public TimeDepositService(
            TimeDepositRepository repository,
            TransactionRunner transactions,
            TimeDepositCalculator calculator,
            int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1, was " + batchSize);
        }
        this.repository = repository;
        this.transactions = transactions;
        this.calculator = calculator;
        this.batchSize = batchSize;
    }

    /**
     * Updates the deposits in batches of {@code batchSize}, in id order, each batch in its own
     * transaction. Only the rows of the current batch are locked, and only until that batch commits,
     * so other work on the table is not blocked for the whole run.
     *
     * <p>Each call still applies exactly one month to every deposit: a concurrent call waits on the
     * locked rows and then reads the committed balances. The run as a whole is not atomic; if it
     * fails, the batches committed so far keep their new balances.
     *
     * <p>Withdrawals are not part of the interest calculation: the requirements and the existing
     * calculator only use plan type, days and balance.
     */
    @Override
    public void updateAllBalances() {
        int afterId = Integer.MIN_VALUE;
        Batch batch;
        do {
            int from = afterId;
            batch = transactions.inTransaction(() -> updateBatch(from));
            afterId = batch.lastId();
        } while (batch.size() == batchSize);
    }

    @Override
    public List<TimeDepositView> listAll() {
        return transactions.inReadOnlyTransaction(repository::loadAll);
    }

    private Batch updateBatch(int afterId) {
        List<TimeDeposit> deposits = repository.lockBatchForUpdate(afterId, batchSize);
        if (deposits.isEmpty()) {
            return new Batch(0, afterId);
        }
        calculator.updateBalance(deposits);
        repository.saveBalances(deposits);
        return new Batch(deposits.size(), deposits.getLast().getId());
    }

    private record Batch(int size, int lastId) {}
}

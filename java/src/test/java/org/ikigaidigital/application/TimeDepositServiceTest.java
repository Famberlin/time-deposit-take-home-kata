package org.ikigaidigital.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.port.out.TransactionRunner;
import org.junit.jupiter.api.Test;

class TimeDepositServiceTest {

    private static final int BATCH_SIZE = 2;

    private final InMemoryTimeDepositRepository repository = new InMemoryTimeDepositRepository();
    private final CountingTransactionRunner transactions = new CountingTransactionRunner();
    private final TimeDepositService service =
            new TimeDepositService(repository, transactions, new TimeDepositCalculator(), BATCH_SIZE);

    @Test
    void updateAllBalancesAddsOneMonthOfInterestToEveryDeposit() {
        repository.add(deposit(1, "basic", "1000.00", 31));
        repository.add(deposit(2, "student", "1000.00", 366));
        repository.add(deposit(3, "premium", "1000.00", 46));

        service.updateAllBalances();

        assertThat(repository.balances()).containsExactly(1000.83, 1000.00, 1004.17);
    }

    @Test
    void updateAllBalancesLocksAndCommitsOneBatchAtATime() {
        for (int id = 1; id <= 5; id++) {
            repository.add(deposit(id, "basic", "1000.00", 31));
        }

        service.updateAllBalances();

        assertThat(repository.lockedBatches()).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(transactions.readWrite()).as("one transaction per batch").isEqualTo(3);
        assertThat(repository.balances()).containsOnly(1000.83);
    }

    @Test
    void updateAllBalancesStopsAfterAnEmptyBatchWhenTheLastOneWasFull() {
        for (int id = 1; id <= 4; id++) {
            repository.add(deposit(id, "basic", "1000.00", 31));
        }

        service.updateAllBalances();

        assertThat(repository.lockedBatches()).containsExactly(List.of(1, 2), List.of(3, 4), List.of());
    }

    @Test
    void updateAllBalancesKeepsDays() {
        repository.add(deposit(1, "basic", "1000.00", 31));

        service.updateAllBalances();
        service.updateAllBalances();

        assertThat(repository.savedDays()).containsOnly(31);
    }

    @Test
    void listAllReturnsDepositsWithWithdrawalsInAReadOnlyTransaction() {
        WithdrawalView withdrawal = new WithdrawalView(7, new BigDecimal("100.00"), LocalDate.of(2026, 10, 1));
        TimeDepositView deposit = new TimeDepositView(1, "basic", new BigDecimal("1000.00"), 31, List.of(withdrawal));
        repository.add(deposit);

        assertThat(service.listAll()).containsExactly(deposit);
        assertThat(transactions.readOnly()).isEqualTo(1);
    }

    @Test
    void handlesNoDeposits() {
        service.updateAllBalances();

        assertThat(service.listAll()).isEmpty();
        assertThat(transactions.readWrite()).isEqualTo(1);
    }

    @Test
    void rejectsBatchSizeBelowOne() {
        TimeDepositCalculator calculator = new TimeDepositCalculator();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TimeDepositService(repository, transactions, calculator, 0))
                .withMessageContaining("batchSize");
    }

    private static TimeDepositView deposit(int id, String planType, String balance, int days) {
        return new TimeDepositView(id, planType, new BigDecimal(balance), days, List.of());
    }

    /** Runs the work directly and counts the transactions. */
    private static final class CountingTransactionRunner implements TransactionRunner {

        private int readWrite;
        private int readOnly;

        int readWrite() {
            return readWrite;
        }

        int readOnly() {
            return readOnly;
        }

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            readWrite++;
            return work.get();
        }

        @Override
        public <T> T inReadOnlyTransaction(Supplier<T> work) {
            readOnly++;
            return work.get();
        }
    }

    /** Fake of the outbound port that keeps deposits in a list ordered by id. */
    private static final class InMemoryTimeDepositRepository implements TimeDepositRepository {

        private final List<TimeDepositView> deposits = new ArrayList<>();
        private final List<Integer> savedDays = new ArrayList<>();
        private final List<List<Integer>> lockedBatches = new ArrayList<>();

        void add(TimeDepositView deposit) {
            deposits.add(deposit);
        }

        List<Double> balances() {
            return deposits.stream().map(d -> d.balance().doubleValue()).toList();
        }

        List<Integer> savedDays() {
            return savedDays;
        }

        List<List<Integer>> lockedBatches() {
            return lockedBatches;
        }

        @Override
        public List<TimeDeposit> lockBatchForUpdate(int afterId, int limit) {
            List<TimeDeposit> batch = deposits.stream()
                    .filter(d -> d.id() > afterId)
                    .limit(limit)
                    .map(d -> new TimeDeposit(d.id(), d.planType(), d.balance().doubleValue(), d.days()))
                    .toList();
            lockedBatches.add(batch.stream().map(TimeDeposit::getId).toList());
            return batch;
        }

        @Override
        public List<TimeDepositView> loadAll() {
            return List.copyOf(deposits);
        }

        @Override
        public void saveBalances(List<TimeDeposit> updated) {
            for (TimeDeposit deposit : updated) {
                savedDays.add(deposit.getDays());
                deposits.replaceAll(d -> d.id() == deposit.getId()
                        ? new TimeDepositView(
                                d.id(),
                                d.planType(),
                                BigDecimal.valueOf(deposit.getBalance()),
                                d.days(),
                                d.withdrawals())
                        : d);
            }
        }
    }
}

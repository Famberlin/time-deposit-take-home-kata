package org.ikigaidigital.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.junit.jupiter.api.Test;

class TimeDepositServiceTest {

    private final InMemoryTimeDepositRepository repository = new InMemoryTimeDepositRepository();
    private final TimeDepositService service = new TimeDepositService(repository, new TimeDepositCalculator());

    @Test
    void updateAllBalancesAddsOneMonthOfInterestToEveryDeposit() {
        repository.add(deposit(1, "basic", "1000.00", 31));
        repository.add(deposit(2, "student", "1000.00", 366));
        repository.add(deposit(3, "premium", "1000.00", 46));

        service.updateAllBalances();

        assertThat(repository.balances()).containsExactly(1000.83, 1000.00, 1004.17);
    }

    @Test
    void updateAllBalancesKeepsDays() {
        repository.add(deposit(1, "basic", "1000.00", 31));

        service.updateAllBalances();
        service.updateAllBalances();

        assertThat(repository.savedDays()).containsOnly(31);
    }

    @Test
    void listAllReturnsDepositsWithWithdrawals() {
        WithdrawalView withdrawal = new WithdrawalView(7, new BigDecimal("100.00"), LocalDate.of(2026, 10, 1));
        TimeDepositView deposit = new TimeDepositView(1, "basic", new BigDecimal("1000.00"), 31, List.of(withdrawal));
        repository.add(deposit);

        assertThat(service.listAll()).containsExactly(deposit);
    }

    @Test
    void handlesNoDeposits() {
        service.updateAllBalances();

        assertThat(service.listAll()).isEmpty();
    }

    private static TimeDepositView deposit(int id, String planType, String balance, int days) {
        return new TimeDepositView(id, planType, new BigDecimal(balance), days, List.of());
    }

    /** Fake of the outbound port that keeps deposits in a list. */
    private static final class InMemoryTimeDepositRepository implements TimeDepositRepository {

        private final List<TimeDepositView> deposits = new ArrayList<>();
        private final List<Integer> savedDays = new ArrayList<>();

        void add(TimeDepositView deposit) {
            deposits.add(deposit);
        }

        List<Double> balances() {
            return deposits.stream().map(d -> d.balance().doubleValue()).toList();
        }

        List<Integer> savedDays() {
            return savedDays;
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

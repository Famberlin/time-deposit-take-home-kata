package org.ikigaidigital.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.ikigaidigital.PostgresContainerConfiguration;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.TimeDepositView;
import org.ikigaidigital.application.WithdrawalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Runs the Flyway migrations on PostgreSQL and checks mapping and queries of the adapter. */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainerConfiguration.class, TimeDepositPersistenceAdapter.class})
class TimeDepositPersistenceAdapterIT {

    @Autowired
    private TimeDepositPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcClient jdbc;

    private int basicId;
    private int premiumId;

    @BeforeEach
    void insertDeposits() {
        TimeDepositEntity basic = new TimeDepositEntity("basic", 31, new BigDecimal("1000.00"));
        TimeDepositEntity premium = new TimeDepositEntity("premium", 46, new BigDecimal("2000.50"));
        entityManager.persist(basic);
        entityManager.persist(premium);
        entityManager.persist(new WithdrawalEntity(premium, new BigDecimal("50.00"), LocalDate.of(2026, 10, 2)));
        entityManager.persist(new WithdrawalEntity(premium, new BigDecimal("25.25"), LocalDate.of(2026, 10, 1)));
        entityManager.flush();
        entityManager.clear();
        basicId = basic.getId();
        premiumId = premium.getId();
    }

    @Test
    void loadAllReturnsDepositsWithWithdrawalsOrderedById() {
        List<TimeDepositView> deposits = adapter.loadAll();

        assertThat(deposits)
                .extracting(
                        TimeDepositView::id, TimeDepositView::planType, TimeDepositView::balance, TimeDepositView::days)
                .containsExactly(
                        tuple(basicId, "basic", new BigDecimal("1000.00"), 31),
                        tuple(premiumId, "premium", new BigDecimal("2000.50"), 46));
        assertThat(deposits.getFirst().withdrawals()).isEmpty();
        assertThat(deposits.get(1).withdrawals())
                .extracting(WithdrawalView::amount, WithdrawalView::date)
                .containsExactly(
                        tuple(new BigDecimal("50.00"), LocalDate.of(2026, 10, 2)),
                        tuple(new BigDecimal("25.25"), LocalDate.of(2026, 10, 1)));
    }

    @Test
    void loadAllUsesOneQuery() {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        adapter.loadAll();

        assertThat(statistics.getPrepareStatementCount())
                .as("no N+1 on withdrawals")
                .isEqualTo(1);
    }

    @Test
    void lockBatchForUpdateReturnsDepositsOrderedById() {
        List<TimeDeposit> deposits = adapter.lockBatchForUpdate(Integer.MIN_VALUE, 10);

        assertThat(deposits)
                .extracting(TimeDeposit::getId, TimeDeposit::getPlanType, TimeDeposit::getBalance, TimeDeposit::getDays)
                .containsExactly(tuple(basicId, "basic", 1000.00, 31), tuple(premiumId, "premium", 2000.50, 46));
    }

    @Test
    void lockBatchForUpdateStartsAfterTheGivenIdAndRespectsTheLimit() {
        assertThat(adapter.lockBatchForUpdate(Integer.MIN_VALUE, 1))
                .extracting(TimeDeposit::getId)
                .containsExactly(basicId);
        assertThat(adapter.lockBatchForUpdate(basicId, 1))
                .extracting(TimeDeposit::getId)
                .containsExactly(premiumId);
        assertThat(adapter.lockBatchForUpdate(premiumId, 1)).isEmpty();
    }

    @Test
    void saveBalancesStoresTwoDecimalsAndOnlyTheBalance() {
        adapter.saveBalances(List.of(
                new TimeDeposit(basicId, "ignored", 1000.83, 999), new TimeDeposit(premiumId, "premium", 2008.84, 46)));
        entityManager.flush();

        record Row(String planType, int days, BigDecimal balance) {}
        List<Row> rows = jdbc.sql("select plan_type, days, balance from time_deposits order by id")
                .query(Row.class)
                .list();
        assertThat(rows)
                .containsExactly(
                        new Row("basic", 31, new BigDecimal("1000.83")),
                        new Row("premium", 46, new BigDecimal("2008.84")));
    }

    @Test
    void saveBalancesIgnoresUnknownIds() {
        adapter.saveBalances(List.of(new TimeDeposit(-1, "basic", 5.00, 31)));
        entityManager.flush();

        assertThat(jdbc.sql("select count(*) from time_deposits")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
    }
}

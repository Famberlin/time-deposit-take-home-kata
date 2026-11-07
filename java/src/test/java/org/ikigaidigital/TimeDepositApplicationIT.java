package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Whole service on PostgreSQL in Docker: HTTP, use cases, persistence and Flyway migrations. */
@SpringBootTest(properties = "time-deposits.balance-update.batch-size=" + TimeDepositApplicationIT.BATCH_SIZE)
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class TimeDepositApplicationIT {

    /** Smaller than the number of deposits, so every update runs several batches. */
    static final int BATCH_SIZE = 2;

    /** PostgreSQL error code for "lock_not_available". */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TimeDepositRepository repository;

    @Autowired
    private UpdateAllBalancesUseCase updateAllBalances;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void insertDeposits() {
        jdbc.sql("TRUNCATE withdrawals, time_deposits RESTART IDENTITY CASCADE").update();
        jdbc.sql("""
                INSERT INTO time_deposits (plan_type, days, balance) VALUES
                    ('basic', 31, 1000.00), ('student', 366, 1000.00), ('premium', 46, 1000.00), ('gold', 100, 1000.00)
                """).update();
        jdbc.sql("INSERT INTO withdrawals (time_deposit_id, amount, date) VALUES (1, 100.00, DATE '2026-10-01')")
                .update();
    }

    @Test
    void updateBalancesThenListThem() throws Exception {
        mvc.perform(put("/api/v1/time-deposits/balances")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "items": [
                            { "id": 1, "planType": "basic", "balance": 1000.83, "days": 31,
                              "withdrawals": [ { "id": 1, "amount": 100.00, "date": "2026-10-01" } ] },
                            { "id": 2, "planType": "student", "balance": 1000.00, "days": 366, "withdrawals": [] },
                            { "id": 3, "planType": "premium", "balance": 1004.17, "days": 46, "withdrawals": [] },
                            { "id": 4, "planType": "gold", "balance": 1000.00, "days": 100, "withdrawals": [] }
                          ],
                          "total": 4
                        }
                        """));
    }

    /** Without the row locks, parallel updates would read the same balance and lose months. */
    @Test
    void concurrentUpdatesEachApplyOneMonth() throws Exception {
        int updates = 4;
        Callable<Void> update = () -> {
            updateAllBalances.updateAllBalances();
            return null;
        };
        List<Future<Void>> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(updates)) {
            results = executor.invokeAll(
                    IntStream.range(0, updates).mapToObj(i -> update).toList());
        }
        for (Future<Void> result : results) {
            result.get();
        }

        assertThat(balance(1)).isEqualTo(expectedAfterMonths("basic", 31, updates));
        assertThat(balance(3)).isEqualTo(expectedAfterMonths("premium", 46, updates));
    }

    @Test
    void balanceUpdateLocksTheRowsOfTheCurrentBatchOnly() {
        transactionTemplate.executeWithoutResult(status -> {
            repository.lockBatchForUpdate(Integer.MIN_VALUE, BATCH_SIZE);

            assertThatExceptionOfType(SQLException.class)
                    .isThrownBy(() -> tryLockFromAnotherConnection("id <= 2"))
                    .satisfies(e -> assertThat(e.getSQLState()).isEqualTo(LOCK_NOT_AVAILABLE));
            assertThatNoException()
                    .as("rows of later batches stay free")
                    .isThrownBy(() -> tryLockFromAnotherConnection("id > 2"));
        });

        assertThatNoException()
                .as("locks are released on commit")
                .isThrownBy(() -> tryLockFromAnotherConnection("true"));
    }

    @Test
    void lockingRequiresATransaction() {
        assertThatExceptionOfType(IllegalTransactionStateException.class)
                .isThrownBy(() -> repository.lockBatchForUpdate(Integer.MIN_VALUE, BATCH_SIZE));
    }

    /** Uses its own connection, outside the Spring-managed transaction. */
    private void tryLockFromAnotherConnection(String condition) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.execute("SELECT id FROM time_deposits WHERE " + condition + " FOR UPDATE NOWAIT");
            } finally {
                connection.rollback();
            }
        }
    }

    private BigDecimal balance(int id) {
        return jdbc.sql("SELECT balance FROM time_deposits WHERE id = ?")
                .param(id)
                .query(BigDecimal.class)
                .single();
    }

    private static BigDecimal expectedAfterMonths(String planType, int days, int months) {
        TimeDeposit deposit = new TimeDeposit(1, planType, 1000.00, days);
        TimeDepositCalculator calculator = new TimeDepositCalculator();
        for (int i = 0; i < months; i++) {
            calculator.updateBalance(List.of(deposit));
        }
        return BigDecimal.valueOf(deposit.getBalance()).setScale(2, RoundingMode.HALF_UP);
    }
}

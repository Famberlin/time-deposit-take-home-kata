package org.ikigaidigital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Characterization tests: they pin the behaviour of {@link TimeDepositCalculator#updateBalance} as
 * it was before the refactoring, so the refactoring cannot change results.
 */
class TimeDepositCalculatorTest {

    private final TimeDepositCalculator calculator = new TimeDepositCalculator();

    @Test
    void updateBalance_Test() {
        List<TimeDeposit> plans = Arrays.asList(new TimeDeposit(1, "basic", 1234567.00, 45));

        calculator.updateBalance(plans);

        assertThat(plans.getFirst().getBalance()).isEqualTo(1235595.81);
    }

    @ParameterizedTest(name = "{0} plan, {1} days, balance {2} -> {3}")
    @CsvSource({
        // basic: 1% p.a. after the first 30 days
        "basic,     0,    1000.00, 1000.00",
        "basic,    30,    1000.00, 1000.00",
        "basic,    31,    1000.00, 1000.83",
        "basic,   366,    1000.00, 1000.83",
        // student: 3% p.a. from day 31 until day 365, none after one year
        "student,  30,    1000.00, 1000.00",
        "student,  31,    1000.00, 1002.50",
        "student, 365,    1000.00, 1002.50",
        "student, 366,    1000.00, 1000.00",
        // premium: 5% p.a. from day 46
        "premium,  31,    1000.00, 1000.00",
        "premium,  45,    1000.00, 1000.00",
        "premium,  46,    1000.00, 1004.17",
        "premium, 400,    1000.00, 1004.17",
        // unknown plans and wrong case earn nothing
        "gold,    100,    1000.00, 1000.00",
        "Basic,   100,    1000.00, 1000.00",
        // interest is rounded HALF_UP to 2 decimals before it is added
        "basic,    31,      10.00,   10.01",
        "student,  31,       1.00,    1.00",
        "premium,  46,      10.00,   10.04",
        "basic,    31,       0.00,    0.00",
    })
    void appliesOneMonthOfInterest(String planType, int days, double balance, double expected) {
        TimeDeposit deposit = new TimeDeposit(1, planType, balance, days);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getBalance()).isEqualTo(expected);
    }

    @Test
    void updatesEveryDepositInTheList() {
        List<TimeDeposit> deposits = List.of(
                new TimeDeposit(1, "basic", 1000.00, 31),
                new TimeDeposit(2, "student", 1000.00, 31),
                new TimeDeposit(3, "premium", 1000.00, 46),
                new TimeDeposit(4, "premium", 1000.00, 45));

        calculator.updateBalance(deposits);

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1000.83, 1002.50, 1004.17, 1000.00);
    }

    @Test
    void keepsIdPlanTypeAndDays() {
        TimeDeposit deposit = new TimeDeposit(7, "premium", 1000.00, 46);

        calculator.updateBalance(List.of(deposit));

        assertThat(deposit.getId()).isEqualTo(7);
        assertThat(deposit.getPlanType()).isEqualTo("premium");
        assertThat(deposit.getDays()).isEqualTo(46);
    }

    @Test
    void acceptsAnEmptyList() {
        List<TimeDeposit> deposits = new ArrayList<>();

        calculator.updateBalance(deposits);

        assertThat(deposits).isEmpty();
    }
}

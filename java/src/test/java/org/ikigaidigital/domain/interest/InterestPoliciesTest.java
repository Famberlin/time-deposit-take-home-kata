package org.ikigaidigital.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterestPoliciesTest {

    private final InterestPolicies policies = InterestPolicies.defaults();

    @ParameterizedTest(name = "{0} plan, {1} days -> {2}")
    @CsvSource({
        "basic,     30, 0",
        "basic,     31, 10",
        "student,   30, 0",
        "student,  365, 30",
        "student,  366, 0",
        "premium,   45, 0",
        "premium,   46, 50",
        "unknown,  100, 0",
    })
    void appliesGracePeriodAndPlanRules(String planType, int days, double expected) {
        double interest = policies.monthlyInterest(new TimeDeposit(1, planType, 12_000.00, days));

        assertThat(interest).isEqualTo(expected);
    }

    @Test
    void returnsUnroundedInterest() {
        double interest = policies.monthlyInterest(new TimeDeposit(1, "basic", 1000.00, 31));

        assertThat(interest).isEqualTo(1000.00 * 0.01 / 12);
    }

    @Test
    void rejectsTwoPoliciesForTheSamePlan() {
        assertThatIllegalStateException()
                .isThrownBy(() -> InterestPolicies.of(new BasicInterestPolicy(), new BasicInterestPolicy()));
    }

    @Test
    void newPlanNeedsOnlyANewPolicy() {
        InterestPolicy gold = new AnnualRateInterestPolicy("gold", 0.12) {
            @Override
            protected boolean isEligible(int days) {
                return days > 60;
            }
        };
        TimeDepositCalculator calculator = new TimeDepositCalculator(InterestPolicies.of(gold));
        TimeDeposit eligible = new TimeDeposit(1, "gold", 1000.00, 61);
        TimeDeposit waiting = new TimeDeposit(2, "gold", 1000.00, 60);
        TimeDeposit basic = new TimeDeposit(3, "basic", 1000.00, 61);

        calculator.updateBalance(List.of(eligible, waiting, basic));

        assertThat(eligible.getBalance()).isEqualTo(1010.00);
        assertThat(waiting.getBalance()).isEqualTo(1000.00);
        assertThat(basic.getBalance()).as("not registered").isEqualTo(1000.00);
    }
}

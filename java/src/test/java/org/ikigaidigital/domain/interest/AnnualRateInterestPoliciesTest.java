package org.ikigaidigital.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.Test;

class AnnualRateInterestPoliciesTest {

    private static double interest(InterestPolicy policy, int days) {
        return policy.monthlyInterest(new TimeDeposit(1, policy.planType(), 1200.00, days));
    }

    @Test
    void basicPaysOnePercentAYear() {
        BasicInterestPolicy basic = new BasicInterestPolicy();

        assertThat(basic.planType()).isEqualTo("basic");
        assertThat(interest(basic, 31)).isEqualTo(1.00);
        assertThat(interest(basic, 10_000)).isEqualTo(1.00);
    }

    @Test
    void studentPaysThreePercentAYearUntilDay365() {
        StudentInterestPolicy student = new StudentInterestPolicy();

        assertThat(student.planType()).isEqualTo("student");
        assertThat(interest(student, 365)).isEqualTo(3.00);
        assertThat(interest(student, 366)).isZero();
    }

    @Test
    void premiumPaysFivePercentAYearFromDay46() {
        PremiumInterestPolicy premium = new PremiumInterestPolicy();

        assertThat(premium.planType()).isEqualTo("premium");
        assertThat(interest(premium, 45)).isZero();
        assertThat(interest(premium, 46)).isEqualTo(5.00);
    }
}

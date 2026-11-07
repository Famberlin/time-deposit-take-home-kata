package org.ikigaidigital;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.ikigaidigital.domain.interest.InterestPolicies;

/** Applies monthly interest to time deposits. The interest rules live in {@link InterestPolicies}. */
public class TimeDepositCalculator {

    private final InterestPolicies interestPolicies;

    /** Uses the default basic, student and premium plans. */
    public TimeDepositCalculator() {
        this(InterestPolicies.defaults());
    }

    /** Uses custom interest policies, for example to add a plan. */
    public TimeDepositCalculator(InterestPolicies interestPolicies) {
        this.interestPolicies = interestPolicies;
    }

    /**
     * Adds one month of interest to the balance of every deposit. The interest is rounded HALF_UP to
     * two decimals before it is added. Unknown plan types are left unchanged.
     *
     * <p>TODO: {@code days} is not advanced, so calling this repeatedly never moves a deposit across a
     * threshold (for example, a student deposit never stops earning). Kept to preserve the original
     * behaviour; advancing time belongs to whoever schedules the monthly run.
     */
    public void updateBalance(List<TimeDeposit> xs) {
        for (TimeDeposit deposit : xs) {
            BigDecimal interest =
                    new BigDecimal(interestPolicies.monthlyInterest(deposit)).setScale(2, RoundingMode.HALF_UP);
            deposit.setBalance(deposit.getBalance() + interest.doubleValue());
        }
    }
}

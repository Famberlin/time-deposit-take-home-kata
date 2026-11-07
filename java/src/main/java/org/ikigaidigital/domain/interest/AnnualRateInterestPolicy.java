package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;

/** Pays one twelfth of a fixed annual rate on the balance while the deposit is eligible. */
public abstract class AnnualRateInterestPolicy implements InterestPolicy {

    private static final int MONTHS_PER_YEAR = 12;

    private final String planType;
    private final double annualRate;

    protected AnnualRateInterestPolicy(String planType, double annualRate) {
        this.planType = planType;
        this.annualRate = annualRate;
    }

    @Override
    public final String planType() {
        return planType;
    }

    @Override
    public final double monthlyInterest(TimeDeposit deposit) {
        if (!isEligible(deposit.getDays())) {
            return 0;
        }
        // Same operation order as the original calculator (balance * rate / 12) so doubles match exactly.
        return deposit.getBalance() * annualRate / MONTHS_PER_YEAR;
    }

    /** Plan-specific eligibility on top of the shared grace period. */
    protected abstract boolean isEligible(int days);
}

package org.ikigaidigital.domain.interest;

/** Premium plan: 5% a year, paid monthly, starting after 45 days. */
public final class PremiumInterestPolicy extends AnnualRateInterestPolicy {

    public static final String PLAN_TYPE = "premium";

    /** Interest starts on day 46. */
    static final int WAITING_PERIOD_DAYS = 45;

    public PremiumInterestPolicy() {
        super(PLAN_TYPE, 0.05);
    }

    @Override
    protected boolean isEligible(int days) {
        return days > WAITING_PERIOD_DAYS;
    }
}

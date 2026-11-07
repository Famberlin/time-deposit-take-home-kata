package org.ikigaidigital.domain.interest;

/** Student plan: 3% a year, paid monthly, no interest after one year. */
public final class StudentInterestPolicy extends AnnualRateInterestPolicy {

    public static final String PLAN_TYPE = "student";

    /** Last day that earns interest. Day 366 and later earn nothing, as in the original calculator. */
    static final int LAST_ELIGIBLE_DAY = 365;

    public StudentInterestPolicy() {
        super(PLAN_TYPE, 0.03);
    }

    @Override
    protected boolean isEligible(int days) {
        return days <= LAST_ELIGIBLE_DAY;
    }
}

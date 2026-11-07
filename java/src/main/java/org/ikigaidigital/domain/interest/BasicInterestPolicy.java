package org.ikigaidigital.domain.interest;

/** Basic plan: 1% a year, paid monthly. */
public final class BasicInterestPolicy extends AnnualRateInterestPolicy {

    public static final String PLAN_TYPE = "basic";

    public BasicInterestPolicy() {
        super(PLAN_TYPE, 0.01);
    }

    @Override
    protected boolean isEligible(int days) {
        return true;
    }
}

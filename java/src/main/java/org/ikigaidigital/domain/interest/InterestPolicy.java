package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;

/**
 * Interest rule for one plan type (Strategy). Add a plan by adding an implementation and registering
 * it in {@link InterestPolicies}; the calculator does not change.
 */
public interface InterestPolicy {

    /** Plan type this policy handles, matched exactly against {@link TimeDeposit#getPlanType()}. */
    String planType();

    /**
     * Unrounded interest for one month. Called only after the grace period shared by all plans
     * ({@link InterestPolicies#GRACE_PERIOD_DAYS}) has passed.
     */
    double monthlyInterest(TimeDeposit deposit);
}

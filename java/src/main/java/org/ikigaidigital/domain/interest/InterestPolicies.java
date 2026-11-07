package org.ikigaidigital.domain.interest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.ikigaidigital.TimeDeposit;

/** Registry of {@link InterestPolicy} by plan type. Unknown plan types earn no interest. */
public final class InterestPolicies {

    /** No plan earns interest during its first 30 days. */
    public static final int GRACE_PERIOD_DAYS = 30;

    private final Map<String, InterestPolicy> byPlanType;

    private InterestPolicies(List<InterestPolicy> policies) {
        this.byPlanType =
                policies.stream().collect(Collectors.toUnmodifiableMap(InterestPolicy::planType, Function.identity()));
    }

    /** Basic, student and premium plans as specified by XA Bank. */
    public static InterestPolicies defaults() {
        return of(new BasicInterestPolicy(), new StudentInterestPolicy(), new PremiumInterestPolicy());
    }

    /**
     * Registry of the given policies.
     *
     * @throws IllegalStateException if two policies handle the same plan type
     */
    public static InterestPolicies of(InterestPolicy... policies) {
        return new InterestPolicies(Arrays.asList(policies));
    }

    /** Unrounded interest for one month; zero during the grace period and for unknown plan types. */
    public double monthlyInterest(TimeDeposit deposit) {
        if (deposit.getDays() <= GRACE_PERIOD_DAYS) {
            return 0;
        }
        InterestPolicy policy = byPlanType.get(deposit.getPlanType());
        return policy == null ? 0 : policy.monthlyInterest(deposit);
    }
}

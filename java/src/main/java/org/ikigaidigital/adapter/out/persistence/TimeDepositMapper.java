package org.ikigaidigital.adapter.out.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.TimeDepositView;
import org.ikigaidigital.application.WithdrawalView;

/** Maps between JPA entities and the application model. */
final class TimeDepositMapper {

    /** Scale of the NUMERIC(19,2) money columns. */
    static final int MONEY_SCALE = 2;

    private TimeDepositMapper() {}

    static TimeDepositView toView(TimeDepositEntity entity) {
        return new TimeDepositView(
                entity.getId(),
                entity.getPlanType(),
                entity.getBalance(),
                entity.getDays(),
                entity.getWithdrawals().stream().map(TimeDepositMapper::toView).toList());
    }

    static WithdrawalView toView(WithdrawalEntity entity) {
        return new WithdrawalView(entity.getId(), entity.getAmount(), entity.getDate());
    }

    static TimeDeposit toTimeDeposit(TimeDepositEntity entity) {
        return new TimeDeposit(
                entity.getId(), entity.getPlanType(), entity.getBalance().doubleValue(), entity.getDays());
    }

    /**
     * {@code TimeDeposit} keeps its {@code Double} balance (shared API). {@link BigDecimal#valueOf(double)}
     * takes the shortest decimal form of the double, so 1000.83 is stored as 1000.83 and not as its
     * binary approximation; the scale matches the column.
     */
    static BigDecimal toMoney(Double balance) {
        return BigDecimal.valueOf(balance).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}

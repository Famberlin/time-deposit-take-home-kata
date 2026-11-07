package org.ikigaidigital.application;

import java.math.BigDecimal;
import java.util.List;

/** Read model of a time deposit as returned to clients. */
public record TimeDepositView(int id, String planType, BigDecimal balance, int days, List<WithdrawalView> withdrawals) {

    public TimeDepositView {
        withdrawals = List.copyOf(withdrawals);
    }
}

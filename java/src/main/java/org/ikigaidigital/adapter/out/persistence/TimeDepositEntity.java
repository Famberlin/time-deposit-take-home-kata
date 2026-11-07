package org.ikigaidigital.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Row of {@code time_deposits}. */
@Entity
@Table(name = "time_deposits")
class TimeDepositEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Column(nullable = false)
    private int days;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @OneToMany(mappedBy = "timeDeposit")
    @OrderBy("id")
    private List<WithdrawalEntity> withdrawals = new ArrayList<>();

    protected TimeDepositEntity() {}

    TimeDepositEntity(String planType, int days, BigDecimal balance) {
        this.planType = planType;
        this.days = days;
        this.balance = balance;
    }

    Integer getId() {
        return id;
    }

    String getPlanType() {
        return planType;
    }

    int getDays() {
        return days;
    }

    BigDecimal getBalance() {
        return balance;
    }

    void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    List<WithdrawalEntity> getWithdrawals() {
        return withdrawals;
    }
}

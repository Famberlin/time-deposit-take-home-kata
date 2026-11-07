package org.ikigaidigital.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read model of a withdrawal from a time deposit. */
public record WithdrawalView(int id, BigDecimal amount, LocalDate date) {}

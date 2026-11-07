package org.ikigaidigital.application.port.in;

/** Adds one month of interest to the balance of every stored time deposit. */
public interface UpdateAllBalancesUseCase {

    void updateAllBalances();
}

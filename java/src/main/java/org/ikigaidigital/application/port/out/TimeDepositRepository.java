package org.ikigaidigital.application.port.out;

import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.TimeDepositView;

/** Storage of time deposits, implemented by the persistence adapter. */
public interface TimeDepositRepository {

    /** All deposits with their withdrawals, ordered by id. */
    List<TimeDepositView> loadAll();

    /**
     * All deposits without withdrawals, with their rows locked until the surrounding transaction ends,
     * so a concurrent balance update waits instead of applying interest to the same balance twice.
     */
    List<TimeDeposit> lockAllForUpdate();

    /** Stores the balance of each given deposit, matched by id. Other fields are not written. */
    void saveBalances(List<TimeDeposit> deposits);
}

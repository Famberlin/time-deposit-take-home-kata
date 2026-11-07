package org.ikigaidigital.application.port.out;

import java.util.List;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.TimeDepositView;

/** Storage of time deposits, implemented by the persistence adapter. */
public interface TimeDepositRepository {

    /** All deposits with their withdrawals, ordered by id. */
    List<TimeDepositView> loadAll();

    /**
     * Up to {@code limit} deposits with an id greater than {@code afterId}, ordered by id and without
     * withdrawals. Their rows stay locked until the surrounding transaction ends, so a concurrent
     * balance update waits for them instead of applying interest to the same balance twice.
     */
    List<TimeDeposit> lockBatchForUpdate(int afterId, int limit);

    /** Stores the balance of each given deposit, matched by id. Other fields are not written. */
    void saveBalances(List<TimeDeposit> deposits);
}

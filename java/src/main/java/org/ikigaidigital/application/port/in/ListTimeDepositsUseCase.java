package org.ikigaidigital.application.port.in;

import java.util.List;
import org.ikigaidigital.application.TimeDepositView;

/** Lists every stored time deposit with its withdrawals. */
public interface ListTimeDepositsUseCase {

    List<TimeDepositView> listAll();
}

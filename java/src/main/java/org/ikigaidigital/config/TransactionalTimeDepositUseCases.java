package org.ikigaidigital.config;

import java.util.List;
import org.ikigaidigital.application.TimeDepositService;
import org.ikigaidigital.application.TimeDepositView;
import org.ikigaidigital.application.port.in.ListTimeDepositsUseCase;
import org.ikigaidigital.application.port.in.UpdateAllBalancesUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs each use case in one transaction (Decorator), so the framework-free {@link
 * TimeDepositService} needs no Spring annotations.
 */
class TransactionalTimeDepositUseCases implements UpdateAllBalancesUseCase, ListTimeDepositsUseCase {

    private final TimeDepositService service;
    private final TransactionTemplate readWrite;
    private final TransactionTemplate readOnly;

    TransactionalTimeDepositUseCases(TimeDepositService service, PlatformTransactionManager transactionManager) {
        this.service = service;
        this.readWrite = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public void updateAllBalances() {
        readWrite.executeWithoutResult(status -> service.updateAllBalances());
    }

    @Override
    public List<TimeDepositView> listAll() {
        return readOnly.execute(status -> service.listAll());
    }
}

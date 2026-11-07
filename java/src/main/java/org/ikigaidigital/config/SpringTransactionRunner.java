package org.ikigaidigital.config;

import java.util.function.Supplier;
import org.ikigaidigital.application.port.out.TransactionRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link TransactionRunner} on Spring's transaction management. */
class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate readWrite;
    private final TransactionTemplate readOnly;

    SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.readWrite = new TransactionTemplate(transactionManager);
        this.readWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return readWrite.execute(status -> work.get());
    }

    @Override
    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }
}

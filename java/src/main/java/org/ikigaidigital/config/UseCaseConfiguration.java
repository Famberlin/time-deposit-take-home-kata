package org.ikigaidigital.config;

import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.TimeDepositService;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Wires the framework-free application core into Spring. */
@Configuration(proxyBeanMethods = false)
class UseCaseConfiguration {

    @Bean
    TimeDepositCalculator timeDepositCalculator() {
        return new TimeDepositCalculator();
    }

    /** One bean implements both inbound ports, each call in its own transaction. */
    @Bean
    TransactionalTimeDepositUseCases timeDepositUseCases(
            TimeDepositRepository repository,
            TimeDepositCalculator calculator,
            PlatformTransactionManager transactionManager) {
        return new TransactionalTimeDepositUseCases(new TimeDepositService(repository, calculator), transactionManager);
    }
}

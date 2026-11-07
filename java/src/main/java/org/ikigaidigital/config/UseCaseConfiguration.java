package org.ikigaidigital.config;

import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.TimeDepositService;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.application.port.out.TransactionRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Wires the framework-free application core into Spring. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BalanceUpdateProperties.class)
class UseCaseConfiguration {

    @Bean
    TimeDepositCalculator timeDepositCalculator() {
        return new TimeDepositCalculator();
    }

    @Bean
    TransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
        return new SpringTransactionRunner(transactionManager);
    }

    /** One bean implements both inbound ports. */
    @Bean
    TimeDepositService timeDepositService(
            TimeDepositRepository repository,
            TransactionRunner transactions,
            TimeDepositCalculator calculator,
            BalanceUpdateProperties properties) {
        return new TimeDepositService(repository, transactions, calculator, properties.batchSize());
    }
}

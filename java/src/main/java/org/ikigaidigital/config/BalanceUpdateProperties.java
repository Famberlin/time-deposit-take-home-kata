package org.ikigaidigital.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of {@code PUT /api/v1/time-deposits/balances}.
 *
 * @param batchSize deposits locked and updated per transaction ({@code
 *     time-deposits.balance-update.batch-size}, env {@code TIME_DEPOSITS_BALANCE_UPDATE_BATCH_SIZE})
 */
@ConfigurationProperties("time-deposits.balance-update")
public record BalanceUpdateProperties(@DefaultValue("500") int batchSize) {}

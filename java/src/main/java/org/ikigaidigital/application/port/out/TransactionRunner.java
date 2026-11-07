package org.ikigaidigital.application.port.out;

import java.util.function.Supplier;

/** Runs work in a transaction, so the application core needs no transaction framework. */
public interface TransactionRunner {

    /** Runs {@code work} in a new read-write transaction that commits when it returns. */
    <T> T inTransaction(Supplier<T> work);

    /** Runs {@code work} in a read-only transaction. */
    <T> T inReadOnlyTransaction(Supplier<T> work);
}

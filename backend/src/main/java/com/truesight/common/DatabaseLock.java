package com.truesight.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A lock that holds across every server, because PostgreSQL keeps it. It needs no table, and ends by itself when the
 * transaction does, including when a server dies. Two keys sharing a hash only make one wait, which is harmless.
 */
@Component
public class DatabaseLock {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Waits until no other transaction holds {@code key}, then holds it until this transaction ends.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException if no transaction is running
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String key) {
        // The function returns void, which JPA cannot read, so its result is cast to text.
        entityManager.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(hashtext(:key)) AS text)")
                .setParameter("key", key)
                .getSingleResult();
    }

    /**
     * Takes {@code key} if nobody else holds it, and holds it until this transaction ends; never waits.
     *
     * @return false when another transaction holds it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryLock(String key) {
        return (Boolean) entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key)
                .getSingleResult();
    }
}

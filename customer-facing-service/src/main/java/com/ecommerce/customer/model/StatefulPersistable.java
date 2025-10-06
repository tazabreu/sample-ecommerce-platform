package com.ecommerce.customer.model;

import org.springframework.data.domain.Persistable;

/**
 * Persistable variant that allows marking an aggregate as persisted after save/load operations.
 */
public interface StatefulPersistable<ID> extends Persistable<ID> {

    void markPersisted();
}

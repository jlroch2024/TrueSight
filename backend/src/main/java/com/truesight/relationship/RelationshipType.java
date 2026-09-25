package com.truesight.relationship;

/**
 * Which way round a relationship goes, seen from the company whose report it came from.
 *
 * <p>On the graph, the arrow always points from the supplier to the customer.
 */
public enum RelationshipType {
    /** The counterparty supplies the company. Arrow: counterparty to company. */
    SUPPLIER,
    /** The counterparty buys from the company. Arrow: company to counterparty. */
    CUSTOMER
}

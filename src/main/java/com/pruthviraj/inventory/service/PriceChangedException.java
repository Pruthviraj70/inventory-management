package com.pruthviraj.inventory.service;

import com.pruthviraj.inventory.model.Models.Quote;

/** Thrown before any writes; the UI asks for acceptance after transaction rollback releases locks. */
public final class PriceChangedException extends RuntimeException {
    private final Quote revised;
    public PriceChangedException(Quote revised) { super("Stock costs changed. Please review the revised prices and total."); this.revised=revised; }
    public Quote revised() { return revised; }
}

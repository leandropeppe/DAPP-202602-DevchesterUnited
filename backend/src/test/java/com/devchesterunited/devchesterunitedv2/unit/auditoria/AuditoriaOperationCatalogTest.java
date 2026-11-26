package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaOperationCatalog;
import org.junit.jupiter.api.Test;

class AuditoriaOperationCatalogTest {
    @Test void containsExactlyTheTenSelectedOperations() {
        assertEquals(10, AuditoriaOperationCatalog.operations().size());
        assertTrue(AuditoriaOperationCatalog.isSupported("catalog.sync"));
        assertTrue(AuditoriaOperationCatalog.isSupported("statistics.sync"));
        assertTrue(AuditoriaOperationCatalog.isSupported("valuation.manual"));
        assertTrue(AuditoriaOperationCatalog.isSupported("valuation.weekly"));
        assertTrue(AuditoriaOperationCatalog.isSupported("market.purchase"));
        assertTrue(AuditoriaOperationCatalog.isSupported("market.sale"));
        assertTrue(AuditoriaOperationCatalog.isSupported("market.p2p.offer.publish"));
        assertTrue(AuditoriaOperationCatalog.isSupported("market.p2p.purchase"));
        assertTrue(AuditoriaOperationCatalog.isSupported("market.p2p.offer.cancel"));
        assertTrue(AuditoriaOperationCatalog.isSupported("market.initialize"));
        assertFalse(AuditoriaOperationCatalog.isSupported("service.anything"));
    }
}

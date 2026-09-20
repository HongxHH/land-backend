package com.gov.landcheck.core.config.mongo;

import org.bson.Document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class MongoReplicaSetRequirementTest {

    @Test
    void replicaSetHelloIsCapable() {
        Document hello = new Document("setName", "rs0").append("ismaster", true);
        assertTrue(MongoReplicaSetRequirement.supportsMultiDocumentTransactions(hello));
        assertEquals("replicaSet=rs0", MongoReplicaSetRequirement.describeTopology(hello));
    }

    @Test
    void mongosHelloIsCapable() {
        Document hello = new Document("msg", "isdbgrid");
        assertTrue(MongoReplicaSetRequirement.supportsMultiDocumentTransactions(hello));
        assertEquals("mongos", MongoReplicaSetRequirement.describeTopology(hello));
    }

    @Test
    void standaloneHelloIsRejected() {
        Document hello = new Document("ismaster", true).append("maxWireVersion", 21);
        assertFalse(MongoReplicaSetRequirement.supportsMultiDocumentTransactions(hello));
        assertEquals("standalone", MongoReplicaSetRequirement.describeTopology(hello));
    }

    @Test
    void blankSetNameIsRejected() {
        assertFalse(MongoReplicaSetRequirement.supportsMultiDocumentTransactions(new Document("setName", "  ")));
        assertFalse(MongoReplicaSetRequirement.supportsMultiDocumentTransactions(null));
    }
}

package org.keyfort.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ledger_blocks")
public class LedgerBlockEntity {

    @Id
    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "payload", nullable = false, length = 1024)
    private String payload;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    public LedgerBlockEntity() {}

    public LedgerBlockEntity(Long seq, Instant at, String type, String payload, String prevHash, String hash) {
        this.seq = seq;
        this.at = at;
        this.type = type;
        this.payload = payload;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }

    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
}

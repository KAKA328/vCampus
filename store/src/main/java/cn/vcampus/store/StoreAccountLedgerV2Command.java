package cn.vcampus.store;

import java.io.Serializable;

/** Wallet ledger V2 explicitly supports library compensation transactions. */
public final class StoreAccountLedgerV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;

    public StoreAccountLedgerV2Command(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        this.token = token.trim();
    }

    public String getToken() { return token; }
}

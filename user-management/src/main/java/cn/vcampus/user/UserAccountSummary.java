package cn.vcampus.user;

import cn.vcampus.common.Role;
import java.io.Serializable;
import java.time.Instant;

/** Display row for administrator account-list screens. */
public final class UserAccountSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String displayName;
    private final Role role;
    private final boolean active;
    private final String profileId;
    private final String createdBy;
    private final Instant createdAt;
    private final String importBatchId;

    public UserAccountSummary(UserAccount account, String profileId) {
        this(account.getUser().getUserId(), account.getUser().getDisplayName(), account.getUser().getRole(),
                account.isActive(), profileId, account.getCreatedBy(), account.getCreatedAt(),
                account.getImportBatchId());
    }

    public UserAccountSummary(String userId, String displayName, Role role, boolean active,
                              String profileId, String createdBy, Instant createdAt, String importBatchId) {
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
        this.profileId = profileId == null ? "" : profileId;
        this.createdBy = createdBy == null ? "" : createdBy;
        this.createdAt = createdAt;
        this.importBatchId = importBatchId == null ? "" : importBatchId;
    }

    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public String getProfileId() { return profileId; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getImportBatchId() { return importBatchId; }
    public String getStatusText() { return active ? "正常" : "停用"; }
}

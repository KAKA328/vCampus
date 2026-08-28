package cn.vcampus.user;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Summary returned after an admin batch-imports user accounts. */
public final class UserImportResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String importBatchId;
    private final int totalCount;
    private final int successCount;
    private final List<UserImportFailure> failures;

    public UserImportResult(String importBatchId, int totalCount, int successCount, List<UserImportFailure> failures) {
        if (importBatchId == null || importBatchId.trim().isEmpty()) {
            throw new IllegalArgumentException("importBatchId must not be blank");
        }
        if (totalCount < 0 || successCount < 0 || successCount > totalCount) {
            throw new IllegalArgumentException("invalid import counts");
        }
        this.importBatchId = importBatchId.trim();
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failures = Collections.unmodifiableList(new ArrayList<UserImportFailure>(
                failures == null ? Collections.<UserImportFailure>emptyList() : failures));
    }

    public String getImportBatchId() { return importBatchId; }
    public int getTotalCount() { return totalCount; }
    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failures.size(); }
    public List<UserImportFailure> getFailures() { return failures; }
}

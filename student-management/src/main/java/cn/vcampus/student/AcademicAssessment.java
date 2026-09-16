package cn.vcampus.student;

import cn.vcampus.common.CreditFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** Complete, persisted academic assessment; final graduation requires a separate human decision. */
public final class AcademicAssessment implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final CreditSummary credits;
    /** Legacy serialized field. Keep name and type for Java wire compatibility. */
    private final int requiredCredits;
    private final BigDecimal requiredCreditsDecimal;
    private final String evidence;
    private final String reviewedBy;
    private final Instant reviewedAt;
    private final String basis;
    private final String graduatedBy;
    private final Instant graduatedAt;
    private final String graduationNote;

    public AcademicAssessment(String id, CreditSummary credits, int requiredCredits, String evidence,
            String reviewedBy, Instant reviewedAt, String basis, String graduatedBy,
            Instant graduatedAt, String graduationNote) {
        this(id, credits, BigDecimal.valueOf(requiredCredits), evidence, reviewedBy, reviewedAt,
                basis, graduatedBy, graduatedAt, graduationNote);
    }
    public AcademicAssessment(String id, CreditSummary credits, BigDecimal requiredCredits, String evidence,
            String reviewedBy, Instant reviewedAt, String basis, String graduatedBy,
            Instant graduatedAt, String graduationNote) {
        this.id = id; this.credits = credits;
        this.requiredCreditsDecimal = CreditFormat.positive(requiredCredits, "requiredCredits");
        this.requiredCredits = CreditFormat.legacyInt(this.requiredCreditsDecimal, "requiredCredits");
        this.evidence = evidence; this.reviewedBy = reviewedBy; this.reviewedAt = reviewedAt;
        this.basis = basis; this.graduatedBy = graduatedBy; this.graduatedAt = graduatedAt;
        this.graduationNote = graduationNote;
    }
    public String getId() { return id; }
    public String getStudentId() { return credits.getStudentId(); }
    public CreditSummary getCredits() { return credits; }
    public int getRequiredCredits() { return requiredCredits; }
    public BigDecimal getRequiredCreditsDecimal() {
        return CreditFormat.decimalOrLegacy(requiredCreditsDecimal, requiredCredits);
    }
    public int getShortfall() {
        return CreditFormat.legacyInt(getShortfallDecimal(), "creditShortfall");
    }
    public BigDecimal getShortfallDecimal() {
        return getRequiredCreditsDecimal().subtract(credits.getEarnedCreditsDecimal()).max(BigDecimal.ZERO);
    }
    public boolean isCreditRequirementMet() {
        return getShortfallDecimal().signum() == 0 && credits.getPendingRetakes() == 0;
    }
    public String getEvidence() { return evidence; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getBasis() { return basis; }
    public String getGraduatedBy() { return graduatedBy; }
    public Instant getGraduatedAt() { return graduatedAt; }
    public String getGraduationNote() { return graduationNote; }
    public boolean isGraduated() { return graduatedAt != null; }
    public AcademicAssessment graduate(String actor, String note) {
        return new AcademicAssessment(id, credits, getRequiredCreditsDecimal(), evidence, reviewedBy,
                reviewedAt, basis, actor, Instant.now(), note);
    }
}

package cn.vcampus.student;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable source data used to calculate and fingerprint graduation credit requirements. */
public final class GraduationCreditRequirement implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String planId;
    private final int requiredCredits;
    private final List<String> requiredCourseCredits;

    public GraduationCreditRequirement(String planId, int requiredCredits,
            List<String> requiredCourseCredits) {
        if (planId == null || planId.trim().isEmpty()) {
            throw new IllegalArgumentException("planId must not be blank");
        }
        if (requiredCredits <= 0) {
            throw new IllegalArgumentException("requiredCredits must be positive");
        }
        if (requiredCourseCredits == null || requiredCourseCredits.isEmpty()) {
            throw new IllegalArgumentException("requiredCourseCredits must not be empty");
        }
        List<String> copied = new ArrayList<String>();
        for (String course : requiredCourseCredits) {
            if (course == null || course.trim().isEmpty()) {
                throw new IllegalArgumentException("requiredCourseCredits must not contain blanks");
            }
            copied.add(course.trim());
        }
        Collections.sort(copied);
        this.planId = planId.trim();
        this.requiredCredits = requiredCredits;
        this.requiredCourseCredits = Collections.unmodifiableList(copied);
    }

    public String getPlanId() { return planId; }
    public int getRequiredCredits() { return requiredCredits; }
    public List<String> getRequiredCourseCredits() { return requiredCourseCredits; }

    /** Stable text included in the assessment evidence fingerprint. */
    public String fingerprint() {
        StringBuilder value = new StringBuilder();
        value.append(planId.length()).append(':').append(planId)
                .append(':').append(requiredCredits);
        for (String course : requiredCourseCredits) {
            value.append(':').append(course.length()).append(':').append(course);
        }
        return value.toString();
    }
}

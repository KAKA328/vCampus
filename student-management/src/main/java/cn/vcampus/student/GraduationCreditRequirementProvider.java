package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;

/** Resolves the published training-plan requirement applicable to one student. */
public interface GraduationCreditRequirementProvider {
    ServiceResult<GraduationCreditRequirement> findFor(StudentRecord student);

    /**
     * Resolves the requirement retained for a completed graduation snapshot.
     * Default keeps existing providers restricted to published plans.
     */
    default ServiceResult<GraduationCreditRequirement> findHistoricalFor(StudentRecord student) {
        return findFor(student);
    }
}

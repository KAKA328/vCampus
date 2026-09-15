package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;

/** Resolves the published training-plan requirement applicable to one student. */
public interface GraduationCreditRequirementProvider {
    ServiceResult<GraduationCreditRequirement> findFor(StudentRecord student);
}

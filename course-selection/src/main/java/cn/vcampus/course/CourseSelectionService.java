package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.time.LocalDateTime;
import java.util.List;

/** 当前选课流程的业务接口，所有学生身份信息都由服务端资料适配层提供。 */
public interface CourseSelectionService {
    ServiceResult<List<SelectionRound>> listAvailableRounds(StudentSelectionProfile student,
            LocalDateTime time);
    ServiceResult<List<SelectableCourseOffering>> listAvailableOfferings(
            StudentSelectionProfile student, String roundId, LocalDateTime time);
    ServiceResult<CourseSelectionRecord> select(StudentSelectionProfile student, String roundId,
            String offeringId, LocalDateTime time);
    ServiceResult<CourseSelectionRecord> drop(StudentSelectionProfile student, String recordId,
            LocalDateTime time);
    ServiceResult<List<SelectedCourseOffering>> listSelectedOfferings(
            StudentSelectionProfile student);
}

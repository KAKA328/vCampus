package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Course selection contract. */
public interface CourseSelectionService {
    ServiceResult<List<Course>> listCourses();
    ServiceResult<Void> select(String studentId, String courseId);
    ServiceResult<Void> drop(String studentId, String courseId);
    ServiceResult<List<Course>> selectedCourses(String studentId);
    ServiceResult<List<SelectionRound>> listRounds(String term);
    ServiceResult<List<CourseOffering>> listOfferings(String roundId, String courseId);
    ServiceResult<CourseSelectionRecord> selectOffering(String studentId, String roundId, String offeringId);
    ServiceResult<Void> dropRecord(String studentId, String recordId);
    ServiceResult<List<CourseSelectionRecord>> selectedRecords(String studentId, String term);
}

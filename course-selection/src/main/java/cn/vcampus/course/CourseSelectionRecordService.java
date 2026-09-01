package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 选课记录的保存和查询接口。
 *
 * <p>未来的正式选课业务会先完成资格、轮次、容量和时间冲突校验，再调用本接口保存记录。</p>
 */
public interface CourseSelectionRecordService {
    ServiceResult<CourseSelectionRecord> create(CourseSelectionRecord record);
    ServiceResult<CourseSelectionRecord> findById(String recordId);
    ServiceResult<List<CourseSelectionRecord>> listByStudent(String studentId);
    ServiceResult<List<CourseSelectionRecord>> listActiveByStudent(String studentId);
    ServiceResult<List<CourseSelectionRecord>> listByOffering(String offeringId);
    ServiceResult<List<CourseSelectionRecord>> listActiveByOffering(String offeringId);
    ServiceResult<CourseSelectionRecord> markDropped(String recordId, LocalDateTime droppedAt);
}

package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * 批量读取多个教学班的有效选课记录，供课程列表一次性计算容量使用。
     *
     * <p>空集合返回空结果，不以空集合代表查询失败。</p>
     */
    ServiceResult<List<CourseSelectionRecord>> listActiveByOfferingIds(
            Collection<String> offeringIds);
    ServiceResult<CourseSelectionRecord> markDropped(String recordId, LocalDateTime droppedAt);
}

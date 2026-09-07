package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** 供教务审核流程原子写入一批已确认正式成绩的学籍接口。 */
public interface CourseResultRecordingService {
    ServiceResult<Integer> nextAttemptNo(String studentId, String courseId);

    /** 整批写入，任一记录失败时不得留下部分正式成绩。 */
    ServiceResult<Void> recordAll(List<FormalCourseResult> results);

    /**
     * 撤销一批因成绩更正而失效的正式成绩。调用方必须已完成权限和来源校验。
     * 任一记录不存在或删除失败时不得留下部分撤销结果。
     */
    ServiceResult<Void> retractAll(List<FormalCourseResult> results);
}

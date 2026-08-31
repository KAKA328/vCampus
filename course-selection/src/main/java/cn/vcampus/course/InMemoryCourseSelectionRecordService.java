package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于开发和测试的内存选课记录服务。
 *
 * <p>该服务只保存和查询记录，不负责学生资格、教学班容量或选课轮次开放校验；这些规则将在
 * 后续正式选课服务中完成。</p>
 */
public final class InMemoryCourseSelectionRecordService implements CourseSelectionRecordService {
    private final Map<String, CourseSelectionRecord> recordsById;

    public InMemoryCourseSelectionRecordService() {
        this(Collections.<CourseSelectionRecord>emptyList());
    }

    /**
     * 使用已有记录创建服务，便于测试或加载演示数据。
     */
    public InMemoryCourseSelectionRecordService(List<CourseSelectionRecord> records) {
        if (records == null) {
            throw new IllegalArgumentException("records must not be null");
        }
        this.recordsById = new LinkedHashMap<String, CourseSelectionRecord>();
        for (CourseSelectionRecord record : records) {
            if (record == null) {
                throw new IllegalArgumentException("records must not contain null");
            }
            if (recordsById.put(record.getRecordId(), record) != null) {
                throw new IllegalArgumentException("duplicate recordId: " + record.getRecordId());
            }
        }
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> create(CourseSelectionRecord record) {
        if (record == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "record must not be null");
        }
        if (recordsById.containsKey(record.getRecordId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "selection record already exists");
        }
        if (record.isActive() && hasActiveSelection(record.getStudentId(), record.getOfferingId())) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "student already has an active selection for this offering");
        }
        recordsById.put(record.getRecordId(), record);
        return ServiceResult.ok(record);
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> findById(String recordId) {
        String normalizedRecordId = normalize(recordId);
        if (normalizedRecordId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "recordId must not be blank");
        }
        CourseSelectionRecord record = recordsById.get(normalizedRecordId);
        if (record == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "selection record not found");
        }
        return ServiceResult.ok(record);
    }

    @Override
    public synchronized ServiceResult<List<CourseSelectionRecord>> listByStudent(String studentId) {
        return listByStudentAndStatus(studentId, null);
    }

    @Override
    public synchronized ServiceResult<List<CourseSelectionRecord>> listActiveByStudent(
            String studentId) {
        return listByStudentAndStatus(studentId, SelectionRecordStatus.ACTIVE);
    }

    @Override
    public synchronized ServiceResult<List<CourseSelectionRecord>> listByOffering(String offeringId) {
        return listByOfferingAndStatus(offeringId, null);
    }

    @Override
    public synchronized ServiceResult<List<CourseSelectionRecord>> listActiveByOffering(
            String offeringId) {
        return listByOfferingAndStatus(offeringId, SelectionRecordStatus.ACTIVE);
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> markDropped(String recordId,
            LocalDateTime droppedAt) {
        String normalizedRecordId = normalize(recordId);
        if (normalizedRecordId == null || droppedAt == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "recordId and droppedAt must not be null");
        }
        CourseSelectionRecord existing = recordsById.get(normalizedRecordId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "selection record not found");
        }
        try {
            CourseSelectionRecord dropped = existing.withDroppedAt(droppedAt);
            recordsById.put(normalizedRecordId, dropped);
            return ServiceResult.ok(dropped);
        } catch (IllegalArgumentException invalidTime) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidTime.getMessage());
        } catch (IllegalStateException alreadyDropped) {
            return ServiceResult.failure(StatusCode.CONFLICT, alreadyDropped.getMessage());
        }
    }

    private boolean hasActiveSelection(String studentId, String offeringId) {
        for (CourseSelectionRecord existing : recordsById.values()) {
            if (existing.isActive()
                    && studentId.equals(existing.getStudentId())
                    && offeringId.equals(existing.getOfferingId())) {
                return true;
            }
        }
        return false;
    }

    private ServiceResult<List<CourseSelectionRecord>> listByStudentAndStatus(String studentId,
            SelectionRecordStatus requiredStatus) {
        String normalizedStudentId = normalize(studentId);
        if (normalizedStudentId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        List<CourseSelectionRecord> records = new ArrayList<CourseSelectionRecord>();
        for (CourseSelectionRecord record : recordsById.values()) {
            if (normalizedStudentId.equals(record.getStudentId())
                    && (requiredStatus == null || requiredStatus == record.getStatus())) {
                records.add(record);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(records));
    }

    private ServiceResult<List<CourseSelectionRecord>> listByOfferingAndStatus(String offeringId,
            SelectionRecordStatus requiredStatus) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offeringId must not be blank");
        }
        List<CourseSelectionRecord> records = new ArrayList<CourseSelectionRecord>();
        for (CourseSelectionRecord record : recordsById.values()) {
            if (normalizedOfferingId.equals(record.getOfferingId())
                    && (requiredStatus == null || requiredStatus == record.getStatus())) {
                records.add(record);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(records));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

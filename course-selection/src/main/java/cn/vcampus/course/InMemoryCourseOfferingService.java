package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于开发和测试的内存教学班管理服务。
 *
 * <p>程序关闭后数据会丢失。后续接入 Access 数据库时，应保持
 * {@link CourseOfferingService} 接口不变。</p>
 */
public final class InMemoryCourseOfferingService implements CourseOfferingService {
    private final Map<String, CourseOffering> offeringsById;
    private final CourseCatalogService courseCatalog;

    public InMemoryCourseOfferingService() {
        this(Collections.<CourseOffering>emptyList(), null);
    }

    /**
     * 使用已有教学班创建服务，便于测试或加载演示数据。
     */
    public InMemoryCourseOfferingService(List<CourseOffering> offerings) {
        this(offerings, null);
    }

    /**
     * 使用课程目录创建教学班服务。传入目录后，新建教学班会校验课程是否存在且已启用。
     */
    public InMemoryCourseOfferingService(CourseCatalogService courseCatalog) {
        this(Collections.<CourseOffering>emptyList(), courseCatalog);
    }

    public InMemoryCourseOfferingService(List<CourseOffering> offerings,
            CourseCatalogService courseCatalog) {
        if (offerings == null) {
            throw new IllegalArgumentException("offerings must not be null");
        }
        this.courseCatalog = courseCatalog;
        this.offeringsById = new LinkedHashMap<String, CourseOffering>();
        for (CourseOffering offering : offerings) {
            if (offering == null) {
                throw new IllegalArgumentException("offerings must not contain null");
            }
            if (offeringsById.put(offering.getOfferingId(), offering) != null) {
                throw new IllegalArgumentException(
                        "duplicate offeringId: " + offering.getOfferingId());
            }
        }
    }

    @Override
    public synchronized ServiceResult<CourseOffering> create(CourseOffering offering) {
        if (offering == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offering must not be null");
        }
        ServiceResult<Void> courseResult = requireActiveCourse(offering.getCourseId());
        if (courseResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(courseResult.getStatus(), courseResult.getMessage());
        }
        if (offeringsById.containsKey(offering.getOfferingId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course offering already exists");
        }
        offeringsById.put(offering.getOfferingId(), offering);
        return ServiceResult.ok(offering);
    }

    @Override
    public synchronized ServiceResult<CourseOffering> findById(String offeringId) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offeringId must not be blank");
        }
        CourseOffering offering = offeringsById.get(normalizedOfferingId);
        if (offering == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course offering not found");
        }
        return ServiceResult.ok(offering);
    }

    @Override
    public synchronized ServiceResult<List<CourseOffering>> listByTerm(String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "term must not be blank");
        }
        List<CourseOffering> offerings = new ArrayList<CourseOffering>();
        for (CourseOffering offering : offeringsById.values()) {
            if (normalizedTerm.equals(offering.getTerm())) {
                offerings.add(offering);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(offerings));
    }

    @Override
    public synchronized ServiceResult<List<CourseOffering>> listByCourse(String courseId,
            String term) {
        return listByCourseAndStatus(courseId, term, null);
    }

    @Override
    public synchronized ServiceResult<List<CourseOffering>> listOpenByCourse(String courseId,
            String term) {
        return listByCourseAndStatus(courseId, term, CourseOfferingStatus.OPEN);
    }

    @Override
    public synchronized ServiceResult<CourseOffering> changeStatus(String offeringId,
            CourseOfferingStatus status) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "offeringId and status must not be null");
        }
        CourseOffering existing = offeringsById.get(normalizedOfferingId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course offering not found");
        }
        CourseOffering changed = existing.withStatus(status);
        offeringsById.put(normalizedOfferingId, changed);
        return ServiceResult.ok(changed);
    }

    @Override
    public synchronized ServiceResult<CourseOffering> changeCapacities(String offeringId,
            int requiredCapacity, int electiveCapacity, int crossMajorCapacity) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offeringId must not be blank");
        }
        CourseOffering existing = offeringsById.get(normalizedOfferingId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "course offering not found");
        }
        try {
            CourseOffering changed = existing.withCapacities(requiredCapacity, electiveCapacity,
                    crossMajorCapacity);
            offeringsById.put(normalizedOfferingId, changed);
            return ServiceResult.ok(changed);
        } catch (IllegalArgumentException invalidCapacity) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidCapacity.getMessage());
        }
    }

    private ServiceResult<List<CourseOffering>> listByCourseAndStatus(String courseId,
            String term, CourseOfferingStatus requiredStatus) {
        String normalizedCourseId = normalize(courseId);
        String normalizedTerm = normalize(term);
        if (normalizedCourseId == null || normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "courseId and term must not be blank");
        }
        List<CourseOffering> offerings = new ArrayList<CourseOffering>();
        for (CourseOffering offering : offeringsById.values()) {
            if (normalizedCourseId.equals(offering.getCourseId())
                    && normalizedTerm.equals(offering.getTerm())
                    && (requiredStatus == null || requiredStatus == offering.getStatus())) {
                offerings.add(offering);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(offerings));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ServiceResult<Void> requireActiveCourse(String courseId) {
        if (courseCatalog == null) {
            return ServiceResult.ok(null);
        }
        ServiceResult<Course> courseResult = courseCatalog.findActiveById(courseId);
        return courseResult.getStatus() == StatusCode.OK
                ? ServiceResult.ok(null)
                : ServiceResult.<Void>failure(courseResult.getStatus(), courseResult.getMessage());
    }
}

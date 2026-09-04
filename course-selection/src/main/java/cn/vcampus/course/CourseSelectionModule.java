package cn.vcampus.course;

/**
 * 选课模块在服务器启动时使用的一组服务。
 *
 * <p>学生选课和教务管理必须共享同一份课程目录、教学班和选课记录；因此服务器通过该对象
 * 一次性接收这些服务，而不是分别创建彼此隔离的演示数据。</p>
 */
public final class CourseSelectionModule {
    private final CourseSelectionService selectionService;
    private final CourseCatalogService catalogService;
    private final CourseOfferingService offeringService;
    private final SelectionRoundService selectionRoundService;
    private final CourseSelectionRecordService selectionRecordService;
    private final GradeSubmissionService gradeSubmissionService;

    public CourseSelectionModule(CourseSelectionService selectionService,
            CourseCatalogService catalogService, CourseOfferingService offeringService) {
        this(selectionService, catalogService, offeringService, null, null, null);
    }

    public CourseSelectionModule(CourseSelectionService selectionService,
            CourseCatalogService catalogService, CourseOfferingService offeringService,
            SelectionRoundService selectionRoundService) {
        this(selectionService, catalogService, offeringService, selectionRoundService, null, null);
    }

    public CourseSelectionModule(CourseSelectionService selectionService,
            CourseCatalogService catalogService, CourseOfferingService offeringService,
            SelectionRoundService selectionRoundService,
            CourseSelectionRecordService selectionRecordService) {
        this(selectionService, catalogService, offeringService, selectionRoundService,
                selectionRecordService, null);
    }

    public CourseSelectionModule(CourseSelectionService selectionService,
            CourseCatalogService catalogService, CourseOfferingService offeringService,
            SelectionRoundService selectionRoundService,
            CourseSelectionRecordService selectionRecordService,
            GradeSubmissionService gradeSubmissionService) {
        if (selectionService == null || catalogService == null || offeringService == null) {
            throw new IllegalArgumentException("course module services must not be null");
        }
        this.selectionService = selectionService;
        this.catalogService = catalogService;
        this.offeringService = offeringService;
        this.selectionRoundService = selectionRoundService;
        this.selectionRecordService = selectionRecordService;
        this.gradeSubmissionService = gradeSubmissionService;
    }

    public CourseSelectionService getSelectionService() {
        return selectionService;
    }

    public CourseCatalogService getCatalogService() {
        return catalogService;
    }

    public CourseOfferingService getOfferingService() {
        return offeringService;
    }

    /** 返回教务维护选课轮次的服务；旧模块组装方式未提供时返回 null。 */
    public SelectionRoundService getSelectionRoundService() {
        return selectionRoundService;
    }

    /** 返回教学班有效选课名单的查询来源；旧模块组装方式未提供时返回 null。 */
    public CourseSelectionRecordService getSelectionRecordService() {
        return selectionRecordService;
    }

    /** 返回教学班成绩草稿服务；旧模块组装方式未提供时返回 null。 */
    public GradeSubmissionService getGradeSubmissionService() {
        return gradeSubmissionService;
    }
}

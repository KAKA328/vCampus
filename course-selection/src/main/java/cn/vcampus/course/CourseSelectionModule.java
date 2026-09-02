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

    public CourseSelectionModule(CourseSelectionService selectionService,
            CourseCatalogService catalogService, CourseOfferingService offeringService) {
        if (selectionService == null || catalogService == null || offeringService == null) {
            throw new IllegalArgumentException("course module services must not be null");
        }
        this.selectionService = selectionService;
        this.catalogService = catalogService;
        this.offeringService = offeringService;
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
}

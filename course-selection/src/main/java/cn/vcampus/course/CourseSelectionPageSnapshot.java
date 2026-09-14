package cn.vcampus.course;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 学生进入某一选课轮次时一次性返回的可选教学班与已选课快照。 */
public final class CourseSelectionPageSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<SelectableCourseOffering> availableOfferings;
    private final List<SelectedCourseOffering> selectedOfferings;

    public CourseSelectionPageSnapshot(List<SelectableCourseOffering> availableOfferings,
            List<SelectedCourseOffering> selectedOfferings) {
        if (availableOfferings == null || selectedOfferings == null) {
            throw new IllegalArgumentException("course selection page data must not be null");
        }
        this.availableOfferings = Collections.unmodifiableList(
                new ArrayList<SelectableCourseOffering>(availableOfferings));
        this.selectedOfferings = Collections.unmodifiableList(
                new ArrayList<SelectedCourseOffering>(selectedOfferings));
    }

    public List<SelectableCourseOffering> getAvailableOfferings() { return availableOfferings; }
    public List<SelectedCourseOffering> getSelectedOfferings() { return selectedOfferings; }
}

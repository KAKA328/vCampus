package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import org.junit.jupiter.api.Test;

/** 验证培养方案 Socket 命令的字段约束，避免客户端发送不完整的管理请求。 */
class TrainingPlanManagementCommandTest {
    @Test
    void createAndChangeStatusKeepTheirBusinessData() {
        TrainingPlan plan = new TrainingPlan("PLAN-CS-2030", "计算机科学与技术", 2030,
                Collections.singletonList(new TrainingPlanCourse("CS201", 1,
                        SelectionType.REQUIRED, false)));

        TrainingPlanManagementCommand create =
                TrainingPlanManagementCommand.create("token", plan);
        TrainingPlanManagementCommand publish = TrainingPlanManagementCommand.changeStatus(
                "token", "PLAN-CS-2030", TrainingPlanStatus.PUBLISHED);

        assertEquals(TrainingPlanManagementCommand.Operation.CREATE, create.getOperation());
        assertEquals(plan, create.getPlan());
        assertEquals(TrainingPlanManagementCommand.Operation.CHANGE_STATUS,
                publish.getOperation());
        assertEquals(TrainingPlanStatus.PUBLISHED, publish.getStatus());
    }

    @Test
    void requiredFieldsCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> TrainingPlanManagementCommand.list("  "));
        assertThrows(IllegalArgumentException.class,
                () -> TrainingPlanManagementCommand.removeCourse("token", "PLAN-CS-2030", " "));
    }
}

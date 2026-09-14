package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.List;

public interface MajorDirectoryService {
    ServiceResult<List<MajorDirectoryEntry>> listActive();

    default ServiceResult<Void> requireActiveName(String name) {
        ServiceResult<List<MajorDirectoryEntry>> result = listActive();
        if (result.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(result.getStatus(), result.getMessage());
        }
        long matches = result.getData().stream()
                .filter(entry -> entry.isActive() && entry.getMajorName().equals(name)).count();
        return matches == 1 ? ServiceResult.ok(null)
                : ServiceResult.failure(StatusCode.BAD_REQUEST, "请选择专业目录中唯一且有效的专业");
    }
}

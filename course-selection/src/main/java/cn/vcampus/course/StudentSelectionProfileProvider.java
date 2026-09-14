package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;

/** 服务端按已认证账号查询选课资料的适配接口。 */
public interface StudentSelectionProfileProvider {
    ServiceResult<StudentSelectionProfile> findByUserId(String userId);

    /**
     * 仅读取选课轮次时不需要重修课程明细，实现可覆盖此方法减少无关数据读取。
     * 认证仍由调用方在每一次请求中完成，默认实现保持与完整资料查询一致。
     */
    default ServiceResult<StudentSelectionProfile> findForAvailableRounds(String userId) {
        return findByUserId(userId);
    }
}

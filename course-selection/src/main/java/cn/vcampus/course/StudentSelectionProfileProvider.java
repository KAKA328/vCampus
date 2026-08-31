package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;

/** 服务端按已认证账号查询选课资料的适配接口。 */
public interface StudentSelectionProfileProvider {
    ServiceResult<StudentSelectionProfile> findByUserId(String userId);
}

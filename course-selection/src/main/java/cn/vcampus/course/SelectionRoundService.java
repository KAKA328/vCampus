package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教务人员维护选课轮次的业务接口。
 *
 * <p>权限由后续服务器消息处理器验证；本接口只定义轮次的创建、查询和状态变更规则。</p>
 */
public interface SelectionRoundService {
    ServiceResult<SelectionRound> create(SelectionRound round);
    ServiceResult<SelectionRound> findById(String roundId);
    ServiceResult<List<SelectionRound>> listByTerm(String term);
    ServiceResult<List<SelectionRound>> listOpenRounds(String term, LocalDateTime time);
    /** 仅调整轮次的起止时间，不改变所属学期和轮次类型。 */
    ServiceResult<SelectionRound> updateTimeWindow(String roundId, LocalDateTime startsAt,
            LocalDateTime endsAt);
    ServiceResult<SelectionRound> changeStatus(String roundId, SelectionRoundStatus status);
}

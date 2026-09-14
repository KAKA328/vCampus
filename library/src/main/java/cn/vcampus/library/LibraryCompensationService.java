package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Server-side bridge to the shared campus wallet. Identity/roles are resolved by the handler. */
public interface LibraryCompensationService {
    /** 登记遗失并按原价生成唯一赔偿账单，不直接扣读者余额。 */
    ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId);
    /** 校验账单归属后结清赔偿；账单、借阅、扣款和流水保持一致。 */
    ServiceResult<LibraryCompensation> pay(String userId, String compensationId);
    /** null selects all users; only the management-authorized handler may request that. */
    ServiceResult<List<LibraryCompensation>> history(String userId);
    /** 读取同一校园钱包的余额，单位为分。 */
    ServiceResult<Long> balance(String userId);
}

package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Server-side bridge to the shared campus wallet. Identity/roles are resolved by the handler. */
public interface LibraryCompensationService {
    ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId);
    ServiceResult<LibraryCompensation> pay(String userId, String compensationId);
    /** null selects all users; only the management-authorized handler may request that. */
    ServiceResult<List<LibraryCompensation>> history(String userId);
    ServiceResult<Long> balance(String userId);
}

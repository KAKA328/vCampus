package cn.vcampus.user;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** User-management contract implemented by the server and called by the client adapter. */
public interface UserManagementService {
    ServiceResult<Void> register(UserCredentials credentials);
    ServiceResult<UserImportResult> importUsers(String token, List<UserImportRow> rows);
    ServiceResult<Void> unregister(String userId, String token);
    ServiceResult<Session> login(UserCredentials credentials);
    ServiceResult<Session> currentSession(String token);
    ServiceResult<Void> logout(String token);
    ServiceResult<Boolean> authorize(String token, String permission);
}

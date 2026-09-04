package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryBackedServiceTest {
    @Test
    void registeredAccountCanBeUsedByAnotherServiceInstance() {
        UserRepository users = new InMemoryUserRepository();
        AuditLogRepository auditLog = new InMemoryAuditLogRepository();
        UserManagementService first = new DefaultUserManagementService(users, new SessionManager(), auditLog);
        UserManagementService second = new DefaultUserManagementService(users, new SessionManager(), auditLog);
        UserCredentials credentials = new UserCredentials("repo001", "repo001", "Repo User", Role.STUDENT.name());

        assertEquals(StatusCode.OK, first.register(credentials).getStatus());

        assertEquals(StatusCode.OK, second.login(credentials).getStatus());
    }

    @Test
    void adminCanUnregisterAnotherUserAndAuditIsRecorded() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        UserCredentials student = new UserCredentials("stu001", "stu001", "Student", Role.STUDENT.name());
        service.register(student);
        Session adminSession = sessions.create(new User("admin001", "Admin", Role.ADMIN));

        assertEquals(StatusCode.OK, service.unregister(student.getUserId(), adminSession.getToken()).getStatus());

        assertEquals(StatusCode.UNAUTHORIZED, service.login(student).getStatus());
        assertEquals(2, auditLog.findAll().size());
        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "UNREGISTER_USER".equals(event.getAction())
                        && "admin001".equals(event.getActorUserId())
                        && student.getUserId().equals(event.getTargetId())));
    }

    @Test
    void nonAdminStillCannotUnregisterAnotherUser() {
        UserManagementService service = new InMemoryUserManagementService();
        UserCredentials first = new UserCredentials("stu002", "stu002", "Student A", Role.STUDENT.name());
        UserCredentials second = new UserCredentials("stu003", "stu003", "Student B", Role.STUDENT.name());
        service.register(first);
        service.register(second);
        Session firstSession = service.login(first).getData();

        assertEquals(StatusCode.UNAUTHORIZED, service.unregister(second.getUserId(), firstSession.getToken()).getStatus());
    }

    @Test
    void adminCanImportUsersAndImporterMetadataIsRecorded() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        Session adminSession = sessions.create(new User("admin002", "Admin", Role.ADMIN));

        ServiceResult<UserImportResult> result = service.importUsers(adminSession.getToken(), Arrays.asList(
                new UserImportRow("imp_stu001", "Demo123", "Imported Student", Role.STUDENT.name()),
                new UserImportRow("imp_tch001", "Demo123", "Imported Teacher", Role.TEACHER.name())));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(2, result.getData().getTotalCount());
        assertEquals(2, result.getData().getSuccessCount());
        assertEquals(0, result.getData().getFailureCount());
        assertFalse(result.getData().getImportBatchId().trim().isEmpty());

        UserAccount imported = users.findById("imp_stu001");
        assertNotNull(imported);
        assertEquals("admin002", imported.getCreatedBy());
        assertEquals(result.getData().getImportBatchId(), imported.getImportBatchId());
        assertNotNull(imported.getCreatedAt());
        assertEquals(StatusCode.OK, service.login(
                new UserCredentials("imp_stu001", "Demo123", "Imported Student", Role.STUDENT.name())).getStatus());

        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "IMPORT_USER".equals(event.getAction())
                        && "admin002".equals(event.getActorUserId())
                        && "imp_stu001".equals(event.getTargetId())));
        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "LOGIN".equals(event.getAction())
                        && "imp_stu001".equals(event.getActorUserId())
                        && "imp_stu001".equals(event.getTargetId())));
    }

    @Test
    void importKeepsValidRowsWhenAnotherRowFails() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        Session adminSession = sessions.create(new User("admin003", "Admin", Role.ADMIN));
        assertEquals(StatusCode.OK, service.register(
                new UserCredentials("dup001", "Demo123", "Duplicate", Role.STUDENT.name())).getStatus());

        ServiceResult<UserImportResult> result = service.importUsers(adminSession.getToken(), Arrays.asList(
                new UserImportRow("dup001", "Demo123", "Duplicate Again", Role.STUDENT.name()),
                new UserImportRow("imp_ok001", "Demo123", "Imported OK", Role.STUDENT.name())));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(2, result.getData().getTotalCount());
        assertEquals(1, result.getData().getSuccessCount());
        assertEquals(1, result.getData().getFailureCount());
        assertEquals(1, result.getData().getFailures().size());
        assertEquals(1, result.getData().getFailures().get(0).getRowNumber());
        assertEquals("dup001", result.getData().getFailures().get(0).getUserId());
        assertNotNull(users.findById("imp_ok001"));
        assertEquals(2, auditLog.findAll().size());
        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "IMPORT_USER".equals(event.getAction()) && "imp_ok001".equals(event.getTargetId())));
    }

    @Test
    void nonAdminCannotImportUsers() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        Session studentSession = sessions.create(new User("stu004", "Student", Role.STUDENT));

        ServiceResult<UserImportResult> result = service.importUsers(studentSession.getToken(), Arrays.asList(
                new UserImportRow("blocked001", "Demo123", "Blocked", Role.STUDENT.name())));

        assertEquals(StatusCode.FORBIDDEN, result.getStatus());
        assertEquals(null, users.findById("blocked001"));
        assertEquals(0, auditLog.findAll().size());
    }

    @Test
    void adminCanListAccountsAndAuditEvents() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        Session adminSession = sessions.create(new User("admin_list", "Admin", Role.ADMIN));
        service.register(new UserCredentials("list_stu001", "Demo123", "List Student", Role.STUDENT.name()));

        ServiceResult<List<UserAccountSummary>> accounts = service.listAccounts(adminSession.getToken());
        ServiceResult<List<AuditEvent>> auditEvents = service.listAuditEvents(adminSession.getToken());

        assertEquals(StatusCode.OK, accounts.getStatus());
        assertTrue(accounts.getData().stream().anyMatch(account ->
                "list_stu001".equals(account.getUserId()) && "正常".equals(account.getStatusText())));
        assertEquals(StatusCode.OK, auditEvents.getStatus());
        assertTrue(auditEvents.getData().stream().anyMatch(event ->
                "REGISTER_USER".equals(event.getAction()) && "list_stu001".equals(event.getTargetId())));
    }

    @Test
    void adminCanDisableEnableAndUnregisterAccount() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        UserCredentials student = new UserCredentials("state_stu001", "Demo123", "State Student", Role.STUDENT.name());
        service.register(student);
        Session adminSession = sessions.create(new User("admin_state", "Admin", Role.ADMIN));

        assertEquals(StatusCode.OK, service.setAccountActive(
                new UserStatusCommand(adminSession.getToken(), "state_stu001", false)).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, service.login(student).getStatus());

        assertEquals(StatusCode.OK, service.setAccountActive(
                new UserStatusCommand(adminSession.getToken(), "state_stu001", true)).getStatus());
        assertEquals(StatusCode.OK, service.login(student).getStatus());

        assertEquals(StatusCode.OK, service.unregister("state_stu001", adminSession.getToken()).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, service.login(student).getStatus());
        assertTrue(auditLog.findAll().stream().anyMatch(event -> "DISABLE_USER".equals(event.getAction())));
        assertTrue(auditLog.findAll().stream().anyMatch(event -> "ENABLE_USER".equals(event.getAction())));
        assertTrue(auditLog.findAll().stream().anyMatch(event -> "UNREGISTER_USER".equals(event.getAction())));
    }

    @Test
    void importAutomaticallyBindsStudentAndTeacherProfilesByProfileId() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        InMemoryProfileBindingRepository profiles = new InMemoryProfileBindingRepository();
        profiles.addStudentProfile("20240001");
        profiles.addTeacherProfile("T2024001");
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog,
                new InMemoryPasswordResetApplicationRepository(), profiles);
        Session adminSession = sessions.create(new User("admin_bind", "Admin", Role.ADMIN));

        ServiceResult<UserImportResult> result = service.importUsers(adminSession.getToken(), Arrays.asList(
                new UserImportRow("bind_stu001", "Demo123", "绑定学生", Role.STUDENT.name(), "20240001"),
                new UserImportRow("bind_tch001", "Demo123", "绑定教师", Role.TEACHER.name(), "T2024001")));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(2, result.getData().getSuccessCount());
        List<UserAccountSummary> accounts = service.listAccounts(adminSession.getToken()).getData();
        assertTrue(accounts.stream().anyMatch(account ->
                "bind_stu001".equals(account.getUserId()) && "20240001".equals(account.getProfileId())));
        assertTrue(accounts.stream().anyMatch(account ->
                "bind_tch001".equals(account.getUserId()) && "T2024001".equals(account.getProfileId())));
    }

    @Test
    void importRejectsMissingProfileIdWhenRoleNeedsProfileBinding() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        InMemoryProfileBindingRepository profiles = new InMemoryProfileBindingRepository();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog,
                new InMemoryPasswordResetApplicationRepository(), profiles);
        Session adminSession = sessions.create(new User("admin_bind_missing", "Admin", Role.ADMIN));

        ServiceResult<UserImportResult> result = service.importUsers(adminSession.getToken(), Arrays.asList(
                new UserImportRow("bind_missing001", "Demo123", "缺少档案", Role.STUDENT.name(), "20249999")));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(0, result.getData().getSuccessCount());
        assertEquals(1, result.getData().getFailureCount());
        assertTrue(result.getData().getFailures().get(0).getReason().contains("档案"));
        assertEquals(null, users.findById("bind_missing001"));
    }

    @Test
    void studentOrTeacherRegistrationRequiresExistingProfileWhenBindingIsEnabled() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        UserManagementService service = new DefaultUserManagementService(users, new SessionManager(),
                new InMemoryAuditLogRepository(), new InMemoryPasswordResetApplicationRepository(),
                new InMemoryProfileBindingRepository());

        assertEquals(StatusCode.BAD_REQUEST, service.register(
                new UserCredentials("bind_required001", "Demo123", "缺少档案", Role.STUDENT.name())).getStatus());
        assertEquals(null, users.findById("bind_required001"));
    }

    @Test
    void failedProfileBindingDoesNotLeaveAnOrphanAccount() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        ProfileBindingRepository failingBindings = new ProfileBindingRepository() {
            @Override public ProfileBindingResult validate(Role role, String profileId, String userId) {
                return ProfileBindingResult.OK;
            }

            @Override public ProfileBindingResult bind(Role role, String profileId, String userId) {
                return ProfileBindingResult.PROFILE_ALREADY_BOUND;
            }

            @Override public String findProfileId(Role role, String userId) {
                return "";
            }
        };
        UserManagementService service = new DefaultUserManagementService(users, new SessionManager(),
                new InMemoryAuditLogRepository(), new InMemoryPasswordResetApplicationRepository(),
                failingBindings);

        assertEquals(StatusCode.BAD_REQUEST, service.register(new UserCredentials(
                "bind_rollback001", "Demo123", "绑定失败", Role.STUDENT.name(), "S-ROLLBACK"))
                .getStatus());
        assertEquals(null, users.findById("bind_rollback001"));
    }

    @Test
    void boundProfileAccountCannotChangeToAnotherRole() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        InMemoryProfileBindingRepository profiles = new InMemoryProfileBindingRepository();
        profiles.addStudentProfile("S-ROLE-001");
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog,
                new InMemoryPasswordResetApplicationRepository(), profiles);
        assertEquals(StatusCode.OK, service.register(new UserCredentials(
                "bound_role001", "Demo123", "已绑定学生", Role.STUDENT.name(), "S-ROLE-001"))
                .getStatus());
        Session admin = sessions.create(new User("admin_bound_role", "Admin", Role.ADMIN));

        assertEquals(StatusCode.FORBIDDEN, service.changeUserRole(new UserRoleChangeCommand(
                admin.getToken(), "bound_role001", Role.ADMIN.name())).getStatus());
        assertEquals(Role.STUDENT, users.findById("bound_role001").getUser().getRole());
    }

    @Test
    void adminCanChangeAnotherUsersRoleAndTargetSessionIsInvalidated() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        UserCredentials target = new UserCredentials("role_target001", "Demo123", "Role Target", Role.STUDENT.name());
        service.register(target);
        Session targetSession = service.login(target).getData();
        Session adminSession = sessions.create(new User("admin_role", "Admin", Role.ADMIN));

        ServiceResult<Void> result = service.changeUserRole(
                new UserRoleChangeCommand(adminSession.getToken(), "role_target001", Role.STORE_MANAGER.name()));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, service.currentSession(targetSession.getToken()).getStatus());
        assertEquals(Role.STORE_MANAGER, users.findById("role_target001").getUser().getRole());
        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "CHANGE_USER_ROLE".equals(event.getAction()) && "role_target001".equals(event.getTargetId())));
    }

    @Test
    void adminCannotChangeOwnRole() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, new InMemoryAuditLogRepository());
        Session adminSession = sessions.create(new User("admin_self_role", "Admin", Role.ADMIN));

        ServiceResult<Void> result = service.changeUserRole(
                new UserRoleChangeCommand(adminSession.getToken(), "admin_self_role", Role.STUDENT.name()));

        assertEquals(StatusCode.FORBIDDEN, result.getStatus());
    }

    @Test
    void emptyImportReturnsBadRequest() {
        UserManagementService service = new InMemoryUserManagementService();

        ServiceResult<UserImportResult> result = service.importUsers("token", Arrays.<UserImportRow>asList());

        assertEquals(StatusCode.BAD_REQUEST, result.getStatus());
    }

    @Test
    void passwordResetApprovalUsesTemporaryPasswordAndForcesUserToChangeIt() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        InMemoryProfileBindingRepository profiles = new InMemoryProfileBindingRepository();
        profiles.addStudentProfile("20241234");
        PasswordResetApplicationRepository passwordResets = new InMemoryPasswordResetApplicationRepository();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog,
                passwordResets, profiles);
        UserCredentials student = new UserCredentials("reset001", "Old123", "Reset Student",
                Role.STUDENT.name(), "20241234");
        service.register(student);
        Session adminSession = sessions.create(new User("admin_reset", "Admin", Role.ADMIN));

        assertEquals(StatusCode.OK, service.requestPasswordReset(
                new PasswordResetRequestCommand("reset001", "忘记密码", "13800000000")).getStatus());
        assertEquals(StatusCode.OK, service.login(student).getStatus());

        List<PasswordResetApplicationSummary> pending = service
                .listPasswordResetApplications(adminSession.getToken()).getData();
        assertEquals(1, pending.size());
        assertEquals("reset001", pending.get(0).getUserId());
        assertEquals("Reset Student", pending.get(0).getDisplayName());
        assertEquals(Role.STUDENT.name(), pending.get(0).getRoleCode());
        assertEquals("20241234", pending.get(0).getProfileId());
        assertEquals("忘记密码", pending.get(0).getReason());

        ServiceResult<PasswordResetReviewResult> review = service.reviewPasswordReset(
                new PasswordResetReviewCommand(adminSession.getToken(), "reset001", true));

        assertEquals(StatusCode.OK, review.getStatus());
        assertNotNull(review.getData().getTemporaryPassword());
        assertEquals(StatusCode.UNAUTHORIZED, service.login(student).getStatus());
        ServiceResult<Session> temporaryLogin = service.login(new UserCredentials(
                "reset001", review.getData().getTemporaryPassword(), "Reset Student", Role.STUDENT.name()));
        assertEquals(StatusCode.OK, temporaryLogin.getStatus());
        assertTrue(temporaryLogin.getData().isForcePasswordChange());
        assertEquals(StatusCode.FORBIDDEN, service.authorize(
                temporaryLogin.getData().getToken(), Permission.COURSE_SELECT.getCode()).getStatus());

        assertEquals(StatusCode.OK, service.changeForcedPassword(
                new PasswordChangeCommand(temporaryLogin.getData().getToken(), "OwnNew123")).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, service.login(new UserCredentials(
                "reset001", review.getData().getTemporaryPassword(), "Reset Student", Role.STUDENT.name())).getStatus());
        ServiceResult<Session> finalLogin = service.login(new UserCredentials(
                "reset001", "OwnNew123", "Reset Student", Role.STUDENT.name()));
        assertEquals(StatusCode.OK, finalLogin.getStatus());
        assertFalse(finalLogin.getData().isForcePasswordChange());
        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "PASSWORD_RESET_REQUEST".equals(event.getAction()) && "reset001".equals(event.getTargetId())));
        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "PASSWORD_RESET_APPROVE".equals(event.getAction()) && "reset001".equals(event.getTargetId())));
        assertTrue(auditLog.findAll().stream().anyMatch(event ->
                "FORCED_PASSWORD_CHANGE".equals(event.getAction()) && "reset001".equals(event.getTargetId())));
    }

    @Test
    void studentCannotReviewPasswordResetRequest() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        PasswordResetApplicationRepository passwordResets = new InMemoryPasswordResetApplicationRepository();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog, passwordResets);
        UserCredentials student = new UserCredentials("reset002", "Old123", "Reset Student", Role.STUDENT.name());
        service.register(student);
        Session studentSession = service.login(student).getData();
        assertEquals(StatusCode.OK, service.requestPasswordReset(
                new PasswordResetRequestCommand("reset002", "New123")).getStatus());

        ServiceResult<PasswordResetReviewResult> result = service.reviewPasswordReset(
                new PasswordResetReviewCommand(studentSession.getToken(), "reset002", true));

        assertEquals(StatusCode.FORBIDDEN, result.getStatus());
        assertEquals(StatusCode.OK, service.logout(studentSession.getToken()).getStatus());
        assertEquals(StatusCode.OK, service.login(student).getStatus());
    }
}

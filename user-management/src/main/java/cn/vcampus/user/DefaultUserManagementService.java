package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Repository-backed user service shared by memory and Access deployments. */
public final class DefaultUserManagementService implements UserManagementService {
    private final UserRepository users;
    private final SessionManager sessions;
    private final AuditLogRepository auditLog;
    private final PasswordResetApplicationRepository passwordResets;
    private final ProfileBindingRepository profileBindings;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final RolePermissionPolicy permissionPolicy = new RolePermissionPolicy();
    private final SecureRandom random = new SecureRandom();

    public DefaultUserManagementService(UserRepository users, SessionManager sessions, AuditLogRepository auditLog) {
        this(users, sessions, auditLog, new InMemoryPasswordResetApplicationRepository());
    }

    public DefaultUserManagementService(UserRepository users, SessionManager sessions, AuditLogRepository auditLog,
                                        PasswordResetApplicationRepository passwordResets) {
        this(users, sessions, auditLog, passwordResets, new NoOpProfileBindingRepository());
    }

    public DefaultUserManagementService(UserRepository users, SessionManager sessions, AuditLogRepository auditLog,
                                        PasswordResetApplicationRepository passwordResets,
                                        ProfileBindingRepository profileBindings) {
        this.users = users;
        this.sessions = sessions;
        this.auditLog = auditLog;
        this.passwordResets = passwordResets;
        this.profileBindings = profileBindings == null ? new NoOpProfileBindingRepository() : profileBindings;
    }

    @Override public ServiceResult<Void> register(UserCredentials c) {
        Role role = role(c);
        if (role == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid registration data");
        ServiceResult<Void> result = createAccount(c, role);
        if (result.getStatus() == StatusCode.OK) {
            auditLog.record(new AuditEvent(c.getUserId(), "REGISTER_USER", "USER", c.getUserId(), Instant.now()));
        }
        return result;
    }

    /** Creates a trusted bootstrap/admin-provisioned account; never expose this method as a public Socket action. */
    public ServiceResult<Void> provisionAccount(UserCredentials c) {
        Role role = role(c);
        if (role == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid account data");
        return createAccount(c, role);
    }

    @Override public ServiceResult<List<UserAccountSummary>> listAccounts(String token) {
        ServiceResult<Session> current = requireUserManager(token);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        List<UserAccountSummary> summaries = new ArrayList<UserAccountSummary>();
        for (UserAccount account : users.findAll()) {
            summaries.add(new UserAccountSummary(account,
                    profileBindings.findProfileId(account.getUser().getRole(), account.getUser().getUserId())));
        }
        Collections.sort(summaries, new Comparator<UserAccountSummary>() {
            @Override public int compare(UserAccountSummary left, UserAccountSummary right) {
                String leftTime = left.getCreatedAt() == null ? "" : left.getCreatedAt().toString();
                String rightTime = right.getCreatedAt() == null ? "" : right.getCreatedAt().toString();
                int byTime = rightTime.compareTo(leftTime);
                return byTime != 0 ? byTime : left.getUserId().compareTo(right.getUserId());
            }
        });
        return ServiceResult.ok(summaries);
    }

    @Override public ServiceResult<UserImportResult> importUsers(String token, List<UserImportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "import rows are required");
        }
        ServiceResult<Session> current = requireUserManager(token);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        User actor = current.getData().getUser();

        String batchId = UUID.randomUUID().toString();
        Instant importedAt = Instant.now();
        List<UserImportFailure> failures = new ArrayList<UserImportFailure>();
        int successCount = 0;
        for (int index = 0; index < rows.size(); index++) {
            UserImportRow row = rows.get(index);
            ImportFailure failure = importOne(actor.getUserId(), row, batchId, importedAt);
            if (failure == null) {
                successCount++;
            } else {
                failures.add(new UserImportFailure(index + 1, failure.userId, failure.message));
            }
        }
        return ServiceResult.ok(new UserImportResult(batchId, rows.size(), successCount, failures));
    }

    @Override public ServiceResult<Void> setAccountActive(UserStatusCommand command) {
        if (command == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "account status command is required");
        }
        ServiceResult<Session> current = requireUserManager(command.getToken());
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        if (!users.setActive(command.getUserId(), command.isActive())) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
        }
        if (!command.isActive()) {
            sessions.invalidateUser(command.getUserId());
        }
        auditLog.record(new AuditEvent(current.getData().getUser().getUserId(),
                command.isActive() ? "ENABLE_USER" : "DISABLE_USER",
                "USER", command.getUserId(), Instant.now()));
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Void> changeUserRole(UserRoleChangeCommand command) {
        if (command == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "role change command is required");
        }
        ServiceResult<Session> current = requireUserManager(command.getToken());
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        User actor = current.getData().getUser();
        if (actor.getUserId().equals(command.getUserId())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "cannot change own role");
        }
        Role newRole;
        try {
            newRole = Role.valueOf(command.getRoleCode());
        } catch (IllegalArgumentException invalidRole) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid role");
        }
        UserAccount target = users.findById(command.getUserId());
        if (target == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
        }
        Role currentRole = target.getUser().getRole();
        if ((currentRole == Role.STUDENT || currentRole == Role.TEACHER)
                && currentRole != newRole
                && !profileBindings.findProfileId(currentRole, command.getUserId()).isEmpty()) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "已有学生或教师档案绑定，不能直接切换为其他角色");
        }
        if ((newRole == Role.STUDENT || newRole == Role.TEACHER)
                && profileBindings.findProfileId(newRole, command.getUserId()).isEmpty()) {
            return ServiceResult.failure(StatusCode.FORBIDDEN,
                    "学生或教师角色必须绑定对应档案");
        }
        if (!users.changeRole(command.getUserId(), newRole)) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
        }
        sessions.invalidateUser(command.getUserId());
        auditLog.record(new AuditEvent(actor.getUserId(), "CHANGE_USER_ROLE",
                "USER", command.getUserId(), Instant.now()));
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<List<AuditEvent>> listAuditEvents(String token) {
        ServiceResult<Session> current = requireUserManager(token);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        List<AuditEvent> events = auditLog.findAll();
        Collections.sort(events, new Comparator<AuditEvent>() {
            @Override public int compare(AuditEvent left, AuditEvent right) {
                return right.getCreatedAt().compareTo(left.getCreatedAt());
            }
        });
        return ServiceResult.ok(events);
    }

    @Override public ServiceResult<Void> requestPasswordReset(PasswordResetRequestCommand command) {
        if (command == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "password reset request is required");
        }
        UserAccount account = users.findById(command.getUserId());
        if (account == null || !account.isActive()) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
        }
        Instant submittedAt = Instant.now();
        PasswordResetApplication application = new PasswordResetApplication(
                command.getUserId(), command.getReason(), command.getContactInfo(), submittedAt,
                PasswordResetStatus.PENDING, null, null);
        passwordResets.save(application);
        auditLog.record(new AuditEvent(command.getUserId(), "PASSWORD_RESET_REQUEST",
                "USER", command.getUserId(), submittedAt));
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<List<PasswordResetApplicationSummary>> listPasswordResetApplications(String token) {
        ServiceResult<Session> current = requireUserManager(token);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        List<PasswordResetApplicationSummary> summaries =
                new ArrayList<PasswordResetApplicationSummary>();
        for (PasswordResetApplication application : passwordResets.findPending()) {
            UserAccount account = users.findById(application.getUserId());
            String displayName = account == null ? "" : account.getUser().getDisplayName();
            String roleCode = account == null ? "" : account.getUser().getRole().name();
            String profileId = account == null ? "" : profileBindings.findProfileId(
                    account.getUser().getRole(), account.getUser().getUserId());
            summaries.add(new PasswordResetApplicationSummary(application.getUserId(),
                    displayName, roleCode, profileId, application.getReason(), application.getContactInfo(),
                    application.getSubmittedAt(), application.getStatus()));
        }
        return ServiceResult.ok(summaries);
    }

    @Override public ServiceResult<PasswordResetReviewResult> reviewPasswordReset(PasswordResetReviewCommand command) {
        if (command == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "password reset review is required");
        }
        ServiceResult<Session> current = requireUserManager(command.getToken());
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        User actor = current.getData().getUser();

        PasswordResetApplication application = passwordResets.findPendingByUserId(command.getUserId());
        if (application == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "password reset request not found");
        }

        Instant reviewedAt = Instant.now();
        PasswordResetStatus status = command.isApproved()
                ? PasswordResetStatus.APPROVED : PasswordResetStatus.REJECTED;
        String temporaryPassword = "";
        if (command.isApproved()) {
            temporaryPassword = temporaryPassword();
            if (!users.updatePasswordHash(command.getUserId(),
                    passwordHasher.hash(temporaryPassword), true)) {
                return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
            }
            sessions.invalidateUser(command.getUserId());
        }
        if (!passwordResets.review(command.getUserId(), status, actor.getUserId(), reviewedAt)) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "password reset request not found");
        }
        auditLog.record(new AuditEvent(actor.getUserId(), command.isApproved()
                ? "PASSWORD_RESET_APPROVE" : "PASSWORD_RESET_REJECT", "USER", command.getUserId(), reviewedAt));
        return ServiceResult.ok(new PasswordResetReviewResult(command.getUserId(), temporaryPassword));
    }

    @Override public ServiceResult<Void> changeForcedPassword(PasswordChangeCommand command) {
        if (command == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "password change command is required");
        }
        ServiceResult<Session> current = currentSession(command.getToken());
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        Session session = current.getData();
        if (!session.isForcePasswordChange()) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "password change is not required");
        }
        if (!users.updatePasswordHash(session.getUser().getUserId(),
                passwordHasher.hash(command.getNewPassword()), false)) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
        }
        sessions.invalidateUser(session.getUser().getUserId());
        auditLog.record(new AuditEvent(session.getUser().getUserId(), "FORCED_PASSWORD_CHANGE",
                "USER", session.getUser().getUserId(), Instant.now()));
        return ServiceResult.ok(null);
    }

    private ServiceResult<Void> createAccount(UserCredentials c, Role role) {
        return createAccount(c, role, null, null, null);
    }

    private ServiceResult<Void> createAccount(UserCredentials c, Role role,
                                              String createdBy, Instant createdAt, String importBatchId) {
        final User user;
        try {
            user = new User(c.getUserId(), c.getDisplayName(), role);
        } catch (IllegalArgumentException invalidUser) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid registration data");
        }
        ProfileBindingResult validation = profileBindings.validate(role, c.getProfileId(), c.getUserId());
        if (validation != ProfileBindingResult.OK && validation != ProfileBindingResult.NOT_REQUIRED) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, bindingMessage(validation));
        }
        UserAccount account = new UserAccount(user, passwordHasher.hash(c.getPassword()),
                true, createdBy, createdAt, importBatchId);
        if (!users.create(account)) {
            return ServiceResult.failure(StatusCode.CONFLICT, "user already exists");
        }
        ProfileBindingResult binding;
        try {
            binding = profileBindings.bind(role, c.getProfileId(), c.getUserId());
        } catch (RuntimeException failure) {
            rollbackCreatedAccount(c.getUserId(), failure);
            throw failure;
        }
        if (binding != ProfileBindingResult.OK && binding != ProfileBindingResult.NOT_REQUIRED) {
            rollbackCreatedAccount(c.getUserId(), null);
            return ServiceResult.failure(StatusCode.BAD_REQUEST, bindingMessage(binding));
        }
        return ServiceResult.ok(null);
    }

    private ImportFailure importOne(String actorUserId, UserImportRow row, String batchId, Instant importedAt) {
        if (row == null) {
            return new ImportFailure("", "row is invalid");
        }
        try {
            UserCredentials credentials = new UserCredentials(
                    row.getUserId(), row.getPassword(), row.getDisplayName(), row.getRoleCode(), row.getProfileId());
            Role role = role(credentials);
            if (role == null) {
                return new ImportFailure(row.getUserId(), "invalid role");
            }
            ServiceResult<Void> created = createAccount(credentials, role, actorUserId, importedAt, batchId);
            if (created.getStatus() != StatusCode.OK) {
                return new ImportFailure(row.getUserId(), created.getMessage());
            }
            auditLog.record(new AuditEvent(actorUserId, "IMPORT_USER", "USER", credentials.getUserId(), importedAt));
            return null;
        } catch (IllegalArgumentException invalidRow) {
            return new ImportFailure(row.getUserId(), "invalid import row");
        }
    }

    private static Role role(UserCredentials credentials) {
        try {
            return Role.valueOf(credentials.getRoleCode());
        } catch (IllegalArgumentException invalidRole) {
            return null;
        }
    }

    @Override public ServiceResult<Void> unregister(String userId, String token) {
        Session session = sessions.find(token);
        if (session == null) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        boolean self = session.getUser().getUserId().equals(userId);
        boolean admin = permissionPolicy.isAllowed(session.getUser().getRole(), Permission.USER_MANAGE);
        if (!self && !admin) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        if (!users.deactivateById(userId)) return ServiceResult.failure(StatusCode.NOT_FOUND, "user not found");
        sessions.invalidateUser(userId);
        auditLog.record(new AuditEvent(session.getUser().getUserId(), "UNREGISTER_USER", "USER", userId, Instant.now()));
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Session> login(UserCredentials c) {
        UserAccount account = users.findById(c.getUserId());
        if (account == null || !account.isActive()
                || !passwordHasher.matches(c.getPassword(), account.getPasswordHash())) {
            return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid credentials");
        }
        Session session = sessions.create(account.getUser(), account.isForcePasswordChange());
        auditLog.record(new AuditEvent(account.getUser().getUserId(), "LOGIN", "USER",
                account.getUser().getUserId(), Instant.now()));
        return ServiceResult.ok(session);
    }

    @Override public ServiceResult<Session> currentSession(String token) {
        Session session = sessions.find(token);
        if (session == null) return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        return ServiceResult.ok(session);
    }

    @Override public ServiceResult<Void> logout(String token) {
        Session session = sessions.find(token);
        if (session == null || !sessions.invalidate(token)) {
            return ServiceResult.failure(StatusCode.UNAUTHORIZED, "invalid session");
        }
        auditLog.record(new AuditEvent(session.getUser().getUserId(), "LOGOUT", "USER",
                session.getUser().getUserId(), Instant.now()));
        return ServiceResult.ok(null);
    }

    @Override public ServiceResult<Boolean> authorize(String token, String permission) {
        ServiceResult<Session> current = currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        Permission requestedPermission = Permission.fromCode(permission);
        if (requestedPermission == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "invalid permission");
        if (current.getData().isForcePasswordChange()) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "password change required");
        }
        boolean allowed = permissionPolicy.isAllowed(current.getData().getUser().getRole(), requestedPermission);
        return allowed ? ServiceResult.ok(Boolean.TRUE) : ServiceResult.failure(StatusCode.FORBIDDEN, "permission denied");
    }

    private ServiceResult<Session> requireUserManager(String token) {
        ServiceResult<Session> current = currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return current;
        }
        if (!permissionPolicy.isAllowed(current.getData().getUser().getRole(), Permission.USER_MANAGE)) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "permission denied");
        }
        return current;
    }

    private static String bindingMessage(ProfileBindingResult result) {
        if (result == ProfileBindingResult.PROFILE_NOT_FOUND) {
            return "档案编号不存在或未预置";
        }
        if (result == ProfileBindingResult.PROFILE_ALREADY_BOUND) {
            return "档案已绑定其他账号";
        }
        if (result == ProfileBindingResult.USER_ALREADY_BOUND) {
            return "账号已绑定其他档案";
        }
        return "档案绑定失败";
    }

    private void rollbackCreatedAccount(String userId, RuntimeException cause) {
        if (!users.deleteById(userId)) {
            throw new IllegalStateException("failed to rollback account after profile binding failure", cause);
        }
    }

    private String temporaryPassword() {
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
        StringBuilder password = new StringBuilder("Tmp");
        for (int i = 0; i < 8; i++) {
            password.append(alphabet[random.nextInt(alphabet.length)]);
        }
        return password.toString();
    }

    private static final class ImportFailure {
        private final String userId;
        private final String message;

        private ImportFailure(String userId, String message) {
            this.userId = userId;
            this.message = message;
        }
    }
}

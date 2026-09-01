package cn.vcampus.user;

/** Result of binding an account to an existing student/teacher profile. */
public enum ProfileBindingResult {
    OK,
    NOT_REQUIRED,
    PROFILE_NOT_FOUND,
    PROFILE_ALREADY_BOUND,
    USER_ALREADY_BOUND
}

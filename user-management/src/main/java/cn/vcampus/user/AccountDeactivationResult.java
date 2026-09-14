package cn.vcampus.user;

/** Outcome of an atomic account deactivation attempt. */
public enum AccountDeactivationResult {
    SUCCESS,
    NOT_FOUND,
    LAST_ACTIVE_ADMINISTRATOR
}

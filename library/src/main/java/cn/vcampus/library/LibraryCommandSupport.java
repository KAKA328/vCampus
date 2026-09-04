package cn.vcampus.library;

final class LibraryCommandSupport {
    private LibraryCommandSupport() { }

    static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    static String optional(String value) { return value == null ? null : value.trim(); }
}

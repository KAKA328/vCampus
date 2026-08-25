package cn.vcampus.client.view;

/** Display metadata for one visible module card in the Swing dashboard. */
public final class ModuleDescriptor {
    private final String title;
    private final String summary;
    private final String status;

    public ModuleDescriptor(String title, String summary, String status) {
        this.title = requireText(title, "title");
        this.summary = requireText(summary, "summary");
        this.status = requireText(status, "status");
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getStatus() {
        return status;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}

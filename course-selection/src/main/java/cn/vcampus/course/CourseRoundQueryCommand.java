package cn.vcampus.course;

import java.io.Serializable;

/** V2 protocol request: query selection rounds for a term. */
public final class CourseRoundQueryCommand implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String token;
    private final String term;

    public CourseRoundQueryCommand(String token, String term) {
        this.token = CourseProtocolText.requireText(token, "token");
        this.term = CourseProtocolText.requireText(term, "term");
    }

    public String getToken() { return token; }
    public String getTerm() { return term; }
}

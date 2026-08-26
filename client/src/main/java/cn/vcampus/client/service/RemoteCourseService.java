package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端选课服务，负责把界面操作转换为 Socket 选课消息。
 */
public final class RemoteCourseService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteCourseService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    /** 查询全部课程。 */
    public Message listCourses() throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_QUERY, CourseQueryCommand.allCourses());
    }

    /** 查询指定学生已经选择的课程。 */
    public Message selectedCourses(String token, String studentId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_QUERY, CourseQueryCommand.selectedCourses(token, studentId));
    }

    /** 为指定学生选择一门课程。 */
    public Message select(String token, String studentId, String courseId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_SELECT,
                new CourseSelectionCommand(token, studentId, courseId));
    }

    /** 为指定学生退选一门课程。 */
    public Message drop(String token, String studentId, String courseId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_DROP,
                new CourseSelectionCommand(token, studentId, courseId));
    }

    private Message send(MessageType type, Object payload)
            throws IOException, ClassNotFoundException {
        return messages.send(Message.request("course-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}

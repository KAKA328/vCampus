package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.course.CourseOfferingQueryCommand;
import cn.vcampus.course.CourseOfferingSelectionCommand;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseRoundQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import cn.vcampus.course.CourseSelectionRecordDropCommand;
import cn.vcampus.course.CourseSelectionRecordQueryCommand;
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

    /** V2：查询某学期开放的选课轮次。 */
    public Message selectionRounds(String token, String term)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_ROUND_QUERY, new CourseRoundQueryCommand(token, term));
    }

    /** V2：查询某轮次下可选的具体教学班；courseId 为空时查询该轮次全部教学班。 */
    public Message offerings(String token, String roundId, String courseId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_OFFERING_QUERY,
                new CourseOfferingQueryCommand(token, roundId, courseId));
    }

    /** V2：查询某学生某学期的选课记录。 */
    public Message selectedRecords(String token, String studentId, String term)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_RECORD_QUERY,
                new CourseSelectionRecordQueryCommand(token, studentId, term));
    }

    /** 为指定学生选择一门课程。 */
    public Message select(String token, String studentId, String courseId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_SELECT,
                new CourseSelectionCommand(token, studentId, courseId));
    }

    /** V2：在某轮次中选择具体教学班。 */
    public Message selectOffering(String token, String studentId, String roundId, String offeringId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_OFFERING_SELECT,
                new CourseOfferingSelectionCommand(token, studentId, roundId, offeringId));
    }

    /** 为指定学生退选一门课程。 */
    public Message drop(String token, String studentId, String courseId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_DROP,
                new CourseSelectionCommand(token, studentId, courseId));
    }

    /** V2：按选课记录编号退选。 */
    public Message dropRecord(String token, String studentId, String recordId)
            throws IOException, ClassNotFoundException {
        return send(MessageType.COURSE_RECORD_DROP,
                new CourseSelectionRecordDropCommand(token, studentId, recordId));
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

package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.course.CourseDropRecordV2Command;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.CourseSelectOfferingV2Command;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** 客户端选课服务：仅发送 token、轮次、教学班或选课记录编号。 */
public final class RemoteCourseService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();
    public RemoteCourseService(String host, int port) throws IOException { messages = new SocketMessageClient(host, port); }
    public Message availableRounds(String token) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECTION_QUERY_V2, CourseSelectionQueryV2Command.availableRounds(token)); }
    public Message availableOfferings(String token, String roundId) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECTION_QUERY_V2, CourseSelectionQueryV2Command.availableOfferings(token, roundId)); }
    public Message selectedOfferings(String token) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECTION_QUERY_V2, CourseSelectionQueryV2Command.selectedOfferings(token)); }
    public Message select(String token, String roundId, String offeringId) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_SELECT_OFFERING_V2, new CourseSelectOfferingV2Command(token, roundId, offeringId)); }
    public Message drop(String token, String recordId) throws IOException, ClassNotFoundException { return send(MessageType.COURSE_DROP_RECORD_V2, new CourseDropRecordV2Command(token, recordId)); }
    /** 发送教务管理员维护课程目录或教学班的请求。 */
    public Message manage(CourseManagementCommand command) throws IOException, ClassNotFoundException {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        return send(MessageType.COURSE_MANAGE, command);
    }
    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException { return messages.send(Message.request("course-" + sequence.incrementAndGet(), type, payload)); }
    @Override public void close() throws IOException { messages.close(); }
}

package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentReviewCommand;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** Client adapter for student-management messages. */
public final class RemoteStudentService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteStudentService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    public Message findById(String token, String studentId) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.byId(token, studentId));
    }

    public Message review(String token, String studentId, int requiredCredits)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_REVIEW, new StudentReviewCommand(token, studentId, requiredCredits));
    }

    private Message send(MessageType type, Object payload)
            throws IOException, ClassNotFoundException {
        return messages.send(Message.request("student-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}

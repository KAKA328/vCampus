package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.StudentUpdateCommand;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** Client-side adapter for student record messages. */
public final class RemoteStudentService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteStudentService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    public Message self(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.self(token));
    }

    public Message findById(String token, String studentId) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.byId(token, studentId));
    }

    public Message findByClass(String token, String classId) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.byClass(token, classId));
    }

    public Message save(String token, StudentRecord record) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_UPDATE, new StudentUpdateCommand(token, record));
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("student-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}

package cn.vcampus.client.service;

import cn.vcampus.client.transport.SocketMessageClient;
import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.StudentUpdateCommand;
import cn.vcampus.student.StudentAcademicQueryV1Command;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** 客户端学生服务，把学籍页面操作转换为 Socket 消息。 */
public final class RemoteStudentService implements Closeable {
    private final SocketMessageClient messages;
    private final AtomicLong sequence = new AtomicLong();

    public RemoteStudentService(String host, int port) throws IOException {
        this.messages = new SocketMessageClient(host, port);
    }

    /** 查询当前登录账号绑定的学生档案。 */
    public Message currentStudent(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.self(token));
    }

    /** 按学号查询学生档案。 */
    public Message findById(String token, String studentId) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.byId(token, studentId));
    }

    /** 按班级查询学生档案。 */
    public Message findByClass(String token, String classId) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.byClass(token, classId));
    }

    /** 按专业查询学生档案。 */
    public Message findByMajor(String token, String majorName) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_QUERY, StudentQueryCommand.byMajor(token, majorName));
    }

    /** 保存学生档案；服务器根据会话角色执行字段级权限校验。 */
    public Message save(String token, StudentRecord record) throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_UPDATE, new StudentUpdateCommand(token, record));
    }

    public Message academicQuery(String token, StudentAcademicQueryV1Command.QueryType queryType)
            throws IOException, ClassNotFoundException {
        return send(MessageType.STUDENT_ACADEMIC_QUERY_V1,
                new StudentAcademicQueryV1Command(token, queryType));
    }

    public Message currentTeacher(String token) throws IOException, ClassNotFoundException {
        return send(MessageType.TEACHER_SELF_QUERY_V1,
                new cn.vcampus.student.TeacherSelfQueryV1Command(token));
    }

    public Message administer(cn.vcampus.student.AcademicAdminCommandV1 command)
            throws IOException, ClassNotFoundException {
        return send(MessageType.ACADEMIC_ADMIN_V1, command);
    }

    private Message send(MessageType type, Object payload) throws IOException, ClassNotFoundException {
        return messages.send(Message.request("student-" + sequence.incrementAndGet(), type, payload));
    }

    @Override
    public void close() throws IOException {
        messages.close();
    }
}

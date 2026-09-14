package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteLibraryService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import java.io.IOException;

/** 只读组合请求；保留局部成功结果，不重试写入。 */
final class LibraryReadRequests {
    /** 部分只读查询失败仍保留其他已得到的结果；绝不用于写操作或自动重试。 */
    private static Message readSafely(RemoteLibraryService service, LibraryRequestRunner.LibraryRequest read) {
        try {
            return read.execute(service);
        } catch (IOException | ClassNotFoundException unavailable) {
            return null;
        }
    }

    /** 查询赔偿和同账户钱包的只读组合请求。 */
    static final class CompensationRequest implements LibraryRequestRunner.LibraryRequest {
        /** 当前页面的视图状态。 */
        private final LibraryViewState v;
        /** 绑定当前页面，查询范围和钱包查询权限取自该页面会话。 */
        CompensationRequest(LibraryViewState v) { this.v = v; }

        /** 赔偿记录或赔偿服务。 */
        Message compensations;
        /** 与商店共用的钱包访问对象或查询结果。 */
        Message wallet;

        /** 先查赔偿再查读者本人钱包，任一读取失败不丢弃另一项结果。 */
        @Override public Message execute(RemoteLibraryService service) {
            compensations = readSafely(service, remote -> remote.compensations(v.session.getToken(), v.manager));
            if (!v.manager) wallet = readSafely(service, remote -> remote.walletBalance(v.session.getToken()));
            return compensations;
        }
    }

    /** 赔偿后依次同步所有关联视图，避免多个独立后台任务相互覆盖 busy 状态。 */
    static final class RefreshRequest implements LibraryRequestRunner.LibraryRequest {
        /** 当前页面的视图状态。 */
        private final LibraryViewState v;
        /** 是否查询全部读者。 */
        final boolean all;
        /** 馆藏检索关键词。 */
        final String keyword;
        /** 馆藏分类。 */
        final String category;
        /** 借阅历史查询结果。 */
        Message history;
        /** 用于显示书名的完整馆藏查询结果。 */
        Message completeCatalog;
        /** 当前筛选范围的馆藏结果。 */
        Message catalog;
        /** 赔偿记录或赔偿服务。 */
        Message compensations;
        /** 与商店共用的钱包访问对象或查询结果。 */
        Message wallet;

        /** 捕获本轮刷新所需的读者范围与馆藏筛选条件。 */
        RefreshRequest(LibraryViewState v, boolean all, String keyword, String category) {
            this.v = v;
            this.all = all;
            this.keyword = keyword;
            this.category = category;
        }

        /** 顺序读取历史、馆藏、赔偿及钱包；这些查询并不是跨服务事务快照。 */
        @Override public Message execute(RemoteLibraryService service) {
            history = readSafely(service, remote -> remote.snapshotHistory(v.session.getToken(), all));
            completeCatalog = readSafely(service, remote -> remote.search(v.session.getToken(), "", ""));
            catalog = keyword.isEmpty() && category.isEmpty() ? completeCatalog
                    : readSafely(service, remote -> remote.search(v.session.getToken(), keyword, category));
            compensations = readSafely(service, remote -> remote.compensations(v.session.getToken(), v.manager));
            if (!v.manager) wallet = readSafely(service, remote -> remote.walletBalance(v.session.getToken()));
            return history;
        }
    }

    /** V4 历史和辅助目录在同一后台任务内完成；目录异常不丢弃借阅名称快照。 */
    static final class HistoryRequest implements LibraryRequestRunner.LibraryRequest {
        /** 当前页面的视图状态。 */
        private final LibraryViewState v;
        /** 是否查询全部读者。 */
        final boolean all;
        /** 当前筛选范围的馆藏结果。 */
        Message catalog;

        /** 保存本次借阅历史查询的页面会话和全部读者标志。 */
        HistoryRequest(LibraryViewState v, boolean all) { this.v = v; this.all = all; }

        /** 查询独立名称快照后刷新辅助目录；目录失败不能覆盖快照内的书名。 */
        @Override public Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException {
            Message history = service.snapshotHistory(v.session.getToken(), all);
            if (history.getStatusCode() == StatusCode.OK) {
                try {
                    catalog = service.search(v.session.getToken(), "", "");
                } catch (IOException | ClassNotFoundException unavailable) {
                    catalog = null;
                }
            }
            return history;
        }
    }
}

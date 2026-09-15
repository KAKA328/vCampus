package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.user.Session;
import java.awt.Point;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 客户端真实Socket回归：失败方补读库存、后台轮询以及迟到回包隔离。 */
class LibraryCatalogRefreshTest {
    @Test void rejectedBorrowImmediatelyRefreshesStockWithoutRetryingWrite() throws Exception {
        failedBorrow(false);
    }

    @Test void uncertainBorrowOnlyRequeriesAndDoesNotRetryTheWrite() throws Exception {
        failedBorrow(true);
    }

    private void failedBorrow(boolean disconnect) throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        LibraryPanel[] panel=new LibraryPanel[1];
        try (ServerSocket listener=new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server=executor.submit(() -> {
                try {
                    respond(listener, request -> {
                        assertEquals(MessageType.LIBRARY_BORROW_V2, request.getType());
                        return disconnect ? null : Message.response(request, StatusCode.CONFLICT, "库存不足，最后一册已被借走");
                    });
                    respond(listener, request -> {
                        assertEquals(MessageType.LIBRARY_QUERY_V2, request.getType());
                        return Message.response(request, StatusCode.OK, Collections.singletonList(book("LAST",0)));
                    });
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            edt(() -> {
                panel[0]=visiblePanel(listener.getLocalPort());
                panel[0].showBooks(books(book("LAST",1)));
                panel[0].state.bookTable.setRowSelectionInterval(0,0);
                panel[0].state.borrowButton.doClick();
            });
            await(() -> panel[0].state.bookModel.getValueAt(0,8).equals(0));
            edt(() -> {
                assertTrue(panel[0].state.borrowButton.isEnabled());
                assertTrue(panel[0].state.status.getText().contains(disconnect ? "无法连接" : "库存不足"));
                assertEquals("0 册",panel[0].state.availableCountValue.getText());
                assertEquals("LAST",LibraryCatalogActions.selectedBookId(panel[0].state));
            });
            server.get(5,TimeUnit.SECONDS);
        } finally { dispose(panel[0]); executor.shutdownNow(); }
    }

    @Test void periodicRefreshUsesDisplayedQueryAndDoesNotDisableControls() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        LibraryPanel[] panel=new LibraryPanel[1];
        CountDownLatch arrived=new CountDownLatch(1),release=new CountDownLatch(1);
        try (ServerSocket listener=new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server=executor.submit(() -> {
                try { respond(listener,request -> {
                    LibraryQueryV2Command query=(LibraryQueryV2Command)request.getPayload();
                    assertEquals("已提交",query.getKeyword()); assertEquals("科幻",query.getCategory());
                    arrived.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));
                    return Message.response(request,StatusCode.OK,Collections.singletonList(book("LAST",0)));
                }); } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            edt(() -> {
                panel[0]=panel(listener.getLocalPort());
                LibraryViewState v=panel[0].state;
                v.keywordField.setText("已提交"); v.categoryField.setSelectedItem("科幻");
                panel[0].showBooks(books(book("LAST",1)));
                v.keywordField.setText("尚未提交的输入"); v.categoryField.setSelectedItem("文学");
                v.initialLoadStarted=true; panel[0].addNotify();
                assertEquals(5000,v.catalogRefresh.timer.getDelay());
                v.catalogRefresh.timer.stop();
            });
            assertTrue(arrived.await(5,TimeUnit.SECONDS));
            edt(() -> {
                LibraryViewState v=panel[0].state;
                assertFalse(v.requestInProgress); assertTrue(v.borrowButton.isEnabled());
                assertTrue(v.searchButton.isEnabled()); assertTrue(v.keywordField.isEnabled());
            });
            release.countDown();
            await(() -> panel[0].state.bookModel.getValueAt(0,8).equals(0));
            edt(() -> {
                assertEquals("尚未提交的输入",panel[0].state.keywordField.getText());
                assertEquals("文学",panel[0].state.categoryField.getSelectedItem());
            });
            server.get(5,TimeUnit.SECONDS);
        } finally { release.countDown(); dispose(panel[0]); executor.shutdownNow(); }
    }

    @Test void foregroundResultCannotBeOverwrittenByEarlierPoll() throws Exception { stalePoll(false); }
    @Test void hiddenPageRejectsInflightPollAndStopsTimer() throws Exception { stalePoll(true); }

    private void stalePoll(boolean hide) throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        LibraryPanel[] panel=new LibraryPanel[1];
        CountDownLatch arrived=new CountDownLatch(1),release=new CountDownLatch(1);
        try (ServerSocket listener=new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server=executor.submit(() -> {
                try { respond(listener,request -> {
                    arrived.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));
                    return Message.response(request,StatusCode.OK,Collections.singletonList(book("LAST",1)));
                }); } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            edt(() -> {
                panel[0]=visiblePanel(listener.getLocalPort());
                panel[0].showBooks(books(book("LAST",1)));
                panel[0].state.catalogRefresh.requestRefresh();
            });
            assertTrue(arrived.await(5,TimeUnit.SECONDS));
            edt(() -> {
                LibraryViewState v=panel[0].state;
                if (hide) { panel[0].setVisible(false); assertFalse(v.catalogRefresh.timer.isRunning()); }
                else v.catalogRefresh.foregroundStarted();
                panel[0].showBooks(books(book("LAST",0)));
                v.catalogRefresh.timer.stop();
                v.status.setText("新操作结果");
            });
            release.countDown(); server.get(5,TimeUnit.SECONDS);
            // SwingWorker done is posted asynchronously; wait for completion without relying on a sleep.
            await(() -> !isPollRunning(panel[0].state));
            edt(() -> {
                assertEquals(0,panel[0].state.bookModel.getValueAt(0,8));
                assertEquals("<html>新操作结果</html>",panel[0].state.status.getText());
            });
        } finally { release.countDown(); dispose(panel[0]); executor.shutdownNow(); }
    }

    @Test void quietUpdatePreservesSelectionSortScrollAndFailureNotice() throws Exception {
        edt(() -> {
            LibraryPanel p=panel(19996); LibraryViewState v=p.state;
            Book[] many=new Book[35]; for(int i=0;i<many.length;i++) many[i]=book("BOOK"+i,1);
            p.showBooks(books(many));
            v.bookTable.getRowSorter().toggleSortOrder(8);
            v.bookTable.addRowSelectionInterval(2,2); v.bookTable.addRowSelectionInterval(8,8);
            java.util.List<String> selected=LibraryCatalogActions.selectedBookIds(v);
            JViewport viewport=(JViewport)v.bookTable.getParent();
            viewport.setExtentSize(new java.awt.Dimension(600,150)); viewport.setViewPosition(new Point(0,180));
            v.status.setText("借阅失败，请查看最新库存");
            many[2]=book("BOOK2",0);
            assertTrue(LibraryViewData.showBooksQuietly(v,books(many)));
            assertEquals(new java.util.HashSet<String>(selected),new java.util.HashSet<String>(LibraryCatalogActions.selectedBookIds(v)));
            assertEquals(1,v.bookTable.getRowSorter().getSortKeys().size());
            assertEquals(180,viewport.getViewPosition().y);
            assertEquals("<html>借阅失败，请查看最新库存</html>",v.status.getText());
            AtomicInteger changes=new AtomicInteger(); v.bookModel.addTableModelListener(e -> changes.incrementAndGet());
            assertTrue(LibraryViewData.showBooksQuietly(v,books(many)));
            assertEquals(0,changes.get());
        });
    }

    @Test void invalidQuietResponseRetainsCatalogAndMainMessage() throws Exception {
        edt(() -> {
            LibraryPanel p=panel(19996); p.showBooks(books(book("LAST",1))); p.state.status.setText("失败说明");
            for(Message response:Arrays.asList(null,Message.response(Message.request("bad",MessageType.LIBRARY_QUERY_V2,null),
                    StatusCode.OK,Arrays.asList(book("LAST",0),"invalid")),Message.response(Message.request("bad",MessageType.LIBRARY_QUERY_V2,null),StatusCode.SERVER_ERROR,"failure"))) {
                assertFalse(LibraryViewData.showBooksQuietly(p.state,response));
                assertEquals(1,p.state.bookModel.getValueAt(0,8)); assertEquals("<html>失败说明</html>",p.state.status.getText());
            }
        });
    }

    @Test void hiddenOrBusyPageDoesNotStartBackgroundConnections() throws Exception {
        try(ServerSocket listener=new ServerSocket(0)) {
            listener.setSoTimeout(250);
            LibraryPanel[] p=new LibraryPanel[1];
            try {
                edt(() -> { p[0]=panel(listener.getLocalPort()); p[0].showBooks(books(book("LAST",1)));
                    p[0].state.catalogRefresh.requestRefresh(); });
                assertThrows(java.net.SocketTimeoutException.class,listener::accept);
                edt(() -> { p[0].state.initialLoadStarted=true; p[0].state.requestInProgress=true; p[0].addNotify();
                    p[0].state.catalogRefresh.requestRefresh(); });
                assertThrows(java.net.SocketTimeoutException.class,listener::accept);
                edt(() -> { p[0].state.workspaceTabs.setSelectedIndex(1); assertFalse(p[0].state.catalogRefresh.timer.isRunning()); });
            } finally { dispose(p[0]); }
        }
    }

    @Test void expiredSessionPausesPollingWithoutErasingDataOrOperationMessage() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor(); LibraryPanel[] p=new LibraryPanel[1];
        try(ServerSocket listener=new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server=executor.submit(() -> {
                try { respond(listener,request -> Message.response(request,StatusCode.UNAUTHORIZED,"invalid session")); }
                catch(Exception failure) { throw new RuntimeException(failure); }
            });
            edt(() -> { p[0]=visiblePanel(listener.getLocalPort()); p[0].showBooks(books(book("LAST",1)));
                p[0].state.status.setText("原有操作提示"); p[0].state.catalogRefresh.requestRefresh(); });
            await(() -> p[0].state.catalogSyncStatus.getText().contains("已暂停")); server.get(5,TimeUnit.SECONDS);
            edt(() -> { assertFalse(p[0].state.catalogRefresh.timer.isRunning());
                assertEquals(1,p[0].state.bookModel.getValueAt(0,8));
                assertEquals("<html>原有操作提示</html>",p[0].state.status.getText());
                p[0].state.catalogRefresh.requestRefresh(); });
            listener.setSoTimeout(250); assertThrows(java.net.SocketTimeoutException.class,listener::accept);
        } finally { dispose(p[0]); executor.shutdownNow(); }
    }

    @Test void repeatedTicksDuringSlowQueryAreCoalescedInsteadOfOverlapping() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor(); LibraryPanel[] p=new LibraryPanel[1];
        CountDownLatch arrived=new CountDownLatch(1),release=new CountDownLatch(1);
        try(ServerSocket listener=new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server=executor.submit(() -> {
                try {
                    respond(listener,request -> { arrived.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));
                        return Message.response(request,StatusCode.OK,Collections.singletonList(book("LAST",1))); });
                    respond(listener,request -> Message.response(request,StatusCode.OK,Collections.singletonList(book("LAST",0))));
                } catch(Exception failure) { throw new RuntimeException(failure); }
            });
            edt(() -> { p[0]=visiblePanel(listener.getLocalPort()); p[0].showBooks(books(book("LAST",1)));
                p[0].state.catalogRefresh.timer.stop(); p[0].state.catalogRefresh.requestRefresh(); });
            assertTrue(arrived.await(5,TimeUnit.SECONDS));
            edt(() -> { for(int i=0;i<5;i++)p[0].state.catalogRefresh.requestRefresh(); });
            listener.setSoTimeout(250); assertThrows(java.net.SocketTimeoutException.class,listener::accept);
            listener.setSoTimeout(5000); release.countDown();
            await(() -> p[0].state.bookModel.getValueAt(0,8).equals(0)); server.get(5,TimeUnit.SECONDS);
            listener.setSoTimeout(250); assertThrows(java.net.SocketTimeoutException.class,listener::accept);
        } finally { release.countDown(); dispose(p[0]); executor.shutdownNow(); }
    }

    private static boolean isPollRunning(LibraryViewState v) {
        try { java.lang.reflect.Field f=LibraryCatalogRefresh.class.getDeclaredField("worker"); f.setAccessible(true); return f.get(v.catalogRefresh)!=null; }
        catch(Exception failure) { throw new AssertionError(failure); }
    }
    private static void respond(ServerSocket listener,Reply reply) throws Exception {
        try(Socket socket=listener.accept()) {
            socket.setSoTimeout(5000);
            try(ObjectInputStream input=new ObjectInputStream(socket.getInputStream()); ObjectOutputStream output=new ObjectOutputStream(socket.getOutputStream())) {
                Message response=reply.handle((Message)input.readObject());
                if(response!=null) { output.writeObject(response); output.flush(); }
            }
        }
    }
    private interface Reply { Message handle(Message request) throws Exception; }
    private static Book book(String id,int available) { return new Book(id,"测试书"+id,"作者","ISBN"+id,"科幻","出版社",10,1,available,"A1"); }
    private static Message books(Book...items) { return Message.response(Message.request("fixture",MessageType.LIBRARY_QUERY_V2,null),StatusCode.OK,Arrays.asList(items)); }
    private static LibraryPanel panel(int port) { return new LibraryPanel("127.0.0.1",port,new Session("refresh-test",new User("reader","读者",Role.STUDENT)),LibraryReminderState.memoryOnly()); }
    private static LibraryPanel visiblePanel(int port) {
        LibraryPanel p=panel(port); p.state.initialLoadStarted=true; p.addNotify();
        assertTrue(p.isShowing()); p.state.catalogRefresh.timer.stop(); return p;
    }
    private static void dispose(LibraryPanel p) throws Exception { if(p!=null) edt(() -> { p.state.catalogRefresh.stop(); if(p.isDisplayable())p.removeNotify(); }); }
    private static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            AtomicBoolean value=new AtomicBoolean(); edt(() -> value.set(condition.getAsBoolean()));
            if(value.get())return; Thread.sleep(10);
        }
        fail("asynchronous update did not complete");
    }
}

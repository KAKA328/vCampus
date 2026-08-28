package cn.vcampus.client.view;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.event.TableModelEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchTableModelTest {
    @Test
    void replacesAllRowsWithOneTableChange() {
        BatchTableModel model = new BatchTableModel(new Object[] {"编号", "名称"});
        AtomicInteger changes = new AtomicInteger();
        model.addTableModelListener(event -> {
            if (event.getType() == TableModelEvent.UPDATE) {
                changes.incrementAndGet();
            }
        });

        model.replaceRows(Arrays.asList(
                new Object[] {"A", "第一项"},
                new Object[] {"B", "第二项"}));

        assertEquals(1, changes.get());
        assertEquals(2, model.getRowCount());
        assertEquals("第二项", model.getValueAt(1, 1));
    }

    @Test
    void replacingRowsDoesNotResetTableStructure() {
        BatchTableModel model = new BatchTableModel(new Object[] {"编号", "名称"});
        AtomicReference<TableModelEvent> event = new AtomicReference<TableModelEvent>();
        model.addTableModelListener(event::set);

        model.replaceRows(Arrays.<Object[]>asList(new Object[] {"A", "第一项"}));

        assertEquals(TableModelEvent.UPDATE, event.get().getType());
        assertEquals(0, event.get().getFirstRow());
    }
}

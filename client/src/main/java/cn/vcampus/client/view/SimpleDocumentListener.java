package cn.vcampus.client.view;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** 将文档三个变更回调收敛为同一个轻量动作。 */
final class SimpleDocumentListener implements DocumentListener {
    private final Runnable action;

    SimpleDocumentListener(Runnable action) {
        if (action == null) throw new IllegalArgumentException("action must not be null");
        this.action = action;
    }

    @Override public void insertUpdate(DocumentEvent event) { action.run(); }
    @Override public void removeUpdate(DocumentEvent event) { action.run(); }
    @Override public void changedUpdate(DocumentEvent event) { action.run(); }
}

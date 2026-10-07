package ir.IrAutoX.JEFB;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EditorPane extends JPanel {

    public final Path file;
    public final RSyntaxTextArea text;
    public final RTextScrollPane scroll;
    public boolean dirty = false;
    public final UndoManager undoManager = new UndoManager();

    public EditorPane(Path file) throws IOException {
        super(new BorderLayout());
        this.file = file;
        text = new RSyntaxTextArea();
        text.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        text.setCodeFoldingEnabled(true);
        text.setAntiAliasingEnabled(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        text.setText(IO.read(file));
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream(
                    IDE.darkMode ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                                 : "/org/fife/ui/rsyntaxtextarea/themes/default.xml"));
            theme.apply(text);
        } catch (Exception ignored) {}
        text.getDocument().addUndoableEditListener(undoManager);
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                dirty = true;
                fireChange();
            }
            @Override public void removeUpdate(DocumentEvent e) {
                dirty = true;
                fireChange();
            }
            @Override public void changedUpdate(DocumentEvent e) {
                dirty = true;
                fireChange();
            }
        });
        scroll = new RTextScrollPane(text);
        scroll.setLineNumbersEnabled(true);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
    }

    void fireChange() {
        try {
            PluginRuntime.fireEditorChange(file, text.getText());
        } catch (Throwable ignored) {}
    }

    public static EditorPane fromString(String title, String content) {
        try {
            Path tmp = Files.createTempFile("jefb-", ".java");
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
            EditorPane ep = new EditorPane(tmp);
            ep.dirty = false;
            return ep;
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private static final long serialVersionUID = 1L;
}
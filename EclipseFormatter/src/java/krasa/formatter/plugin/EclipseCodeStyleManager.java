package krasa.formatter.plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import krasa.formatter.eclipse.EclipseCodeFormatterFacade;
import krasa.formatter.settings.DisabledFileTypeSettings;
import krasa.formatter.settings.JoinedGroup;
import krasa.formatter.settings.Settings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Scanner;

/**
 * Supported operations are handled by Eclipse formatter, other by IntelliJ
 * formatter.
 * <p/>
 * TODO proper write action thread handling
 *
 * @author Vojtech Krasa
 * @since 30.10.20011
 */
public class EclipseCodeStyleManager extends DelegatingCodeStyleManager {

    private static final Logger LOG = Logger.getInstance(EclipseCodeStyleManager.class.getName());
    public static final String LINE_SEPARATOR = "\n";

    @NotNull
    private final Project project;
    @NotNull
    protected final EclipseCodeFormatterFacade codeFormatterFacade;
    @NotNull
    private Settings settings;
    private Notifier notifier;

    public EclipseCodeStyleManager(@NotNull CodeStyleManager original,
                                   @NotNull Settings settings,
                                   @NotNull Project project) {
        super(original);
        this.project = project;
        this.settings = settings;
        codeFormatterFacade = new EclipseCodeFormatterFacade(settings.getEclipsePrefs());
        notifier = new Notifier(project);
    }

    public void reformatText(@NotNull PsiFile psiFile, final int startOffset, final int endOffset)
            throws IncorrectOperationException {
        boolean formattedByIntelliJ = false;
        try {
            ApplicationManager.getApplication().assertWriteAccessAllowed();
            PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
            CheckUtil.checkWritable(psiFile);

            final VirtualFile virtualFile = psiFile.getVirtualFile();
            Project project = psiFile.getProject();

            if (virtualFile == null) {
                Notification notification = new Notification(ProjectSettingsComponent.GROUP_DISPLAY_ID, "", "No file to format",
                        NotificationType.ERROR);
                notifier.showNotification(notification);
            } else if (!canReformatWithEclipse(virtualFile, project)) {
                if (skipFormatting(virtualFile, psiFile, startOffset, endOffset)) {
                    notifier.notifyFormattingWasDisabled(psiFile);
                } else {
                    formatWithIntelliJ(psiFile, startOffset, endOffset);
                }
            } else {
                optimizeImports(psiFile, project);
                formatWithEclipse(psiFile, startOffset, endOffset, virtualFile);
            }
        } catch (final Exception e) {
            e.printStackTrace();
            notifier.notifyFailedFormatting(psiFile, formattedByIntelliJ, e);
        }
    }

    private boolean skipFormatting(VirtualFile virtualFile,
                                   PsiFile psiFile,
                                   int startOffset,
                                   int endOffset) {
        if (settings.isFormatSeletedTextInAllFileTypes()) {
            final Editor editor = PsiUtilBase.findEditor(psiFile);
            if (editor != null) {
                Document document = editor.getDocument();
                String text = document.getText();
                if (!isWholeFile(startOffset, endOffset, text)) {
                    return false;
                }
            }
        }
        if (settings.isFormatOtherFileTypesWithIntelliJ()) {
            return isDisabledFileType(virtualFile);
        }
        return true;
    }

    private boolean isWholeFile(int startOffset, int endOffset, String text) {
        return startOffset == 0 && endOffset == text.length();
    }

    private boolean isDisabledFileType(VirtualFile virtualFile) {
        String path = ioFile(virtualFile).getPath();
        DisabledFileTypeSettings disabledFileTypeSettings = settings.geDisabledFileTypeSettings();
        return disabledFileTypeSettings.isDisabled(path);
    }


    private void formatWithEclipse(PsiFile psiFile, int startOffset, int endOffset, VirtualFile virtualFile) {
        final Editor editor = PsiUtilBase.findEditor(psiFile);
        boolean skipSuccessFormattingNotification = false;
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        if (editor != null) {
            int visualColumnToRestore = getVisualColumnToRestore(editor);

            // ApplicationManager.getApplication().runWriteAction(new
            // Runnable() {
            // @Override
            // public void run() {
            Document document = editor.getDocument();
            // fileDocumentManager.saveDocument(document); DO NOT SAVE
            // IT, IT BREAKS IT
            String text = document.getText();
            int fixedStartOffset = startOffset;
            // if there is selected text
            if (startOffset != 0) {
                // start offset must be on the start of line
                fixedStartOffset = text.substring(0, startOffset).lastIndexOf(LINE_SEPARATOR) + 1;
            }
            skipSuccessFormattingNotification = isSkipNotification(startOffset,
                    endOffset); // little fix for etc. ctrl+shift+enter
            document.setText(codeFormatterFacade.format(text, fixedStartOffset, endOffset, LINE_SEPARATOR));
            postOptimizeImports(document);

            restoreVisualColumnToRestore(editor, visualColumnToRestore);

            // }
            // });
        } else { // editor is closed
            Document writeTo = fileDocumentManager.getDocument(virtualFile);
            fileDocumentManager.saveDocument(writeTo);
            writeTo.setText(codeFormatterFacade.format(ioFile(virtualFile), LINE_SEPARATOR));
            postOptimizeImports(writeTo);
            fileDocumentManager.saveDocument(writeTo);
        }
        if (!skipSuccessFormattingNotification) {
            notifier.notifySuccessFormatting(psiFile, false);
        }
    }

    private boolean isSkipNotification(int startOffset, int endOffset) {
        return endOffset - startOffset < settings.getNotifyFromTextLenght();
    }

    private void formatWithIntelliJ(PsiFile psiFile, int startOffset, int endOffset) {
        super.reformatText(psiFile, startOffset, endOffset);
        if (!isSkipNotification(startOffset, endOffset)) {
            notifier.notifySuccessFormatting(psiFile, true);
        }
    }

    private void optimizeImports(PsiFile psiFile, Project project) {
        if (!settings.isOptimizeImports()) {
            return;
        }
        final PsiImportList newImportList = JavaCodeStyleManager.getInstance(project)
                .prepareOptimizeImportsResult((PsiJavaFile) psiFile);

        try {
            final PsiDocumentManager manager = PsiDocumentManager.getInstance(psiFile.getProject());
            final Document document = manager.getDocument(psiFile);
            if (document != null) {
                manager.commitDocument(document);
            }
            final PsiImportList oldImportList = ((PsiJavaFile) psiFile).getImportList();
            assert oldImportList != null;
            if (newImportList != null) {
                oldImportList.replace(newImportList);
            }
            manager.doPostponedOperationsAndUnblockDocument(document);
        } catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    /**
     * appends blank lines between import groups
     */
    private void postOptimizeImports(Document document) {
        if (!settings.isOptimizeImports()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        Scanner scanner = new Scanner(document.getText());
        String lastImportGroup = null;
        while (scanner.hasNext()) {
            String next = scanner.nextLine();
            if (next == null) {
                break;
            }
            if (next.startsWith("import ")) {
                int i = next.indexOf(".");
                if (i < 7) {
                    continue;
                }
                String currentImportGroup = next.substring(7, i);
                if (shouldAppendBlankLine(lastImportGroup, currentImportGroup)) {
                    sb.append(LINE_SEPARATOR);
                }
                lastImportGroup = currentImportGroup;
            } else if (next.isEmpty()) {
                lastImportGroup = null;
            }
            append(sb, next);
        }
        document.setText(sb.toString());
    }

    private boolean shouldAppendBlankLine(String lastImportGroup, String currentImportGroup) {
        if (lastImportGroup == null) return false;

        // TODO find out what is the eclipse's algorithm
        return !lastImportGroup.equals(currentImportGroup) && !settings.getImportGroupSettings()
                .contains(new JoinedGroup(lastImportGroup, currentImportGroup));
    }

    private void append(StringBuilder sb, String next) {
        sb.append(next);
        sb.append(LINE_SEPARATOR);
    }

    private void restoreVisualColumnToRestore(Editor editor, int visualColumnToRestore) {
        if (visualColumnToRestore < 0) {
        } else {
            CaretModel caretModel = editor.getCaretModel();
            VisualPosition position = caretModel.getVisualPosition();
            if (visualColumnToRestore != position.column) {
                caretModel.moveToVisualPosition(new VisualPosition(position.line, visualColumnToRestore));
            }
        }
    }

    private int getVisualColumnToRestore(Editor editor) {
        int visualColumnToRestore = -1;

        if (editor != null) {
            Document document1 = editor.getDocument();
            int caretOffset = editor.getCaretModel().getOffset();
            caretOffset = Math.max(Math.min(caretOffset, document1.getTextLength() - 1), 0);
            CharSequence text1 = document1.getCharsSequence();
            int caretLine = document1.getLineNumber(caretOffset);
            int lineStartOffset = document1.getLineStartOffset(caretLine);
            int lineEndOffset = document1.getLineEndOffset(caretLine);
            boolean fixCaretPosition = true;
            for (int i = lineStartOffset; i < lineEndOffset; i++) {
                char c = text1.charAt(i);
                if (c != ' ' && c != '\t' && c != '\n') {
                    fixCaretPosition = false;
                    break;
                }
            }
            if (fixCaretPosition) {
                visualColumnToRestore = editor.getCaretModel().getVisualPosition().column;
            }
        }
        return visualColumnToRestore;
    }


    private boolean canReformatWithEclipse(@NotNull VirtualFile file, @NotNull Project project) {
        return file.isInLocalFileSystem() && isWritable(file, project) && fileTypeIsSupported(file);
    }

    private static boolean isWritable(@NotNull VirtualFile file, @NotNull Project project) {
        return !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(file).hasReadonlyFiles();
    }

    private boolean fileTypeIsSupported(@NotNull VirtualFile file) {
        return ioFile(file).getPath().endsWith(".java");
    }

    @NotNull
    private static File ioFile(@NotNull VirtualFile file) {
        return new File(file.getPath());
    }
}

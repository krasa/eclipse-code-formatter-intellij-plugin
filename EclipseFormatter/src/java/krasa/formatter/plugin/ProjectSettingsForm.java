/*
 * External Code Formatter Copyright (c) 2007-2009 Esko Luontola, www.orfjackal.net Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */

package krasa.formatter.plugin;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import krasa.formatter.Messages;
import krasa.formatter.settings.Settings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Configuration dialog for changing the {@link krasa.formatter.settings.Settings} of the plugin.
 *
 * @author Esko Luontola
 * @author Vojtech Krasa
 * @since 4.12.2007
 */
public class ProjectSettingsForm {

    private static final Color NORMAL = new JTextField().getBackground();
    private static final Color WARNING = new Color(255, 255, 204);
    private static final Color ERROR = new Color(255, 204, 204);

    private JPanel rootComponent;

    private JRadioButton useDefaultFormatter;
    private JRadioButton useEclipseFormatter;

    private JFormattedTextField optimizeImportGroups;

    private JLabel eclipseSupportedFileTypesLabel;
    private JCheckBox optimizeImportsCheckBox;
    private JLabel optimizeImportGroupsLabel;
    private JLabel optimizeImportGroupsHelpLabel;
    private JTextArea help;

    private JTextField disabledFileTypes;
    private JLabel disabledFileTypesHelpLabel;
    private JRadioButton doNotFormatOtherFilesRadioButton;
    private JRadioButton formatOtherFilesWithExceptionsRadioButton;
    private JCheckBox formatSelectedTextInAllFileTypes;

    private JLabel eclipsePreferenceFileJavaLabel;
    private JLabel eclipsePreferenceFileJSLabel;

    private JTextField pathToEclipsePreferenceFileJava;
    private JTextField pathToEclipsePreferenceFileJS;

    private JLabel eclipsePrefsExample;
    private JLabel eclipsePrefsExampleJS;

    private JCheckBox enableJavaFormatting;
    private JCheckBox enableJSFormatting;

    private JButton eclipsePreferenceFilePathJavaBrowse;
    private JButton eclipsePreferenceFilePathJSBrowse;

    private final List<Popup> visiblePopups = new ArrayList<Popup>();
    @NotNull
    private Project project;

    public ProjectSettingsForm(Project project) {
        this.project = project;
        JToggleButton[] modifiableButtons = new JToggleButton[]{useDefaultFormatter, useEclipseFormatter,
                optimizeImportsCheckBox, enableJavaFormatting, doNotFormatOtherFilesRadioButton,
                formatOtherFilesWithExceptionsRadioButton, formatSelectedTextInAllFileTypes, enableJSFormatting};
        for (JToggleButton button : modifiableButtons) {
            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    updateComponents();
                }
            });
        }

        JTextField[] modifiableFields = new JTextField[]{pathToEclipsePreferenceFileJava, pathToEclipsePreferenceFileJS,
                optimizeImportGroups, disabledFileTypes};
        for (JTextField field : modifiableFields) {
            field.getDocument().addDocumentListener(new DocumentAdapter() {
                protected void textChanged(DocumentEvent e) {
                    updateComponents();
                }
            });
        }

        eclipsePreferenceFilePathJavaBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                browseForFile(pathToEclipsePreferenceFileJava);
            }
        });
        eclipsePreferenceFilePathJSBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                browseForFile(pathToEclipsePreferenceFileJS);
            }
        });

        rootComponent.addAncestorListener(new AncestorListener() {
            public void ancestorAdded(AncestorEvent event) {
                // Called when component becomes visible, to ensure that the
                // popups
                // are visible when the form is shown for the first time.
                updateComponents();
            }

            public void ancestorRemoved(AncestorEvent event) {
            }

            public void ancestorMoved(AncestorEvent event) {
            }
        });
    }

    private void browseForFile(@NotNull final JTextField target) {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        descriptor.setHideIgnored(false);

        descriptor.setTitle("Select config file");
        String text = target.getText();
        final VirtualFile toSelect = text == null || text.isEmpty() ? project.getBaseDir()
                : LocalFileSystem.getInstance().findFileByPath(text);

        // 10.5 does not have #chooseFile
        VirtualFile[] virtualFile = FileChooser.chooseFiles(project, descriptor, toSelect);
        if (virtualFile != null && virtualFile.length > 0) {
            target.setText(virtualFile[0].getPath());
        }

    }

    private void updateComponents() {
        hidePopups();

        enabledBy(new JComponent[]{eclipseSupportedFileTypesLabel, enableJavaFormatting, enableJSFormatting,
                doNotFormatOtherFilesRadioButton, formatOtherFilesWithExceptionsRadioButton,
                formatSelectedTextInAllFileTypes,}, useEclipseFormatter);

        enabledBy(new JComponent[]{pathToEclipsePreferenceFileJava, eclipsePrefsExample,
                eclipsePreferenceFileJavaLabel, optimizeImportsCheckBox, eclipsePreferenceFilePathJavaBrowse},
                useEclipseFormatter, enableJavaFormatting);

        enabledBy(new JComponent[]{optimizeImportGroups, optimizeImportGroupsLabel, optimizeImportGroupsHelpLabel,},
                optimizeImportsCheckBox, useEclipseFormatter, enableJavaFormatting);

        enabledBy(new JComponent[]{pathToEclipsePreferenceFileJS, eclipsePrefsExampleJS, eclipsePreferenceFileJSLabel,
                eclipsePreferenceFilePathJSBrowse}, useEclipseFormatter, enableJSFormatting);

        enabledBy(new JComponent[]{disabledFileTypes, disabledFileTypesHelpLabel,},
                formatOtherFilesWithExceptionsRadioButton, useEclipseFormatter);

    }

    private void enabledBy(@NotNull JComponent[] targets, @NotNull JToggleButton... control) {
        boolean b = true;
        for (JToggleButton jToggleButton : control) {
            b = b && (jToggleButton.isEnabled() && jToggleButton.isSelected());
        }
        for (JComponent target : targets) {
            target.setEnabled(b);
        }
    }

    private boolean notEmpty(@NotNull JTextField field) {
        if (field.getText().trim().length() == 0) {
            field.setBackground(WARNING);
            showPopup(field, Messages.message("warning.requiredField"));
            return false;
        }
        return true;
    }

    private boolean containsText(@NotNull String needle, @NotNull JTextField field) {
        if (!field.getText().contains(needle)) {
            field.setBackground(ERROR);
            showPopup(field, Messages.message("warning.mustContain", needle));
            return false;
        }
        return true;
    }

    private boolean fileExists(@NotNull JTextField field) {
        if (!new File(field.getText()).isFile()) {
            field.setBackground(ERROR);
            showPopup(field, Messages.message("warning.noSuchFile"));
            return false;
        }
        return true;
    }

    private void atLeastOneSelected(JToggleButton... buttons) {
        for (JToggleButton button : buttons) {
            if (button.isSelected()) {
                return;
            }
        }
        showPopup(buttons[0], Messages.message("warning.selectAtLeastOne"));
    }

    private void ok(@NotNull JTextField field) {
        field.setBackground(NORMAL);
    }

    private void showPopup(@NotNull JComponent parent, @NotNull String message) {
        if (!parent.isShowing() || !parent.isEnabled()) {
            return; // if getLocationOnScreen is called when the component is
            // not showing, an exception is thrown
        }
        JToolTip tip = new JToolTip();
        tip.setTipText(message);
        Dimension tipSize = tip.getPreferredSize();

        Point location = parent.getLocationOnScreen();
        int x = (int) location.getX();
        int y = (int) (location.getY() - tipSize.getHeight());

        Popup popup = PopupFactory.getSharedInstance().getPopup(parent, tip, x, y);
        popup.show();
        visiblePopups.add(popup);
    }

    private void hidePopups() {
        for (Iterator<Popup> it = visiblePopups.iterator(); it.hasNext(); ) {
            Popup popup = it.next();
            popup.hide();
            it.remove();
        }
    }

    @NotNull
    public JPanel getRootComponent() {
        return rootComponent;
    }

    public void importFrom(@NotNull Settings in) {
        formatOtherFilesWithExceptionsRadioButton.setSelected(in.isFormatOtherFileTypesWithIntelliJ());
        doNotFormatOtherFilesRadioButton.setSelected(!in.isFormatOtherFileTypesWithIntelliJ());
        useDefaultFormatter.setSelected(in.getFormatter().equals(Settings.Formatter.DEFAULT));
        useEclipseFormatter.setSelected(in.getFormatter().equals(Settings.Formatter.ECLIPSE));
        setData(in);
        updateComponents();
    }

    public void exportTo(@NotNull Settings out) {
        if (useEclipseFormatter.isSelected()) {
            out.setFormatter(Settings.Formatter.ECLIPSE);
        } else {
            out.setFormatter(Settings.Formatter.DEFAULT);
        }
        if (formatOtherFilesWithExceptionsRadioButton.isSelected()) {
            out.setFormatOtherFileTypesWithIntelliJ(true);
        } else {
            out.setFormatOtherFileTypesWithIntelliJ(false);
        }
        getData(out);
        out.setPathToConfigFileJava(pathToEclipsePreferenceFileJava.getText());
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    public void setData(Settings data) {
        pathToEclipsePreferenceFileJava.setText(data.getPathToConfigFileJava());
        optimizeImportsCheckBox.setSelected(data.isOptimizeImports());
        optimizeImportGroups.setText(data.getJoinedGroup());
        disabledFileTypes.setText(data.getDisabledFileTypes());
        formatSelectedTextInAllFileTypes.setSelected(data.isFormatSeletedTextInAllFileTypes());
        pathToEclipsePreferenceFileJS.setText(data.getPathToConfigFileJS());
        enableJavaFormatting.setSelected(data.isEnableJavaFormatting());
        enableJSFormatting.setSelected(data.isEnableJSFormatting());
    }

    public void getData(Settings data) {
        data.setPathToConfigFileJava(pathToEclipsePreferenceFileJava.getText());
        data.setOptimizeImports(optimizeImportsCheckBox.isSelected());
        data.setJoinedGroup(optimizeImportGroups.getText());
        data.setDisabledFileTypes(disabledFileTypes.getText());
        data.setFormatSeletedTextInAllFileTypes(formatSelectedTextInAllFileTypes.isSelected());
        data.setPathToConfigFileJS(pathToEclipsePreferenceFileJS.getText());
        data.setEnableJavaFormatting(enableJavaFormatting.isSelected());
        data.setEnableJSFormatting(enableJSFormatting.isSelected());
    }

    public boolean isModified(Settings data) {
        if (customIsModified(data)) return true;


        if (pathToEclipsePreferenceFileJava.getText() != null ? !pathToEclipsePreferenceFileJava.getText().equals(
                data.getPathToConfigFileJava()) : data.getPathToConfigFileJava() != null)
            return true;
        if (optimizeImportsCheckBox.isSelected() != data.isOptimizeImports())
            return true;
        if (optimizeImportGroups.getText() != null ? !optimizeImportGroups.getText().equals(data.getJoinedGroup())
                : data.getJoinedGroup() != null)
            return true;
        if (disabledFileTypes.getText() != null ? !disabledFileTypes.getText().equals(data.getDisabledFileTypes())
                : data.getDisabledFileTypes() != null)
            return true;
        if (formatSelectedTextInAllFileTypes.isSelected() != data.isFormatSeletedTextInAllFileTypes())
            return true;
        if (pathToEclipsePreferenceFileJS.getText() != null ? !pathToEclipsePreferenceFileJS.getText().equals(
                data.getPathToConfigFileJS()) : data.getPathToConfigFileJS() != null)
            return true;
        if (enableJavaFormatting.isSelected() != data.isEnableJavaFormatting())
            return true;
        if (enableJSFormatting.isSelected() != data.isEnableJSFormatting())
            return true;
        return false;
    }

    private boolean customIsModified(Settings data) {
        if (useDefaultFormatter.isSelected() != data.getFormatter().equals(Settings.Formatter.DEFAULT)) {
            return true;
        }
        if (useEclipseFormatter.isSelected() != data.getFormatter().equals(Settings.Formatter.ECLIPSE)) {
            return true;
        }
        if (formatOtherFilesWithExceptionsRadioButton.isSelected() != data.isFormatOtherFileTypesWithIntelliJ()) {
            return true;
        }
        if (doNotFormatOtherFilesRadioButton.isSelected() != !data.isFormatOtherFileTypesWithIntelliJ()) {
            return true;
        }
        return false;
    }
}

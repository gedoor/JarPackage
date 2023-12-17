package com.github.gedoor.jarpackage.ui;

import com.github.gedoor.jarpackage.pack.Packager;
import com.github.gedoor.jarpackage.pack.impl.AllPacker;
import com.github.gedoor.jarpackage.pack.impl.EachPacker;
import com.github.gedoor.jarpackage.util.Messages;
import com.github.gedoor.jarpackage.util.Util;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.util.Consumer;


import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.intellij.openapi.ui.Messages.showErrorDialog;

@SuppressWarnings("DialogTitleCapitalization")
public class Settings extends JDialog {
    private static File tempFile = null;
    private Properties properties = null;
    private final DataContext dataContext;
    private final Project project;
    private final Module module;
    private final VirtualFile[] virtualFiles;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField exportDirectoryField;
    private JButton selectPathButton;
    private JTextField exportJarNameField;
    private JCheckBox exportEachChildrenCheckBox;
    private JCheckBox fastModeCheckBox;


    public Settings(DataContext dataContext) {
        this.dataContext = dataContext;
        project = CommonDataKeys.PROJECT.getData(dataContext);
        module = LangDataKeys.MODULE.getData(dataContext);
        virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        this.buttonOK.addActionListener(e -> onOK());
        this.buttonCancel.addActionListener(e -> onCancel());

        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        this.contentPane.registerKeyboardAction(e ->
                onCancel(), KeyStroke.getKeyStroke(27, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        this.exportEachChildrenCheckBox.addActionListener(e -> onExportEachChildrenCheckBoxChange());
        this.selectPathButton.addActionListener(e -> onSelectPathButtonAction());

        List<String> names = new ArrayList<>();

        assert virtualFiles != null;
        assert project != null;
        assert module != null;
        for (VirtualFile file : virtualFiles) {
            PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(file);
            if (psiDirectory != null) {
                PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
                assert psiPackage != null;
                names.add(psiPackage.getQualifiedName());
            }
        }

        try {

            tempFile = new File(project.getBasePath() + File.separator + "package-path.properties");

            if (!tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.createNewFile();
            }

            properties = new Properties();
            InputStream in = new FileInputStream(tempFile);
            properties.load(in);

            Object exportJarName = properties.get("JAR_" + getPropertyKey());

            String jarName = Util.getTheSameStart(names);
            if (jarName.isEmpty()) {
                jarName = module.getName();
            }

            if (jarName.endsWith(".")) {
                jarName = jarName.substring(0, jarName.lastIndexOf("."));
            }

            if (exportJarName == null) {
                exportJarName = jarName;
            }
            this.exportJarNameField.setText(exportJarName.toString());

            Object exportPath = properties.get(getPropertyKey());
            if (exportPath == null) {
                exportPath = CompilerPaths.getModuleOutputPath(module, false);
            }
            assert exportPath != null;
            this.exportDirectoryField.setText(exportPath.toString());
        } catch (IOException e) {
            Messages.error(project, e.toString());
        }

    }


    private void onExportEachChildrenCheckBoxChange() {
        this.exportJarNameField.setEnabled(!this.exportEachChildrenCheckBox.isSelected());

    }

    private void onSelectPathButtonAction() {
        Project project = CommonDataKeys.PROJECT.getData(this.dataContext);
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        FileChooserConsumerImpl chooserConsumer = new FileChooserConsumerImpl(this.exportDirectoryField);
        FileChooser.chooseFile(descriptor, project, null, chooserConsumer);
    }

    private void onCancel() {
        this.dispose();
    }


    private void onOK() {
        String exportJarName = this.exportJarNameField.getText();
        exportJarName = exportJarName.trim() + ".jar";
        Messages.clear(project);
        if (Util.matchFileNamingConventions(exportJarName)) {
            String exportJarPath = this.exportDirectoryField.getText().trim();
            File _temp0 = new File(exportJarPath);
            if (!_temp0.exists()) {
                showErrorDialog(project, "the selected output path is not exists", "");
            } else {
                Packager packager = exportEachChildrenCheckBox.isSelected()
                        ? new EachPacker(dataContext, exportJarPath)
                        : new AllPacker(dataContext, exportJarPath, exportJarName);

                if (this.fastModeCheckBox.isSelected()) {
                    CompilerManager.getInstance(project).make(module, packager);
                } else {
                    CompilerManager.getInstance(project).compile(module, packager);
                }

                saveOutPutDir(this.exportDirectoryField.getText());
                saveOutPutJarName(this.exportJarNameField.getText());
                this.dispose();
            }
        } else {
            showErrorDialog(project, "please set a name of the output jar", "");
        }
    }

    private String getPropertyKey() {
        StringBuilder pKey = new StringBuilder("MDL_" + module.getName());

        for (VirtualFile file : virtualFiles) {
            PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(file);
            if (psiDirectory != null) {
                PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
                assert psiPackage != null;
                pKey.append("_PKG_").append(psiPackage.getQualifiedName());
            }
        }
        return pKey.toString().replace('.', '_');
    }

    private void saveOutPutJarName(String name) {

        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        assert project != null;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tempFile);

            properties.setProperty("JAR_" + getPropertyKey(), name);
            properties.store(out, "The New properties file");
        } catch (IOException e) {
            Messages.info(project, e.toString());
        } finally {
            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    Messages.info(project, e.toString());
                }
            }
        }
    }

    private void saveOutPutDir(String path) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        assert project != null;
        FileOutputStream out = null;
        try {

            out = new FileOutputStream(tempFile);

            properties.setProperty(getPropertyKey(), path);
            properties.store(out, "The New properties file");

        } catch (IOException e) {
            Messages.info(project, e.toString());
        } finally {

            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    Messages.info(project, e.toString());
                }
            }
        }
    }

    private static class FileChooserConsumerImpl implements Consumer<VirtualFile> {
        private final JTextField ouPutDirectoryField;

        public FileChooserConsumerImpl(JTextField jTextField) {
            this.ouPutDirectoryField = jTextField;
        }

        @Override
        public void consume(VirtualFile virtualFile) {
            this.ouPutDirectoryField.setText(virtualFile.getPath());
        }
    }
}

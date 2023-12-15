package com.github.gedoor.jarpackage.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.apache.http.util.TextUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;

public class Action extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return super.getActionUpdateThread();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        super.update(event);
        Presentation presentation = event.getPresentation();
        if (presentation.isEnabled()) {
            DataContext dataContext = event.getDataContext();
            Project project = CommonDataKeys.PROJECT.getData(dataContext);
            if (project == null) {
                presentation.setEnabled(false);
                presentation.setVisible(false);
                return;
            }

            VirtualFile[] virtualFiles = checkVirtualFiles(project, CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext));
            if (virtualFiles.length == 0) {
                presentation.setEnabled(false);
                presentation.setVisible(false);
                return;
            }

            PsiPackage psiPackage = null;
            if (virtualFiles.length == 1) {
                PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFiles[0]);
                if (psiDirectory != null) {
                    psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
                }
            } else {
                PsiElement var12 = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
                if (var12 instanceof PsiPackage) {
                    psiPackage = (PsiPackage) var12;
                }
            }

            String text;
            if (psiPackage != null) {
                text = psiPackage.getQualifiedName();
            } else if (virtualFiles.length == 1) {
                VirtualFile virtualFile = virtualFiles[0];
                text = virtualFile.getName();
            } else {
                text = JavaCompilerBundle.message("action.compile.description.selected.files");
            }

            if (TextUtils.isEmpty(text)) {
                presentation.setEnabled(false);
            } else {
                presentation.setText(this.getButtonName(text), true);
                presentation.setEnabled(true);
            }
        }

    }

    @Override
    public void actionPerformed(AnActionEvent event) {
//        Settings setting = new Settings(event.getDataContext());
//        setting.setResizable(false);
//        setting.setSize(500, 200);
//        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
//        Dimension frameSize = setting.getSize();
//        if (frameSize.height > screenSize.height) {
//            frameSize.height = screenSize.height;
//        }
//
//        if (frameSize.width > screenSize.width) {
//            frameSize.width = screenSize.width;
//        }
//
//        setting.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
//        setting.setTitle("Package Jars");
//        setting.setVisible(true);
    }

    public static void main(String[] args) {
    }

    private String getButtonName(String test) {
        StringBuilder sb = new StringBuilder(40);
        sb.append("Package '");
        int length = test.length();
        if (length > 23) {
            if (StringUtil.startsWithChar(test, '\'')) {
                sb.append("'");
            }

            sb.append("...");
            sb.append(test, length - 20, length);
        } else {
            sb.append(test);
        }
        sb.append("'");
        return sb.toString();
    }

    private VirtualFile[] checkVirtualFiles(Project project, VirtualFile[] virtualFiles) {
        if (virtualFiles != null && virtualFiles.length != 0) {
            PsiManager psiManager = PsiManager.getInstance(project);
            ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            ArrayList<VirtualFile> arrayList = new ArrayList<>();

            for (VirtualFile virtualFile : virtualFiles) {
                if (projectFileIndex.isInSourceContent(virtualFile)
                        && virtualFile.isInLocalFileSystem()
                        && virtualFile.isDirectory()) {
                    PsiDirectory var11 = psiManager.findDirectory(virtualFile);
                    if (var11 != null && JavaDirectoryService.getInstance().getPackage(var11) != null) {
                        arrayList.add(virtualFile);
                    }
                }
            }

            return VfsUtilCore.toVirtualFileArray(arrayList);
        } else {
            return VirtualFile.EMPTY_ARRAY;
        }
    }
}

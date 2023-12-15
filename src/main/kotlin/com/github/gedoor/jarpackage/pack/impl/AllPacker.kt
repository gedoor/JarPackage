//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
package com.github.gedoor.jarpackage.pack.impl

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiManager
import com.github.gedoor.jarpackage.pack.Packager
import com.github.gedoor.jarpackage.util.CommonUtils
import com.github.gedoor.jarpackage.util.Messages
import java.io.IOException
import java.nio.file.Path

class AllPacker(
    private val dataContext: DataContext,
    private val exportPath: String,
    private val exportJarName: String
) : Packager() {

    private val project: Project =
        CommonDataKeys.PROJECT.getData(dataContext)!!
    private val virtualFiles: Array<VirtualFile> =
        CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)!!
    private val outPutDir: VirtualFile =
        CompilerPaths.getModuleOutputDirectory(LangDataKeys.MODULE.getData(dataContext)!!, false)!!

    @Throws(Exception::class)
    override fun pack() {
        val allVfs: MutableSet<VirtualFile> = HashSet()
        for (virtualFile in virtualFiles) {
            val psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile)
            if (psiDirectory != null) {
                val psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory)!!
                var pvf: VirtualFile = outPutDir
                val packageNames = psiPackage.qualifiedName
                    .split("\\.".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                for (n in packageNames) {
                    pvf = pvf.findChild(n) ?: throw IOException("$n 文件夹不存在")
                }
                CommonUtils.collectExportFilesNest(project, allVfs, pvf)
            }
        }
        val filePaths: MutableList<Path> = ArrayList()
        val jarEntryNames: MutableList<String> = ArrayList()
        val outIndex = outPutDir.path.length + 1
        for (vf in allVfs) {
            filePaths.add(vf.toNioPath())
            jarEntryNames.add(vf.path.substring(outIndex))
        }
        CommonUtils.createNewJar(project, Path.of(exportPath, exportJarName), filePaths, jarEntryNames)
    }

    override fun finished(b: Boolean, error: Int, i1: Int, compileContext: CompileContext) {
        if (error == 0) {
            try {
                pack()
            } catch (e: Exception) {
                Messages.error(project, e.localizedMessage)
                e.printStackTrace()
            }
        } else {
            Messages.error(project, "compile error")
        }
    }
}
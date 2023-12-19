//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
package com.github.gedoor.jarpackage.pack.impl

import com.github.gedoor.jarpackage.pack.Packager
import com.github.gedoor.jarpackage.util.CommonUtils
import com.github.gedoor.jarpackage.util.Messages
import com.github.gedoor.jarpackage.util.Util
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import java.io.IOException
import java.nio.file.Path

class EachPacker(dataContext: DataContext, private val exportPath: String) : Packager() {

    private val project: Project = dataContext.getData(CommonDataKeys.PROJECT)!!
    private val virtualFiles: Array<VirtualFile> = dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)!!
    private val outPutDir: VirtualFile =
        CompilerPaths.getModuleOutputDirectory(dataContext.getData(LangDataKeys.MODULE)!!, false)!!

    @Throws(Exception::class)
    override fun pack() {
        val directories = HashSet<VirtualFile>()
        for (virtualFile in virtualFiles) {
            Util.iterateDirectory(project, directories, virtualFile)
        }
        val iterator: Iterator<VirtualFile> = directories.iterator()
        while (true) {
            var psiDirectory: PsiDirectory?
            do {
                if (!iterator.hasNext()) {
                    return
                }
                val directory = iterator.next()
                psiDirectory = PsiManager.getInstance(project).findDirectory(directory)
            } while (psiDirectory == null)
            val psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory)!!
            var pvf: VirtualFile = outPutDir
            val packageNames = psiPackage.qualifiedName
                .split("\\.".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
            for (n in packageNames) {
                pvf = pvf.findChild(n) ?: throw IOException("$n 文件夹不存在")
            }
            val allVfs: MutableSet<VirtualFile> = HashSet()
            CommonUtils.collectExportFilesNest(project, allVfs, pvf)
            val filePaths: MutableList<Path> = ArrayList()
            val jarEntryNames: MutableList<String> = ArrayList()
            val outIndex = outPutDir.path.length + 1
            for (vf in allVfs) {
                filePaths.add(vf.toNioPath())
                jarEntryNames.add(vf.path.substring(outIndex))
            }
            CommonUtils.createNewJar(
                project,
                Path.of(exportPath, psiPackage.qualifiedName + ".jar"),
                filePaths,
                jarEntryNames
            )
        }
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
package com.github.gedoor.jarpackage.util

import com.intellij.compiler.CompilerConfiguration
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.github.gedoor.jarpackage.util.Messages.info
import com.github.gedoor.jarpackage.util.Messages.notify
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.regex.Pattern

@Suppress("unused")
object CommonUtils {
    /**
     * 不需要打包的文档
     */
    private val pattern = Pattern.compile(".*?\\.(doc|docx|xls|xlsx|ppt|pptx)$", Pattern.CASE_INSENSITIVE)
    private val versionOpcodes = try {
        val apiVersion = Opcodes::class.java.getField("API_VERSION")
        apiVersion[null] as Int
    } catch (e: Exception) {
        Opcodes.API_VERSION
    }

    @JvmStatic
    fun collectExportFilesNest(project: Project, collected: MutableSet<VirtualFile>, parentVf: VirtualFile) {
        if (!parentVf.isDirectory && !pattern.matcher(parentVf.name).matches()) {
            collected.add(parentVf)
        }
        val vfs = parentVf.children
        for (child in vfs) {
            if (child.isDirectory) {
                collectExportFilesNest(project, collected, child)
            } else {
                if (!pattern.matcher(child.name).matches()) {
                    collected.add(child)
                }
            }
        }
    }

    @JvmStatic
    fun createNewJar(project: Project?, jarFileFullPath: Path, filePaths: List<Path>, entryNames: List<String?>) {
        val manifest = Manifest()
        val mainAttributes = manifest.mainAttributes
        mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainAttributes[Attributes.Name("Created-By")] = Constants.creator
        try {
            Files.newOutputStream(jarFileFullPath).use { os ->
                BufferedOutputStream(os).use { bos ->
                    JarOutputStream(bos, manifest).use { jos ->
                        info(project!!, "start package $jarFileFullPath")
                        for (i in entryNames.indices) {
                            val entryName = entryNames[i]
                            val je = JarEntry(entryName)
                            val filePath = filePaths[i]
                            // using origin entry(file) last modified time
                            je.lastModifiedTime = Files.getLastModifiedTime(filePath)
                            jos.putNextEntry(je)
                            if (!Files.isDirectory(filePath)) {
                                jos.write(Files.readAllBytes(filePath))
                            }
                            jos.closeEntry()
                            info(project, "packed $filePath")
                        }
                        info(project, "packageJar success $jarFileFullPath")
                        notify(
                            NotificationType.INFORMATION,
                            "packageJar Success",
                            jarFileFullPath.toString(),
                            listOf(ActionShowExplorer.of(jarFileFullPath))
                        )
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /**
     * check selected file can export
     *
     * @param project     project object
     * @param virtualFile selected file
     * @return true can export, false can not export
     */
    @JvmStatic
    private fun isValidExport(project: Project, virtualFile: VirtualFile): Boolean {
        val psiManager = PsiManager.getInstance(project)
        val compilerConfiguration = CompilerConfiguration.getInstance(project)
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        val compilerManager = CompilerManager.getInstance(project)
        return if (projectFileIndex.isInSourceContent(virtualFile) && virtualFile.isInLocalFileSystem) {
            if (virtualFile.isDirectory) {
                val vfd = psiManager.findDirectory(virtualFile)
                vfd != null && JavaDirectoryService.getInstance().getPackage(vfd) != null
            } else {
                compilerManager.isCompilableFileType(virtualFile.fileType) ||
                        compilerConfiguration.isCompilableResourceFile(project, virtualFile)
            }
        } else false
    }

    /**
     * lookup modules from data context
     */
    @JvmStatic
    fun findModule(context: DataContext): Array<Module>? {
        val project = CommonDataKeys.PROJECT.getData(context)!!
        val modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(context)
        return if (modules == null) {
            val module = LangDataKeys.MODULE.getData(context)
            if (module != null) {
                return arrayOf(module)
            }
            val virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context)
            if (virtualFiles.isNullOrEmpty()) {
                null
            } else findModule(project, virtualFiles)
        } else {
            modules
        }
    }

    /**
     * find modules where virtual files locating in
     *
     * @param project      project
     * @param virtualFiles selected virtual files
     * @return modules, or null
     */
    @JvmStatic
    fun findModule(project: Project, virtualFiles: Array<VirtualFile>?): Array<Module>? {
        if (virtualFiles.isNullOrEmpty()) {
            return null
        }
        val ms: MutableSet<Module> = HashSet()
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
        for (virtualFile in virtualFiles) {
            val m = projectFileIndex.getModuleForFile(virtualFile)
            if (m != null) {
                ms.add(m)
            }
        }
        return ms.toTypedArray()
    }

    /**
     * find class name define in one java file, not including inner class and anonymous class
     *
     * @param classes  psi classes in the package
     * @param javaFile current java file
     * @return class name set includes name of the class their source code in the java file
     */
    @JvmStatic
    fun findClassNameDefineIn(classes: Array<PsiClass>, javaFile: VirtualFile): Set<String> {
        val localClasses: MutableSet<String> = HashSet()
        for (psiClass in classes) {
            if (psiClass.sourceElement!!.containingFile.virtualFile == javaFile) {
                localClasses.add(psiClass.name!!)
            }
        }
        return localClasses
    }

    /**
     * find inner classes and anonymous classes belong to the ancestor class
     *  * nested call to read the class file, to parse inner classes and anonymous classes
     *
     * @param offspringClassNames store of found class name
     * @param ancestorClassFile   the ancestor class file full path
     */
    @JvmStatic
    fun findOffspringClassName(offspringClassNames: MutableSet<String?>, ancestorClassFile: Path) {
        try {
            val reader = ClassReader(Files.readAllBytes(ancestorClassFile))
            val ancestorClassName = reader.className
            reader.accept(object : ClassVisitor(versionOpcodes) {
                override fun visitInnerClass(name: String, outer: String, inner: String, access: Int) {
                    val indexSplash = name.lastIndexOf('/')
                    val className = if (indexSplash >= 0) name.substring(indexSplash + 1) else name
                    if (offspringClassNames.contains(className) || !name.startsWith(ancestorClassName)) {
                        return
                    }
                    offspringClassNames.add(className)
                    val innerClassPath = ancestorClassFile.parent.resolve("$className.class")
                    findOffspringClassName(offspringClassNames, innerClassPath)
                }
            }, arrayOfNulls(0), ClassReader.SKIP_DEBUG or ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}
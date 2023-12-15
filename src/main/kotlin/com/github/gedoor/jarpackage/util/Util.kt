package com.github.gedoor.jarpackage.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

object Util {
    @JvmStatic
    fun matchFileNamingConventions(fileName: String): Boolean {
        return fileName.matches("[^/\\\\<>*?|\"]+".toRegex())
    }

    fun iterateDirectory(project: Project, directories: HashSet<VirtualFile>, directory: VirtualFile?) {
        if (directory != null) {
            val psiDirectory = PsiManager.getInstance(project).findDirectory(directory)
            directories.add(psiDirectory!!.virtualFile)
            val psiDirectories = psiDirectory.subdirectories
            for (pd in psiDirectories) {
                iterateDirectory(project, directories, pd.virtualFile)
            }
        }
    }

    @JvmStatic
    fun getTheSameStart(strings: List<String>?): String {
        return if (!strings.isNullOrEmpty()) {
            var max = 888888
            for (string in strings) {
                if (string.length < max) {
                    max = string.length
                }
            }
            val sb = StringBuilder()
            val set = HashSet<Char>()
            for (i in 0 until max) {
                for (string in strings) {
                    set.add(string[i])
                }
                if (set.size != 1) {
                    break
                }
                sb.append(set.iterator().next())
                set.clear()
            }
            sb.toString()
        } else {
            ""
        }
    }

    private fun getMinorVersion(vs: String): Int {
        val dashIndex = vs.lastIndexOf(95.toChar())
        if (dashIndex >= 0) {
            val builder = StringBuilder()
            for (idx in dashIndex + 1 until vs.length) {
                val ch = vs[idx]
                if (!Character.isDigit(ch)) {
                    break
                }
                builder.append(ch)
            }
            if (builder.isNotEmpty()) {
                try {
                    return builder.toString().toInt()
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return 0
    }
}
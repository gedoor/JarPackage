package com.github.gedoor.jarpackage.util

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.nio.file.Files
import java.nio.file.Path

/**
 * open the file in system file explorer
 */
class ActionShowExplorer private constructor(filePath: Path) : AnAction(Constants.actionNameExplorer) {
    private val filePath: Path?

    init {
        this.filePath = filePath
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (filePath != null && !Files.isDirectory(filePath)) {
            RevealFileAction.openFile(filePath.toFile())
        } else if (filePath != null) {
            RevealFileAction.openDirectory(filePath.toFile())
        }
    }

    companion object {
        fun of(filePath: Path): ActionShowExplorer {
            return ActionShowExplorer(filePath)
        }
    }
}
package edu.illinois.cs.cs125.intellijlogger

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile

private fun countCharacter(project: Project?) {
    ApplicationManager
        .getApplication()
        .getComponent(Component::class.java).currentProjectCounters[project]?.let { counters ->
        log.trace("countCharacter (${counters.keystrokeCount})")
        counters.keystrokeCount++
    } ?: run {
        log.warn("can't get counters for project")
    }
}

class Character : TypedHandlerDelegate() {
    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        countCharacter(project)
        return Result.CONTINUE
    }
}

class Backspace : BackspaceHandlerDelegate() {
    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        return true
    }

    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        countCharacter(editor.project)
        return
    }
}

class Enter : EnterHandlerDelegate {
    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        return EnterHandlerDelegate.Result.Continue
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {
        countCharacter(editor.project)
        return EnterHandlerDelegate.Result.Continue
    }
}

/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbService
import com.intellij.task.ProjectTaskManager
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ScratchFileLanguageProvider
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.scratch.printDebugMessage
import org.jetbrains.kotlin.idea.scratch.LOG as log

class RunScratchAction : ScratchAction(
    KotlinBundle.message("scratch.run.button"),
    AllIcons.Actions.Execute
) {

    init {
        KeymapManager.getInstance().activeKeymap.getShortcuts("Kotlin.RunScratch").firstOrNull()?.let {
            templatePresentation.text += " (${KeymapUtil.getShortcutText(it)})"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val scratchPanel = getScratchPanel(editor) ?: return

        val scratchFile = scratchPanel.scratchFile
        val psiFile = scratchFile.getPsiFile() ?: return

        val isMakeBeforeRun = scratchPanel.isMakeBeforeRun()
        val isRepl = scratchPanel.isRepl()

        val provider = ScratchFileLanguageProvider.get(psiFile.language) ?: return

        val handler = provider.getOutputHandler()
        handler.onStart(scratchFile)

        log.printDebugMessage("Run Action: isMakeBeforeRun = $isMakeBeforeRun, isRepl = $isRepl")

        val module = scratchPanel.getModule()

        fun executeScratch() {
            val executor = if (isRepl) provider.createReplExecutor(scratchFile) else provider.createCompilingExecutor(scratchFile)
            if (executor == null) {
                handler.error(scratchFile, "Couldn't run ${psiFile.name}")
                handler.onFinish(scratchFile)
                return
            }

            e.presentation.isEnabled = false

            executor.addOutputHandler(handler)

            executor.addOutputHandler(object : ScratchOutputHandlerAdapter() {
                override fun onFinish(file: ScratchFile) {
                    e.presentation.isEnabled = true
                }
            })

            try {
                executor.execute()
            } catch (ex: Throwable) {
                handler.error(scratchFile, "Exception occurs during Run Scratch Action")
                handler.onFinish(scratchFile)

                e.presentation.isEnabled = true

                log.error(ex)
            }
        }

        if (isMakeBeforeRun && module != null) {
            ProjectTaskManager.getInstance(project).build(arrayOf(module)) { result ->
                if (result.isAborted || result.errors > 0) {
                    handler.error(scratchFile, "There were compilation errors in module ${module.name}")
                }

                if (DumbService.isDumb(project)) {
                    DumbService.getInstance(project).smartInvokeLater {
                        executeScratch()
                    }
                } else {
                    executeScratch()
                }
            }
        } else {
            executeScratch()
        }
    }
}

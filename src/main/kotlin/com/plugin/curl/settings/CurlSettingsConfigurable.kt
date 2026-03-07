package com.plugin.curl.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class CurlSettingsConfigurable : Configurable {

    private val curlPathField = JBTextField()

    override fun createComponent(): JComponent {
        return panel {
            row("Curl Executable Path:") {
                cell(curlPathField)
                    .comment("Default is 'curl'. Enter full path if it's not in your system PATH.")
            }
        }
    }

    override fun isModified(): Boolean {
        val state = CurlSettingsState.getInstance()
        return curlPathField.text != state.curlExecutablePath
    }

    override fun apply() {
        val state = CurlSettingsState.getInstance()
        state.curlExecutablePath = curlPathField.text
    }

    override fun reset() {
        val state = CurlSettingsState.getInstance()
        curlPathField.text = state.curlExecutablePath
    }

    override fun getDisplayName(): String = "Curl Orchestrator"
}

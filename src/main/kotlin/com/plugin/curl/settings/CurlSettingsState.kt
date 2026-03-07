package com.plugin.curl.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.plugin.curl.settings.CurlSettingsState",
    storages = [Storage("CurlOrchestratorPlugin.xml")]
)
@Service(Service.Level.APP)
class CurlSettingsState : PersistentStateComponent<CurlSettingsState> {
    var curlExecutablePath: String = "curl"

    override fun getState(): CurlSettingsState {
        return this
    }

    override fun loadState(state: CurlSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): CurlSettingsState {
            return com.intellij.openapi.application.ApplicationManager.getApplication().getService(CurlSettingsState::class.java)
        }
    }
}

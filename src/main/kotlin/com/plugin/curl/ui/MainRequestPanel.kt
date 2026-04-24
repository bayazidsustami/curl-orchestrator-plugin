package com.plugin.curl.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.plugin.curl.engine.CurlExecutorService
import com.plugin.curl.engine.CurlRequest
import com.plugin.curl.engine.HistoryManager
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.ImageIcon
import javax.swing.SwingConstants
import java.awt.Image
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.event.DocumentEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableModel
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.icons.AllIcons
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File

class MainRequestPanel(private val project: Project, private val toolWindow: ToolWindow) : Disposable {

    // History Sidebar
    private val historyListModel = DefaultListModel<CurlRequest>()
    private val historyList = JBList(historyListModel).apply {
        val renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is CurlRequest) {
                    text = "[${value.method}] ${value.url}"
                }
                return component
            }
        }
        cellRenderer = renderer
    }

    private val methodComboBox = JComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"))
    private val urlTextField = JBTextField()
    private val sendButton = JButton("Send")
    private val importButton = JButton(AllIcons.ToolbarDecorator.Import).apply { toolTipText = "Import Request Schema" }
    private val exportButton = JButton(AllIcons.ToolbarDecorator.Export).apply { toolTipText = "Export Request Schema" }

    // Query Params Table
    private val queryParamsModel = object : DefaultTableModel(arrayOf("Key", "Value"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
    }
    private val queryParamsTable = JBTable(queryParamsModel).apply {
        putClientProperty("terminateEditOnFocusLost", true)
    }
    private var isUpdatingUrlFromTable = false
    private var isUpdatingTableFromUrl = false

    // Request Headers Table
    private val headersModel = object : DefaultTableModel(arrayOf("Key", "Value"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
    }
    private val headersTable = JBTable(headersModel).apply {
        putClientProperty("terminateEditOnFocusLost", true)
    }

    // Form Data Table (File Uploads)
    private val formDataModel = object : DefaultTableModel(arrayOf("Key", "File Path"), 0) {
        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
    }
    private val formDataTable = JBTable(formDataModel).apply {
        putClientProperty("terminateEditOnFocusLost", true)
        
        // Add mouse listener to open File Chooser on the "File Path" column
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)
                    if (col == 1) { // "File Path" column
                        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                        descriptor.title = "Select File to Upload"
                        FileChooser.chooseFile(descriptor, project, null) { file ->
                            model.setValueAt(file.path, row, col)
                        }
                    }
                }
            }
        })
    }

    // Request Body Tab
    private val requestBodyTextArea = JBTextArea(10, 40)

    // Response Data
    private var responseEditor: Editor? = null
    private val responseHeadersArea = JBTextArea().apply { 
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val responseLogArea = JBTextArea().apply { 
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val responseTabs = JTabbedPane()
    private val imageLabel = JLabel("", SwingConstants.CENTER)
    private var imageTabAdded = false
    private val formatJsonButton = JButton("Format JSON")

    val content: JPanel = JPanel(BorderLayout())

    init {
        urlTextField.emptyText.text = "Enter URL (e.g., https://httpbin.org/get)"
        
        // Add one empty row so users can start typing immediately
        queryParamsModel.addRow(arrayOf("", ""))
        headersModel.addRow(arrayOf("", ""))
        formDataModel.addRow(arrayOf("", ""))
        
        initEditors()
        setupUI()
        setupListeners()
    }

    private fun initEditors() {
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument("")
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension("json")
            ?: com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE
        responseEditor = editorFactory.createViewer(document, project, com.intellij.openapi.editor.EditorKind.MAIN_EDITOR)
    }

    private fun setupUI() {
        // Build UI using Kotlin UI DSL
        val customPanel = panel {
            // Row 0: Import/Export Controls
            row {
                cell(importButton)
                cell(exportButton)
            }

            // Row 1: Method + URL + Send
            row {
                cell(methodComboBox)
                cell(urlTextField).align(AlignX.FILL).resizableColumn()
                cell(sendButton)
            }

            // Row 2: Tabs for Params, Headers, Form Data, & Body
            row {
                val requestTabs = JTabbedPane()
                val queryParamsPanel = ToolbarDecorator.createDecorator(queryParamsTable).createPanel()
                val headersPanel = ToolbarDecorator.createDecorator(headersTable).createPanel()
                val formDataPanel = ToolbarDecorator.createDecorator(formDataTable).createPanel()

                requestTabs.addTab("Params", queryParamsPanel)
                requestTabs.addTab("Headers", headersPanel)
                requestTabs.addTab("Form Data", formDataPanel)
                requestTabs.addTab("Raw Body", JBScrollPane(requestBodyTextArea))
                cell(requestTabs).align(AlignX.FILL).align(AlignY.FILL).resizableColumn()
            }

            separator()

            // Row 3: Response Area Controls (Format JSON)
            row {
                label("Response")
                cell(formatJsonButton)
            }

            // Row 4: Response Tabs
            row {
                responseEditor?.component?.let {
                    responseTabs.addTab("Body", it)
                }
                responseTabs.addTab("Headers", JBScrollPane(responseHeadersArea))
                responseTabs.addTab("Log", JBScrollPane(responseLogArea))
                cell(responseTabs).align(AlignX.FILL).align(AlignY.FILL).resizableColumn()
            }
        }.apply {
            border = JBUI.Borders.empty(15) // Add padding around the entire UI
        }

        val scrollableCustomPanel = JBScrollPane(customPanel).apply {
            border = JBUI.Borders.empty()
            verticalScrollBar.unitIncrement = 16
        }

        // Build Splitter Layout
        val splitter = JBSplitter()
        
        val historyPanel = panel {
            row {
                label("History").bold()
            }
            row {
                cell(JBScrollPane(historyList)).align(AlignX.FILL).align(AlignY.FILL).resizableColumn()
            }
        }.apply {
            border = JBUI.Borders.empty(15)
        }

        splitter.setHonorComponentsMinimumSize(true)

        val updateSplitterLayout = { isBottom: Boolean ->
            if (isBottom) {
                // Docked at bottom: history on side (left), custom on right
                splitter.orientation = false
                splitter.firstComponent = historyPanel
                splitter.secondComponent = scrollableCustomPanel
                splitter.proportion = 0.25f
            } else {
                // Docked at side: request on top (first), history below response section (second)
                splitter.orientation = true
                splitter.firstComponent = scrollableCustomPanel
                splitter.secondComponent = historyPanel
                splitter.proportion = 0.70f
            }
        }
        
        // Initial setup based on current anchor
        val isBottom = toolWindow.anchor == ToolWindowAnchor.BOTTOM
        updateSplitterLayout(isBottom)

        // Listen for ToolWindow placement changes
        project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                val window = toolWindowManager.getToolWindow("Curl Orchestrator")
                if (window != null) {
                    val newIsBottom = window.anchor == ToolWindowAnchor.BOTTOM
                    val currentOrientation = splitter.orientation
                    val expectedOrientation = !newIsBottom
                    if (currentOrientation != expectedOrientation) {
                        updateSplitterLayout(newIsBottom)
                    }
                }
            }
        })

        content.add(splitter, BorderLayout.CENTER)
        
        loadHistory()
    }

    private fun loadHistory() {
        historyListModel.clear()
        HistoryManager.getHistory().forEach { historyListModel.addElement(it) }
    }

    private fun parseRequestToUI(request: CurlRequest) {
        ApplicationManager.getApplication().invokeLater {
            isUpdatingTableFromUrl = true
            isUpdatingUrlFromTable = true
            try {
                urlTextField.text = request.url
                methodComboBox.selectedItem = request.method
                
                while (headersModel.rowCount > 0) headersModel.removeRow(0)
                while (formDataModel.rowCount > 0) formDataModel.removeRow(0)
                
                if (request.headers.isNotEmpty()) {
                    request.headers.forEach { (k, v) -> headersModel.addRow(arrayOf<Any>(k, v)) }
                } else {
                    headersModel.addRow(arrayOf<Any>("", ""))
                }
                
                if (request.formData.isNotEmpty()) {
                    request.formData.forEach { (k, v) -> formDataModel.addRow(arrayOf<Any>(k, v)) }
                } else {
                    formDataModel.addRow(arrayOf<Any>("", ""))
                }
                
                requestBodyTextArea.text = request.body ?: ""
            } finally {
                isUpdatingTableFromUrl = false
                isUpdatingUrlFromTable = false
                updateTableFromUrl() // Sync query params table with newly set URL
            }
        }
    }

    private fun setupListeners() {
        sendButton.addActionListener { executeRequest() }
        formatJsonButton.addActionListener { formatResponseJson() }
        importButton.addActionListener { importRequest() }
        exportButton.addActionListener { exportRequest() }

        historyList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = historyList.selectedValue
                if (selected != null) {
                    parseRequestToUI(selected)
                }
            }
        }

        urlTextField.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) {
                    executeRequest()
                }
            }
        })

        urlTextField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!isUpdatingUrlFromTable) {
                    updateTableFromUrl()
                }
            }
        })

        queryParamsModel.addTableModelListener { e ->
            if (e.type == TableModelEvent.UPDATE || e.type == TableModelEvent.INSERT || e.type == TableModelEvent.DELETE) {
                if (!isUpdatingTableFromUrl) {
                    updateUrlFromTable()
                }
            }
            if (e.type == TableModelEvent.UPDATE) {
                val lastRow = queryParamsModel.rowCount - 1
                if (lastRow >= 0) {
                    val k = queryParamsModel.getValueAt(lastRow, 0)?.toString() ?: ""
                    val v = queryParamsModel.getValueAt(lastRow, 1)?.toString() ?: ""
                    if (k.isNotEmpty() || v.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            if (queryParamsModel.rowCount > 0) {
                                val currentLastK = queryParamsModel.getValueAt(queryParamsModel.rowCount - 1, 0)?.toString() ?: ""
                                val currentLastV = queryParamsModel.getValueAt(queryParamsModel.rowCount - 1, 1)?.toString() ?: ""
                                if (currentLastK.isNotEmpty() || currentLastV.isNotEmpty()) {
                                    queryParamsModel.addRow(arrayOf("", ""))
                                }
                            }
                        }
                    }
                }
            }
        }

        headersModel.addTableModelListener { e ->
            if (e.type == TableModelEvent.UPDATE) {
                val lastRow = headersModel.rowCount - 1
                if (lastRow >= 0) {
                    val k = headersModel.getValueAt(lastRow, 0)?.toString() ?: ""
                    val v = headersModel.getValueAt(lastRow, 1)?.toString() ?: ""
                    if (k.isNotEmpty() || v.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            if (headersModel.rowCount > 0) {
                                val currentLastK = headersModel.getValueAt(headersModel.rowCount - 1, 0)?.toString() ?: ""
                                val currentLastV = headersModel.getValueAt(headersModel.rowCount - 1, 1)?.toString() ?: ""
                                if (currentLastK.isNotEmpty() || currentLastV.isNotEmpty()) {
                                    headersModel.addRow(arrayOf("", ""))
                                }
                            }
                        }
                    }
                }
            }
        }

        formDataModel.addTableModelListener { e ->
            if (e.type == TableModelEvent.UPDATE) {
                val lastRow = formDataModel.rowCount - 1
                if (lastRow >= 0) {
                    val k = formDataModel.getValueAt(lastRow, 0)?.toString() ?: ""
                    val v = formDataModel.getValueAt(lastRow, 1)?.toString() ?: ""
                    if (k.isNotEmpty() || v.isNotEmpty()) {
                        ApplicationManager.getApplication().invokeLater {
                            if (formDataModel.rowCount > 0) {
                                val currentLastK = formDataModel.getValueAt(formDataModel.rowCount - 1, 0)?.toString() ?: ""
                                val currentLastV = formDataModel.getValueAt(formDataModel.rowCount - 1, 1)?.toString() ?: ""
                                if (currentLastK.isNotEmpty() || currentLastV.isNotEmpty()) {
                                    formDataModel.addRow(arrayOf("", ""))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateTableFromUrl() {
        isUpdatingTableFromUrl = true
        try {
            val urlStr = urlTextField.text
            val queryIndex = urlStr.indexOf('?')
            if (queryIndex != -1 && queryIndex < urlStr.length - 1) {
                val queryStr = urlStr.substring(queryIndex + 1)
                val pairs = queryStr.split("&")

                while (queryParamsModel.rowCount > 0) {
                    queryParamsModel.removeRow(0)
                }

                for (pair in pairs) {
                    if (pair.isEmpty()) continue
                    val kv = pair.split("=", limit = 2)
                    val k = kv[0]
                    val v = if (kv.size > 1) kv[1] else ""
                    queryParamsModel.addRow(arrayOf(k, v))
                }
            } else {
                while (queryParamsModel.rowCount > 0) {
                    queryParamsModel.removeRow(0)
                }
                queryParamsModel.addRow(arrayOf("", ""))
            }
        } finally {
            isUpdatingTableFromUrl = false
        }
    }

    private fun updateUrlFromTable() {
        isUpdatingUrlFromTable = true
        try {
            var urlStr = urlTextField.text
            val queryIndex = urlStr.indexOf('?')
            if (queryIndex != -1) {
                urlStr = urlStr.substring(0, queryIndex)
            }

            val queryParams = mutableListOf<String>()
            for (i in 0 until queryParamsModel.rowCount) {
                val key = queryParamsModel.getValueAt(i, 0)?.toString() ?: ""
                val value = queryParamsModel.getValueAt(i, 1)?.toString() ?: ""
                if (key.isNotEmpty()) {
                    queryParams.add("$key=$value")
                }
            }

            if (queryParams.isNotEmpty()) {
                urlTextField.text = urlStr + "?" + queryParams.joinToString("&")
            } else {
                urlTextField.text = urlStr
            }
        } finally {
            isUpdatingUrlFromTable = false
        }
    }

    private fun executeRequest() {
        val url = urlTextField.text.trim()
        if (url.isEmpty()) {
            Messages.showErrorDialog("Please enter a valid URL", "Error")
            return
        }

        val method = methodComboBox.selectedItem as String
        val headers = getHeadersFromTable()
        val formData = getFormDataFromTable()
        val body = requestBodyTextArea.text

        val request = CurlRequest(url, method, headers, formData, body)

        // Save to History natively
        HistoryManager.saveRequest(request)
        loadHistory()

        // Disable UI while running
        sendButton.isEnabled = false
        sendButton.text = "Sending..."

        val executor = project.getService(CurlExecutorService::class.java)

        ApplicationManager.getApplication().executeOnPooledThread {
            val response = executor.execute(request)

            // Update UI on EDT
            ApplicationManager.getApplication().invokeLater {
                sendButton.isEnabled = true
                sendButton.text = "Send"

                // Handle Image Mode specifically
                if (response.isImage && response.imageBytes != null && response.imageBytes.isNotEmpty()) {
                    try {
                        val bais = ByteArrayInputStream(response.imageBytes)
                        val bufferedImage = ImageIO.read(bais)
                        if (bufferedImage != null) {
                            val icon = ImageIcon(bufferedImage)
                            imageLabel.icon = icon
                            
                            if (!imageTabAdded) {
                                responseTabs.insertTab("Image View", null, JBScrollPane(imageLabel), null, 0)
                                imageTabAdded = true
                            }
                            responseTabs.selectedIndex = 0
                            updateResponseText(response.body) // The binary fallback text
                        } else {
                            handleTextResponse(response)
                        }
                    } catch (e: Exception) {
                         handleTextResponse(response)
                    }
                } else {
                    handleTextResponse(response)
                }

                val headerStr = buildString {
                    appendLine("Status Code: ${response.statusCode}")
                    appendLine("Time: ${response.timeMillis} ms")
                    appendLine("-------------------------")
                    response.headers.forEach { (k, v) -> appendLine("$k: $v") }
                }
                responseHeadersArea.text = headerStr
                
                responseLogArea.text = "Command Executed:\n${response.command}\n\nRaw Output:\n${response.rawOutput}"
            }
        }
    }

    private fun handleTextResponse(response: com.plugin.curl.engine.CurlResponse) {
        if (imageTabAdded) {
            responseTabs.removeTabAt(0)
            imageTabAdded = false
        }
        
        if (response.error != null) {
            updateResponseText("Error: ${response.error}")
        } else {
            updateResponseText(response.body)
        }
    }

    private fun getHeadersFromTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until headersModel.rowCount) {
            val key = headersModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
            val value = headersModel.getValueAt(i, 1)?.toString()?.trim() ?: ""
            if (key.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun getFormDataFromTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until formDataModel.rowCount) {
            val key = formDataModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
            val value = formDataModel.getValueAt(i, 1)?.toString()?.trim() ?: ""
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    private fun updateResponseText(text: String) {
        val normalizedText = com.intellij.openapi.util.text.StringUtil.convertLineSeparators(text)
        ApplicationManager.getApplication().runWriteAction {
            responseEditor?.document?.setText(normalizedText)
        }
    }

    private fun exportRequest() {
        val url = urlTextField.text.trim()
        val method = methodComboBox.selectedItem as String
        val headers = getHeadersFromTable()
        val formData = getFormDataFromTable()
        val body = requestBodyTextArea.text
        
        val request = CurlRequest(url, method, headers, formData, body)
        val jsonString = GsonBuilder().setPrettyPrinting().create().toJson(request)
        
        val descriptor = FileSaverDescriptor("Export Request", "Save Curl Request as JSON", "json")
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val virtualFileWrapper = dialog.save(null as VirtualFile?, "request.json")
        
        virtualFileWrapper?.file?.let { file ->
            try {
                file.writeText(jsonString)
                Messages.showInfoMessage("Request exported successfully!", "Export Success")
            } catch (e: Exception) {
                Messages.showErrorDialog("Failed to save file: ${e.message}", "Export Error")
            }
        }
    }

    private fun importRequest() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension == "json" }
        descriptor.title = "Select Request JSON File"
        
        FileChooser.chooseFile(descriptor, project, null) { file ->
            try {
                val jsonString = File(file.path).readText()
                val type = object : TypeToken<CurlRequest>() {}.type
                val request: CurlRequest = GsonBuilder().create().fromJson(jsonString, type)
                parseRequestToUI(request)
            } catch (e: Exception) {
                Messages.showErrorDialog("Failed to load or parse JSON: ${e.message}", "Import Error")
            }
        }
    }

    private fun formatResponseJson() {
        val raw = responseEditor?.document?.text ?: return
        if (raw.isBlank()) return

        try {
            val element = JsonParser.parseString(raw)
            val gson = GsonBuilder().setPrettyPrinting().create()
            val formatted = gson.toJson(element)
            updateResponseText(formatted)
        } catch (e: Exception) {
            Messages.showInfoMessage("Response body is not valid JSON.", "Cannot Format")
        }
    }

    override fun dispose() {
        responseEditor?.let {
            if (!it.isDisposed) {
                EditorFactory.getInstance().releaseEditor(it)
            }
        }
    }
}

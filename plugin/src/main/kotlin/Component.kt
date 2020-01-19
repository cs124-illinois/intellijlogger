package edu.illinois.cs.cs125.intellijlogger

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import java.io.File
import java.net.NetworkInterface
import java.nio.file.Files
import java.time.Instant
import java.util.Properties
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.timer
import org.apache.commons.httpclient.HttpStatus
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContextBuilder
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml

val log = Logger.getInstance("edu.illinois.cs.cs125.intellijlogger")

val version: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs125.intellijlogger.version"))
}.getProperty("version")
val intellijVersion: String = ApplicationInfo.getInstance().strictVersion

private const val STATE_TIMER_PERIOD_SEC = 5
private const val MAX_SAVED_COUNTERS = (2 * 60 * 60 / STATE_TIMER_PERIOD_SEC) // 2 hours of logs
private const val UPLOAD_LOG_COUNT_THRESHOLD = (5 * 60 / STATE_TIMER_PERIOD_SEC) // 5 minutes of logs
private const val EMPTY_LOG_COUNT_THRESHOLD = (2 * 60 / STATE_TIMER_PERIOD_SEC) // 2 minutes of inactivity
private const val SHORTEST_UPLOAD_WAIT = 5 * 60 * 1000 // 5 minutes
private const val SHORTEST_UPLOAD_INTERVAL = 10 * 60 * 1000 // 10 minutes
private const val SECONDS_TO_MILLISECONDS = 1000L

@Suppress("TooManyFunctions")
class Component :
    BaseComponent,
    CaretListener,
    VisibleAreaListener,
    EditorMouseListener,
    SelectionListener,
    DocumentListener,
    ProjectManagerListener,
    CompilationStatusListener,
    FileEditorManagerListener,
    RunManagerListener,
    ExecutionListener,
    Disposable {

    @NotNull
    override fun getComponentName(): String {
        return "CS125 Plugin"
    }

    var currentProjectCounters = mutableMapOf<Project, Counter>()

    data class ProjectConfiguration(
        val destination: String,
        val name: String,
        val emailLocation: String?,
        var email: String?,
        val networkAddress: String?,
        val buttonAction: String?,
        val trustSelfSignedCertificates: Boolean
    )

    val projectConfigurations = mutableMapOf<Project, ProjectConfiguration>()

    data class ProjectState(
        var currentRunConfiguration: String?
    )

    private val projectStates = mutableMapOf<Project, ProjectState>()

    override fun initComponent() {
        log.trace("initComponent")

        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(ProjectManager.TOPIC, this)

        val state = Persistence.getInstance().persistentState
        log.trace("Loading " + state.savedCounters.size.toString() + " counters")

        if (state.UUID == "") {
            state.UUID = UUID.randomUUID().toString()
            if (state.savedCounters.size != 0) {
                log.warn("Must be updating plugin since saved counters exist before UUID is set")
            }
        }
        for (counter in state.savedCounters) {
            if (counter.UUID != state.UUID) {
                log.warn("Altering counter with bad UUID: ${counter.UUID} != ${state.UUID}")
                counter.UUID = state.UUID
            }
        }
        for (counter in state.activeCounters) {
            if (counter.UUID != state.UUID) {
                log.warn("Altering counter with bad UUID: ${counter.UUID} != ${state.UUID}")
                counter.UUID = state.UUID
            }
            counter.end = state.lastSave
            state.savedCounters.add(counter)
        }
        state.activeCounters.clear()

        ApplicationManager.getApplication().invokeLater {
            EditorFactory.getInstance().eventMulticaster.addCaretListener(this, this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this, this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this, this)
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(this, this)
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, this)
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().eventMulticaster.removeCaretListener(this)
        EditorFactory.getInstance().eventMulticaster.removeVisibleAreaListener(this)
        EditorFactory.getInstance().eventMulticaster.removeEditorMouseListener(this)
        EditorFactory.getInstance().eventMulticaster.removeSelectionListener(this)
        EditorFactory.getInstance().eventMulticaster.removeDocumentListener(this)
    }

    var uploadBusy = false
    var lastUploadFailed = false
    var lastUploadAttempt: Long = 0
    var lastSuccessfulUpload: Long = 0

    @Suppress("ReturnCount", "TooGenericExceptionCaught", "ComplexMethod")
    @Synchronized
    fun uploadCounters() {
        log.trace("uploadCounters")
        if (uploadBusy) {
            log.warn("Previous upload still busy")
            return
        }

        val state = Persistence.getInstance().persistentState
        if (state.savedCounters.size == 0) {
            log.trace("No counters to upload")
            return
        }

        if (lastUploadFailed && Instant.now().toEpochMilli() - lastUploadAttempt <= SHORTEST_UPLOAD_WAIT) {
            log.trace("Need to wait for longer to retry upload")
            return
        }

        val startIndex = 0
        val endIndex = state.savedCounters.size

        val uploadingCounters = mutableListOf<Counter>()
        uploadingCounters.addAll(state.savedCounters)

        if (uploadingCounters.isEmpty()) {
            log.trace("No counters to upload")
            return
        }

        val project = try {
            ProjectManager.getInstance().openProjects.find { project ->
                val window = WindowManager.getInstance().suggestParentWindow(project)
                window != null && window.isFocused
            } ?: ProjectManager.getInstance().openProjects[0]
        } catch (e: Exception) {
            null
        }

        if (project == null) {
            log.warn("Can't find project in uploadCounters")
            return
        }

        val uploadCounterTask = object : Task.Backgroundable(
            project, "Uploading CS 125 logs...",
            false
        ) {
            override fun run(progressIndicator: ProgressIndicator) {
                val now = Instant.now().toEpochMilli()

                val projectConfiguration = projectConfigurations[project]
                if (projectConfiguration == null) {
                    log.warn("no configuration for project in uploadTask")
                    return
                }

                val json = Counters.adapter.toJson(Counters(uploadingCounters))
                if (json == null) {
                    log.warn("couldn't convert counters to JSON")
                    return
                }

                val destination = projectConfiguration.destination
                if (destination == "console") {
                    log.warn("Uploading to console")
                    log.trace(json)
                    state.savedCounters.subList(startIndex, endIndex).clear()
                    log.trace("Upload succeeded")
                    lastSuccessfulUpload = now
                    lastUploadFailed = false
                    return
                }

                val httpClient = if (projectConfiguration.trustSelfSignedCertificates) {
                    HttpClients.custom().setSSLSocketFactory(
                        SSLConnectionSocketFactory(
                            SSLContextBuilder.create().loadTrustMaterial(TrustSelfSignedStrategy()).build(),
                            NoopHostnameVerifier()
                        )
                    ).build()
                } else {
                    HttpClientBuilder.create().build()
                }
                val counterPost = HttpPost(destination)
                counterPost.addHeader("content-type", "application/json")
                counterPost.entity = StringEntity(json)

                log.trace(
                    "Uploading ${state.savedCounters[startIndex].index}..${state.savedCounters[endIndex - 1].index}"
                )

                lastUploadFailed = try {
                    val response = httpClient.execute(counterPost)
                    assert(response.statusLine.statusCode == HttpStatus.SC_OK) {
                        "upload failed: ${response.statusLine}"
                    }

                    state.savedCounters.subList(startIndex, endIndex).clear()

                    log.trace("Upload succeeded")
                    lastSuccessfulUpload = now

                    false
                } catch (e: Throwable) {
                    log.warn("Upload failed: $e")
                    true
                } finally {
                    uploadBusy = false
                    lastUploadAttempt = now
                }
            }
        }
        ProgressManager.getInstance().run(uploadCounterTask)
    }

    private var emptyIntervals = 0
    @Synchronized
    fun rotateCounters() {
        log.trace("rotateCounters")

        val state = Persistence.getInstance().persistentState
        val end = Instant.now().toEpochMilli()

        if (currentProjectCounters.values.none { !it.isEmpty() }) {
            log.trace("$emptyIntervals empty intervals")
            emptyIntervals++
        } else {
            emptyIntervals = 0
            for ((project, counter) in currentProjectCounters) {
                if (counter.isEmpty()) {
                    continue
                }
                counter.end = end

                val fileDocumentManager = FileDocumentManager.getInstance()
                val openFiles: MutableMap<String, FileInfo> = mutableMapOf()
                for (file in FileEditorManager.getInstance(project).openFiles.filterNotNull()) {
                    val document = fileDocumentManager.getCachedDocument(file) ?: continue
                    openFiles[file.path] = FileInfo(file.path, document.lineCount)
                }
                counter.openFiles = openFiles.values.toMutableList()
                counter.openFileCount = counter.openFiles.size
                counter.closed = false

                log.trace("Counter $counter")

                state.savedCounters.add(counter)
                state.activeCounters.remove(counter)

                val newCounter = Counter(
                    state.UUID,
                    state.counterIndex++,
                    counter.index,
                    projectConfigurations[project]?.name,
                    projectConfigurations[project]?.email,
                    projectConfigurations[project]?.networkAddress,
                    version,
                    intellijVersion
                )
                currentProjectCounters[project] = newCounter
                state.activeCounters.add(newCounter)
            }
        }

        if (state.savedCounters.size > MAX_SAVED_COUNTERS) {
            state.savedCounters.subList(0, MAX_SAVED_COUNTERS - state.savedCounters.size).clear()
        }

        val now = Instant.now().toEpochMilli()
        if (state.savedCounters.size >= UPLOAD_LOG_COUNT_THRESHOLD) {
            log.trace("Log storage interval exceeded")
            uploadCounters()
        } else if (now - lastSuccessfulUpload > SHORTEST_UPLOAD_INTERVAL) {
            log.trace("Upload interval exceeded")
            uploadCounters()
        } else if (emptyIntervals > EMPTY_LOG_COUNT_THRESHOLD && state.savedCounters.size > 0) {
            log.trace("Quiescent interval exceeded")
            uploadCounters()
        }
    }

    private var stateTimer: Timer? = null
    @Suppress("ComplexMethod", "LongMethod", "NestedBlockDepth", "TooGenericExceptionCaught")
    override fun projectOpened(project: Project) {
        log.trace("projectOpened")

        val configurationFile = File(project.basePath.toString()).resolve(File(".intellijlogger.yaml"))
        if (!configurationFile.exists()) {
            log.trace("no project configuration found")
            return
        }

        val projectConfiguration = try {
            @Suppress("UNCHECKED_CAST")
            val configuration = Yaml().load(Files.newBufferedReader(configurationFile.toPath())) as Map<String, String>

            val destination = configuration["destination"]
                ?: throw IllegalArgumentException("destination missing from configuration")
            val name = configuration["name"] ?: throw IllegalArgumentException("name missing from configuration")

            val emailLocation = configuration["emailLocation"]
            val email = if (emailLocation == null) {
                null
            } else {
                File(project.basePath.toString()).resolve(File(emailLocation)).let {
                    if (it.exists()) {
                        it.readText().trim()
                    } else {
                        null
                    }
                }
            }

            @Suppress("MagicNumber")
            val networkAddress = try {
                NetworkInterface.getNetworkInterfaces().toList().flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList()
                        .filter { it.address.size == 4 }
                        .filter { !it.isLoopbackAddress }
                        .filter { it.address[0] != 10.toByte() }
                        .map { it.hostAddress }
                }.first()
            } catch (e: Exception) {
                null
            }

            val buttonAction = try {
                configuration["buttonAction"]
            } catch (e: Exception) {
                null
            }

            @Suppress("CAST_NEVER_SUCCEEDS")
            val trustSelfSignedCertificates = try {
                configuration["trustSelfSignedCertificates"] as Boolean
            } catch (e: Exception) {
                false
            }

            ProjectConfiguration(
                destination,
                name,
                emailLocation,
                email,
                networkAddress,
                buttonAction,
                trustSelfSignedCertificates
            )
        } catch (e: Exception) {
            log.debug("Can't load project configuration: $e")
            return
        }

        log.trace(projectConfiguration.toString())
        projectConfigurations[project] = projectConfiguration

        val state = Persistence.getInstance().persistentState

        val newCounter = Counter(
            state.UUID,
            state.counterIndex++,
            -1,
            projectConfiguration.name,
            projectConfiguration.email,
            projectConfiguration.networkAddress,
            version,
            intellijVersion
        )
        newCounter.opened = true
        currentProjectCounters[project] = newCounter
        state.activeCounters.add(newCounter)

        if (currentProjectCounters.size == 1) {
            stateTimer?.cancel()
            stateTimer = timer(
                "edu.illinois.cs.cs125",
                true,
                STATE_TIMER_PERIOD_SEC * SECONDS_TO_MILLISECONDS,
                STATE_TIMER_PERIOD_SEC * SECONDS_TO_MILLISECONDS
            ) {
                rotateCounters()
            }
        }
        uploadCounters()

        project.messageBus.connect().subscribe(CompilerTopics.COMPILATION_STATUS, this)
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        project.messageBus.connect().subscribe(RunManagerListener.TOPIC, this)
        project.messageBus.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, this)

        projectStates[project] = ProjectState(RunManager.getInstance(project).selectedConfiguration?.name)
    }

    override fun projectClosing(project: Project) {
        log.trace("projectClosing")

        val currentCounter = currentProjectCounters[project] ?: return
        val state = Persistence.getInstance().persistentState

        // We save this counter regardless of whether it has counts just to mark the end of a session
        currentCounter.end = Instant.now().toEpochMilli()
        state.savedCounters.add(currentCounter)
        state.activeCounters.remove(currentCounter)

        currentProjectCounters.remove(project)
        if (currentProjectCounters.isEmpty()) {
            stateTimer?.cancel()
        }
        // Force an immediate upload
        lastSuccessfulUpload = 0
        uploadCounters()
        
        projectConfigurations.remove(project)
        projectStates.remove(project)

        return
    }

    override fun caretAdded(caretEvent: CaretEvent) {
        val projectCounter = currentProjectCounters[caretEvent.editor.project] ?: return
        log.trace("caretAdded")
        projectCounter.caretAdded++
        return
    }

    override fun caretRemoved(caretEvent: CaretEvent) {
        val projectCounter = currentProjectCounters[caretEvent.editor.project] ?: return
        log.trace("caretRemoved")
        projectCounter.caretRemoved++
        return
    }

    override fun caretPositionChanged(caretEvent: CaretEvent) {
        val projectCounter = currentProjectCounters[caretEvent.editor.project] ?: return
        log.trace("caretPositionChanged")
        projectCounter.caretPositionChangedCount++
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        val projectCounter = currentProjectCounters[visibleAreaEvent.editor.project] ?: return
        log.trace("visibleAreaChanged")
        projectCounter.visibleAreaChangedCount++
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        val projectCounter = currentProjectCounters[editorMouseEvent.editor.project] ?: return
        log.trace("mousePressed")
        projectCounter.mousePressedCount++
    }

    override fun mouseClicked(editorMouseEvent: EditorMouseEvent) {
        val projectCounter = currentProjectCounters[editorMouseEvent.editor.project] ?: return
        log.trace("mouseActivity")
        projectCounter.mouseActivityCount++
    }

    override fun mouseReleased(editorMouseEvent: EditorMouseEvent) {
        val projectCounter = currentProjectCounters[editorMouseEvent.editor.project] ?: return
        log.trace("mouseActivity")
        projectCounter.mouseActivityCount++
    }

    override fun mouseEntered(editorMouseEvent: EditorMouseEvent) {
        val projectCounter = currentProjectCounters[editorMouseEvent.editor.project] ?: return
        log.trace("mouseActivity")
        projectCounter.mouseActivityCount++
    }

    override fun mouseExited(editorMouseEvent: EditorMouseEvent) {
        val projectCounter = currentProjectCounters[editorMouseEvent.editor.project] ?: return
        log.trace("mouseActivity")
        projectCounter.mouseActivityCount++
    }

    override fun selectionChanged(selectionEvent: SelectionEvent) {
        val projectCounter = currentProjectCounters[selectionEvent.editor.project] ?: return
        log.trace("selectionChanged")
        projectCounter.selectionChangedCount++
    }

    @Suppress("TooGenericExceptionCaught")
    override fun documentChanged(documentEvent: DocumentEvent) {
        log.trace("documentChanged")

        val changedFile = FileDocumentManager.getInstance().getFile(documentEvent.document)
        for ((project, info) in projectConfigurations) {
            if (info.emailLocation == null) {
                continue
            }
            try {
                val emailPath = File(project.basePath.toString()).resolve(File(info.emailLocation)).canonicalPath
                if (changedFile?.canonicalPath.equals(emailPath)) {
                    info.email = documentEvent.document.text.trim()
                    log.debug("Updated email for project " + info.name + ": " + info.email)
                }
            } catch (e: Throwable) {
                // Ignore errors here so that we can proceed
            }
        }

        val editors = EditorFactory.getInstance().getEditors(documentEvent.document)
        for (editor in editors) {
            val projectCounter = currentProjectCounters[editor.project] ?: continue
            projectCounter.documentChangedCount++
        }
    }

    override fun beforeDocumentChange(event: DocumentEvent) {
        return
    }

    override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
        if (aborted) {
            return
        }
        val projectCounter = currentProjectCounters[compileContext.project] ?: return
        log.trace("compilationFinished")
        projectCounter.compileCount++
        if (errors == 0) {
            projectCounter.successfulCompileCount++
        } else {
            projectCounter.failedCompileCount++
        }
        projectCounter.compilerErrorCount += errors
        projectCounter.compilerWarningCount += warnings
    }

    override fun fileOpened(manager: FileEditorManager, file: VirtualFile) {
        val projectCounter = currentProjectCounters[manager.project] ?: return
        log.trace("fileOpened")
        projectCounter.fileOpenedCount++
    }

    override fun fileClosed(manager: FileEditorManager, file: VirtualFile) {
        val projectCounter = currentProjectCounters[manager.project] ?: return
        log.trace("fileClosed")
        projectCounter.fileClosedCount++
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val projectCounter = currentProjectCounters[event.manager.project] ?: return
        log.trace("fileSelectionChanged")
        projectCounter.fileSelectionChangedCount++
        projectCounter.selectedFile = event.newFile?.path ?: ""
    }

    override fun runConfigurationSelected(runnerAndConfigurationSettings: RunnerAndConfigurationSettings?) {
        val projectState = projectStates[runnerAndConfigurationSettings?.configuration?.project] ?: return
        projectState.currentRunConfiguration = runnerAndConfigurationSettings?.name
    }

    override fun processStarted(
        executorId: String,
        executionEnvironment: ExecutionEnvironment,
        processHandler: ProcessHandler
    ) {
        val projectCounter = currentProjectCounters[executionEnvironment.project] ?: return
        val projectState = projectStates[executionEnvironment.project] ?: return

        log.trace("processStarted")
        projectCounter.totalRunCount++
        if (projectState.currentRunConfiguration == null) {
            log.warn("current run configuration not set")
            return
        }
        val runCounter =
            projectCounter.runCounts.find { it.name === projectState.currentRunConfiguration } ?: RunCounter(
                projectState.currentRunConfiguration!!
            ).also {
                projectCounter.runCounts.add(it)
            }
        runCounter.started++
    }
}

internal fun getCounters(project: Project): Counter? {
    return ApplicationManager.getApplication().getComponent(Component::class.java).currentProjectCounters[project]
}

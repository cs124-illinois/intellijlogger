package edu.illinois.cs.cs125.intellijlogger

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.ServiceManager
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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.httpclient.HttpStatus
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContextBuilder
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.NetworkInterface
import java.nio.file.Files
import java.time.Instant
import java.util.Properties
import java.util.Timer
import kotlin.concurrent.timer

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
class StartupActivity :
    StartupActivity,
    CaretListener,
    // VisibleAreaListener,
    EditorMouseListener,
    SelectionListener,
    DocumentListener,
    ProjectManagerListener,
    CompilationStatusListener,
    FileEditorManagerListener,
    RunManagerListener,
    ExecutionListener {

    private val currentProjectCounters by lazy {
        ServiceManager.getService(ApplicationService::class.java).currentProjectCounters
    }

    private val projectConfigurations by lazy {
        ServiceManager.getService(ApplicationService::class.java).projectConfigurations
    }

    data class ProjectState(
        var currentRunConfiguration: String?
    )

    private val projectStates = mutableMapOf<Project, ProjectState>()

    private var uploadBusy = false
    var lastUploadFailed = false
    private var lastUploadAttempt: Long = 0
    var lastSuccessfulUpload: Long = 0

    @Suppress("ReturnCount", "TooGenericExceptionCaught", "ComplexMethod", "NestedBlockDepth")
    @Synchronized
    fun uploadCounters() {
        log.trace("uploadCounters")
        if (uploadBusy) {
            log.warn("Previous upload still busy")
            return
        }

        val state = ApplicationService.getInstance()._state
        if (state.savedCounters.size == 0) {
            log.trace("No counters to upload")
            return
        }
        if (lastUploadFailed && Instant.now().toEpochMilli() - lastUploadAttempt <= SHORTEST_UPLOAD_WAIT) {
            log.trace("Need to wait for longer to retry upload")
            return
        }
        val currentCount = state.savedCounters.size

        val uploadCounterTask = object : Task.Backgroundable(
            null, "Uploading CS 125 logs...",
            false
        ) {
            @Suppress("LongMethod")
            override fun run(progressIndicator: ProgressIndicator) {
                try {
                    if (uploadBusy) {
                        log.warn("Previous upload still busy")
                        return
                    }
                    val now = Instant.now().toEpochMilli()
                    uploadBusy = true

                    @Suppress("LoopWithTooManyJumpStatements")
                    for (index in 0..currentCount) {
                        if (state.savedCounters.isEmpty()) {
                            break
                        }
                        val counter = state.savedCounters.first()
                        val json = Counter.adapter.toJson(counter)
                        if (counter.destination === "console") {
                            log.trace(json)
                            lastUploadFailed = false
                        } else {
                            val httpClient = if (counter.trustSelfSignedCertificates) {
                                HttpClients.custom().setSSLSocketFactory(
                                    SSLConnectionSocketFactory(
                                        SSLContextBuilder.create().loadTrustMaterial(TrustSelfSignedStrategy()).build(),
                                        NoopHostnameVerifier()
                                    )
                                ).build()
                            } else {
                                HttpClientBuilder.create().build()
                            }

                            val counterPost = HttpPost(counter.destination).also {
                                it.addHeader("content-type", "application/json")
                                it.entity = StringEntity(json)
                            }

                            lastUploadFailed = try {
                                val response = httpClient.execute(counterPost)
                                assert(response.statusLine.statusCode == HttpStatus.SC_OK) {
                                    "upload failed: ${response.statusLine}"
                                }
                                false
                            } catch (e: Throwable) {
                                log.warn("Upload failed: $e")
                                true
                            }
                        }
                        if (lastUploadFailed) {
                            break
                        }
                        log.trace("Upload succeeded (${counter.index}) -> ${counter.destination}")
                        lastSuccessfulUpload = now
                        synchronized(state.savedCounters) {
                            try {
                                state.savedCounters.removeAt(0)
                            } catch (e: Exception) {
                                log.warn("Problem removing head counter: $e")
                            }
                        }
                    }
                } finally {
                    uploadBusy = false
                }
            }
        }
        ProgressManager.getInstance().run(uploadCounterTask)
    }

    private var emptyIntervals = 0

    @Synchronized
    @Suppress("ComplexMethod", "LongMethod")
    fun rotateCounters() {
        log.trace("rotateCounters")

        val state = ApplicationService.getInstance()._state
        val end = Instant.now().toEpochMilli()

        if (currentProjectCounters.values.none { !it.isEmpty() }) {
            log.trace("$emptyIntervals empty intervals")
            emptyIntervals++
        } else {
            emptyIntervals = 0
            @Suppress("LoopWithTooManyJumpStatements")
            for ((project, counter) in currentProjectCounters) {
                if (counter.isEmpty()) {
                    continue
                }
                val projectConfiguration = projectConfigurations[project]
                if (projectConfiguration == null) {
                    log.warn("Missing project configuration in rotate")
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

                synchronized(state.savedCounters) {
                    state.savedCounters.add(counter)
                }
                state.activeCounters.remove(counter)

                val newCounter = Counter(
                    projectConfiguration.destination,
                    projectConfiguration.trustSelfSignedCertificates,
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
    override fun runActivity(project: Project) {
        log.info("projectOpened")

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

        val stateService = ApplicationService.getInstance()
        val state = stateService._state

        val newCounter = Counter(
            projectConfiguration.destination,
            projectConfiguration.trustSelfSignedCertificates,
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
            ApplicationManager.getApplication().invokeLater {
                EditorFactory.getInstance().eventMulticaster.addCaretListener(this, stateService)
                // EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this, this)
                EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this, stateService)
                EditorFactory.getInstance().eventMulticaster.addSelectionListener(this, stateService)
                EditorFactory.getInstance().eventMulticaster.addDocumentListener(this, stateService)
            }
        }
        uploadCounters()

        project.messageBus.connect().subscribe(CompilerTopics.COMPILATION_STATUS, this)
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        project.messageBus.connect().subscribe(RunManagerListener.TOPIC, this)
        project.messageBus.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, this)

        projectStates[project] = ProjectState(RunManager.getInstance(project).selectedConfiguration?.name)

        Disposer.register(
            ServiceManager.getService(project, ProjectService::class.java),
            {
                projectClosing(project)
            }
        )
    }

    override fun projectClosing(project: Project) {
        log.info("projectClosing")

        val currentCounter = currentProjectCounters[project] ?: return
        val state = ApplicationService.getInstance()._state

        // We save this counter regardless of whether it has counts just to mark the end of a session
        currentCounter.end = Instant.now().toEpochMilli()
        state.savedCounters.add(currentCounter)
        state.activeCounters.remove(currentCounter)

        currentProjectCounters.remove(project)
        if (currentProjectCounters.isEmpty()) {
            stateTimer?.cancel()

            EditorFactory.getInstance().eventMulticaster.removeCaretListener(this)
            // EditorFactory.getInstance().eventMulticaster.removeVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.removeEditorMouseListener(this)
            EditorFactory.getInstance().eventMulticaster.removeSelectionListener(this)
            EditorFactory.getInstance().eventMulticaster.removeDocumentListener(this)
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

    /*
    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        val projectCounter = currentProjectCounters[visibleAreaEvent.editor.project] ?: return
        log.trace("visibleAreaChanged")
        projectCounter.visibleAreaChangedCount++
    }
    */

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

package edu.illinois.cs.cs125.intellijplugin.server

import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import com.ryanharter.ktor.moshi.moshi
import com.squareup.moshi.*
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.source.json.toJson
import com.uchuhimo.konf.source.yaml
import edu.illinois.cs.cs125.intellijplugin.CS125Component
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.features.origin
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mu.KotlinLogging
import org.bson.BsonDocument
import java.io.File
import java.net.URI
import java.time.Instant
import java.util.*

@Suppress("UNUSED")
private val logger = KotlinLogging.logger {}

const val DEFAULT_HTTP = "http://0.0.0.0:8888"
const val DEFAULT_MONGO_COLLECTION = "intellijlogger"

object TopLevel : ConfigSpec("") {
    val http by optional(DEFAULT_HTTP)
    val semester by optional<String?>(null)
    val mongo by required<String>()
    val mongoCollection by optional(DEFAULT_MONGO_COLLECTION)
}

val configuration = Config {
    addSpec(TopLevel)
}.let {
    if (File("config.yaml").exists() && File("config.yaml").length() > 0) {
        it.from.yaml.file("config.yaml")
    }
    it.from.env()
}

val mongoCollection: MongoCollection<BsonDocument> = configuration[TopLevel.mongo].run {
    val uri = MongoClientURI(this)
    val database = uri.database ?: assert {"MONGO must specify database to use" }
    val collection = configuration[TopLevel.mongoCollection]
    MongoClient(uri).getDatabase(database).getCollection(collection, BsonDocument::class.java)
}

@JsonClass(generateAdapter = true)
data class ReceivedCounter(
        val counter: CS125Component.Counter,
        val receivedTime: Instant,
        val receivedIP: String,
        val receivedSemester: String?
) {
    companion object {
        val adapter: JsonAdapter<ReceivedCounter> = Moshi.Builder().build().adapter(ReceivedCounter::class.java)
    }
}
@Suppress("unused")
class InstantAdapter {
    @FromJson
    fun instantFromJson(timestamp: String): Instant {
        return Instant.parse(timestamp)
    }
    @ToJson
    fun instantToJson(instant: Instant): String {
        return instant.toString()
    }
}

fun Application.intellijlogger() {
    install(XForwardedHeaderSupport)
    install(CORS) {
        anyHost()
    }
    install(ContentNegotiation) {
        moshi {
            this.add(InstantAdapter())
        }
    }
    routing {
        get("/") {
            call.respond(currentStatus)
        }
        post("/") {
            val upload = try {
                call.receive<CS125Component.Counters>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest)
                currentStatus.failureCount++
                return@post
            }

            try {
                val receivedTime = Instant.now()
                val receivedIP = call.request.origin.remoteHost
                val receivedSemester = configuration[TopLevel.semester]

                val receivedCounters = upload.counters.map { counter ->
                    val receivedCounter = ReceivedCounter(counter, receivedTime, receivedIP, receivedSemester)
                    BsonDocument.parse(ReceivedCounter.adapter.toJson(receivedCounter))
                }
                mongoCollection.insertMany(receivedCounters)
                currentStatus.uploadCount += receivedCounters.size

                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError)
                currentStatus.failureCount++
                return@post
            }
        }
    }
    intercept(ApplicationCallPipeline.Fallback) {
        if (call.response.status() == null) { call.respond(HttpStatusCode.NotFound) }
    }
}

@JsonClass(generateAdapter = true)
class Status(var version: String? = null, var uploadCount: Int = 0, var failureCount: Int = 0) {
    init {
        version = try {
            Properties().also {
                it.load(this.javaClass.getResourceAsStream("/version.properties"))
            }.getProperty("version")
        } catch (e: Exception) {
            null
        }
    }
}
val currentStatus = Status()

fun main() {
    println(currentStatus.version)
    logger.info(configuration.toJson.toText())

    val uri = URI(configuration[TopLevel.http])
    assert(uri.scheme == "http")

    embeddedServer(Netty, host=uri.host, port=uri.port, module=Application::intellijlogger).start(wait = true)
}

@Suppress("unused")
fun assert(block: () -> String): Nothing { throw AssertionError(block()) }
@Suppress("unused")
fun check(block: () -> String): Nothing { throw IllegalStateException(block()) }
@Suppress("unused")
fun require(block: () -> String): Nothing { throw IllegalArgumentException(block()) }

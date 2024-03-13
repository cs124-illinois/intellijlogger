package edu.illinois.cs.cs125.intellijlogger.server

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import mu.KotlinLogging
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonString
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.net.URI
import java.time.Instant
import java.util.Properties
import java.util.zip.GZIPInputStream

@Suppress("UNUSED")
private val logger = KotlinLogging.logger {}

const val NAME = "intellijlogger"
const val DEFAULT_HTTP = "http://0.0.0.0:8888"
val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs125.intellijlogger.server.version"))
}.getProperty("version")

object TopLevel : ConfigSpec("") {
    val http by optional(DEFAULT_HTTP)
    val semester by optional<String?>(null)
    val mongodb by required<String>()
    val mongoCollection by optional(NAME)
}

val configuration = Config {
    addSpec(TopLevel)
}.from.env()

val mongoCollection: MongoCollection<BsonDocument> = configuration[TopLevel.mongodb].run {
    val uri = MongoClientURI(this)
    val database = uri.database ?: error("MONGO must specify database to use")
    val collection = configuration[TopLevel.mongoCollection]
    MongoClient(uri).getDatabase(database).getCollection(collection, BsonDocument::class.java)
}

@Suppress("unused")
data class Status(
    var name: String = NAME,
    var version: String = VERSION,
    var upSince: Instant = Instant.now(),
    var statusCount: Int = 0,
    var uploadCount: Int = 0,
    var receivedCount: Int = 0,
    var compressedCount: Int = 0,
    var failureCount: Int = 0,
    var lastUpload: Instant? = null,
)

val currentStatus = Status()

val gson: Gson = GsonBuilder()
    .setPrettyPrinting()
    .registerTypeAdapter(
        Instant::class.java,
        object : JsonSerializer<Instant> {
            override fun serialize(
                instant: Instant?,
                typeOfSrc: Type?,
                context: JsonSerializationContext?,
            ): JsonElement {
                return JsonPrimitive(instant.toString())
            }
        },
    ).create()

fun ByteArray.decompress() = BufferedReader(InputStreamReader(GZIPInputStream(ByteArrayInputStream(this)), "UTF-8"))
    .use(BufferedReader::readText)

@Suppress("LongMethod")
fun Application.intellijlogger() {
    install(ForwardedHeaders)
    install(CORS) {
        anyHost()
        allowNonSimpleContentTypes = true
    }
    routing {
        get("/") {
            call.respond(gson.toJson(currentStatus))
            currentStatus.statusCount++
        }
        get("/version") {
            call.respond(VERSION)
        }
        post("/") {
            @Suppress("TooGenericExceptionCaught")
            val upload = try {
                call.receive<ByteArray>().let { bytes ->
                    when (call.request.headers["content-encoding"].equals("gzip", true)) {
                        true -> bytes.decompress().also {
                            currentStatus.compressedCount++
                        }

                        false -> String(bytes)
                    }
                }.let { string ->
                    gson.fromJson<JsonObject>(string, JsonObject::class.java)
                }
            } catch (e: Exception) {
                logger.warn { "couldn't deserialize counters: $e" }
                call.respond(HttpStatusCode.BadRequest)
                currentStatus.failureCount++
                return@post
            }

            @Suppress("TooGenericExceptionCaught")
            try {
                val receivedTime = BsonDateTime(Instant.now().toEpochMilli())
                val receivedIP = BsonString(call.request.origin.remoteHost)
                val receivedSemester = BsonString(configuration[TopLevel.semester])

                val counters = if (upload.has("counters")) {
                    upload.getAsJsonArray("counters").toList()
                } else {
                    listOf(upload)
                }
                currentStatus.receivedCount++
                counters.map { counter ->
                    BsonDocument.parse(gson.toJson(counter))
                        .append("receivedVersion", BsonString(VERSION))
                        .append("receivedTime", receivedTime)
                        .append("receivedIP", receivedIP)
                        .append("receivedSemester", receivedSemester)
                }.let { receivedCounters ->
                    mongoCollection.insertMany(receivedCounters)
                    currentStatus.uploadCount += receivedCounters.size
                    currentStatus.lastUpload = Instant.now()
                }
                logger.debug {
                    "${counters.size} counters uploaded " +
                        "(${counters.first().asJsonObject.getAsJsonPrimitive("index").asLong}" +
                        "..${counters.last().asJsonObject.getAsJsonPrimitive("index").asLong})"
                }

                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                logger.warn { "couldn't save upload: $e" }
                call.respond(HttpStatusCode.InternalServerError)
                currentStatus.failureCount++
                return@post
            }
        }
    }
    intercept(ApplicationCallPipeline.Fallback) {
        if (call.response.status() == null) {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

fun main() {
    val uri = URI(configuration[TopLevel.http])
    check(uri.scheme == "http")
    embeddedServer(Netty, host = uri.host, port = uri.port, module = Application::intellijlogger).start(wait = true)
}

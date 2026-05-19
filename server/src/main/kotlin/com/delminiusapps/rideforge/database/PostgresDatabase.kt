package com.delminiusapps.rideforge.database

import com.delminiusapps.rideforge.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.sql.Connection

class PostgresDatabase private constructor(
    private val dataSource: HikariDataSource,
) : AutoCloseable {

    suspend fun <T> query(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use(block)
    }

    suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val originalAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (throwable: Throwable) {
                connection.rollback()
                throw throwable
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }
    }

    fun migrate() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    override fun close() {
        dataSource.close()
    }

    companion object {
        fun create(config: AppConfig): PostgresDatabase {
            val settings = config.postgresConnectionSettings()
            val hikari = HikariConfig().apply {
                jdbcUrl = settings.jdbcUrl
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = config.databaseMaxPoolSize
                poolName = "RideForgePostgres"
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                validate()
            }
            (config.databaseUser ?: settings.user)?.let { hikari.username = it }
            (config.databasePassword ?: settings.password)?.let { hikari.password = it }
            return PostgresDatabase(HikariDataSource(hikari))
        }
    }
}

private data class PostgresConnectionSettings(
    val jdbcUrl: String,
    val user: String?,
    val password: String?,
)

private fun AppConfig.postgresConnectionSettings(): PostgresConnectionSettings {
    val rawUrl = databaseUrl.trim()
    if (rawUrl.startsWith("jdbc:postgresql://")) {
        return PostgresConnectionSettings(rawUrl, null, null)
    }

    val normalized = when {
        rawUrl.startsWith("postgresql://") -> rawUrl
        rawUrl.startsWith("postgres://") -> rawUrl.replaceFirst("postgres://", "postgresql://")
        else -> throw IllegalArgumentException(
            "DATABASE_URL must use jdbc:postgresql://, postgresql://, or postgres://"
        )
    }

    val uri = URI(normalized)
    val host = uri.host ?: throw IllegalArgumentException("DATABASE_URL must include a host")
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = uri.rawPath.takeIf { !it.isNullOrBlank() } ?: "/postgres"
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    val userInfo = uri.rawUserInfo?.split(":", limit = 2).orEmpty()

    return PostgresConnectionSettings(
        jdbcUrl = "jdbc:postgresql://$host$port$path$query",
        user = userInfo.getOrNull(0)?.decodeUrlComponent(),
        password = userInfo.getOrNull(1)?.decodeUrlComponent(),
    )
}

private fun String.decodeUrlComponent(): String =
    URLDecoder.decode(this, StandardCharsets.UTF_8)

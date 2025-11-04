package org.scastie.storage.postgres

import scala.concurrent.ExecutionContext

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource
import scalasql.core.DbClient
import scalasql.core.DialectConfig
import scalasql.dialects.PostgresDialect

class PostgresContainer(
    defaultConfig: Boolean = false,
    useConnectionPool: Boolean = true,
    runMigrations: Boolean = true,
    maxPoolSize: Int = 20
)(
    implicit val ec: ExecutionContext
) extends PostgresSnippetsContainer {

  private val (jdbcUrl, user, password) = {
    if (defaultConfig) {
      (
        "jdbc:postgresql://localhost:5432/scastie",
        "postgres",
        ""
      )
    } else {
      val config   = ConfigFactory.load().getConfig("org.scastie.postgres")
      val host     = config.getString("host")
      val port     = config.getInt("port")
      val database = config.getString("database")
      val user     = config.getString("user")
      val password = config.getString("password")

      (
        s"jdbc:postgresql://$host:$port/$database",
        user,
        password
      )
    }
  }

  if (runMigrations) migrate()

  private val dataSource = useConnectionPool match {
    case true =>
      val ds = new HikariDataSource()
      ds.setJdbcUrl(jdbcUrl)
      ds.setUsername(user)
      ds.setPassword(password)
      ds.setPoolName("PostgresPool")
      ds.setMaximumPoolSize(maxPoolSize)
      ds
    case false =>
      val ds = new PGSimpleDataSource()
      ds.setUrl(jdbcUrl)
      ds.setUser(user)
      ds.setPassword(password)
      ds
  }

  private implicit val dialect: DialectConfig = PostgresDialect

  lazy val db: DbClient.DataSource = new DbClient.DataSource(
    dataSource,
    config = new scalasql.Config {
      override def nameMapper(v: String) = {
        /* Removes the "Postgres" prefix and converts camelCase to snake_case */
        /* For example: "PostgresUserOutput" -> "user_output" */
        val noPrefix = if (v.startsWith("Postgres")) v.stripPrefix("Postgres") else v
        noPrefix
          .replaceAll("([A-Z])", "_$1")
          .toLowerCase
          .stripPrefix("_")
      }
      override def logSql(sql: String, file: String, line: Int) = println(s"$file:$line $sql")
    }
  )

  private def migrate(): Unit = {
    val flyway = Flyway
      .configure()
      .dataSource(jdbcUrl, user, password)
      .load()

    flyway.migrate()
  }

}

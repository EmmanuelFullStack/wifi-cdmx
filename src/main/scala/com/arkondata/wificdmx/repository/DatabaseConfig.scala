package com.arkondata.wificdmx.repository

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

final class DatabaseConfig(config: Config) extends LazyLogging {

  private val dbCfg   = config.getConfig("wifi-cdmx.database")
  private val poolCfg = dbCfg.getConfig("pool")

  val db: PostgresProfile.backend.Database = {
    val props = new java.util.Properties()
    props.setProperty("maximumPoolSize", poolCfg.getInt("max-connections").toString)
    props.setProperty("minimumIdle", poolCfg.getInt("min-idle").toString)
    props.setProperty("connectionTimeout", poolCfg.getLong("connection-timeout").toString)
    props.setProperty("idleTimeout", poolCfg.getLong("idle-timeout").toString)

    val url  = dbCfg.getString("url")
    val user = dbCfg.getString("user")

    val maskedUrl = url.replaceAll(":[^:@/]+@", ":***@")
    logger.info(s"Connecting to database: $maskedUrl as user=$user")
    logger.info(
      s"Pool: max=${poolCfg.getInt("max-connections")} min-idle=${poolCfg.getInt("min-idle")}"
    )

    Database.forURL(
      url = url,
      user = user,
      password = dbCfg.getString("password"),
      driver = dbCfg.getString("driver"),
      prop = props
    )
  }

  def close(): Unit = {
    logger.info("Closing database connection pool")
    db.close()
  }
}

package multithreading

import com.redis.RedisClient
import play.api.Logger
import play.api.libs.json.JsValue


/**
  *
  * Handles Error Conditions when the Bucket Worker fails and the request processing is to be delayed
  *
  **/

class BucketErrorHandler(period: Long, jsonBody: JsValue) extends Thread {

  override def run(): Unit = {
    val redisClient = new RedisClient("localhost", 6379)
    // Add request at the end and update its next time period
    reportRequestProcessingErrorInLog(redisClient, jsonBody)
  }

  private def reportRequestProcessingErrorInLog(redisClient: RedisClient, jsonBody: JsValue): Unit = {
    // Recovering from errors by delaying request processing to next timestep
    Logger.logger.debug("Recovering from error, action delayed for Request ID : " + getRequestId(jsonBody))
  }

  private def getRequestId(jsonBody: JsValue): Long = {
    (jsonBody \ "reqId").get.toString.toLong
  }
}

package multithreading

import com.redis.RedisClient
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}

class BucketErrorHandler(period: Long, jsonBody: JsValue) extends Thread {

  override def run(): Unit = {
    val redisClient = new RedisClient("localhost", 6379)
    val myQueue = generateWorkerQId
    // Add request at the end and update its next time period
    delayFailedRequestProcessing(myQueue, redisClient, jsonBody)
  }

  private def generateWorkerQId: String = {
    "worker_queue_" + period.toString
  }

  private def delayFailedRequestProcessing(myQueue: String, redisClient: RedisClient, jsonBody: JsValue): Unit = {
    // Recovering from errors by delaying request processing to next timestep
    Logger.logger.debug("Recovering from error, action delayed")
    redisClient.lpush(myQueue, updateTimePeriod(redisClient, jsonBody))
  }

  private def updateTimePeriod(redisClient: RedisClient, jsonBody: JsValue): JsObject = {
    jsonBody.as[JsObject] ++ Json.obj("time_period" -> (getTimestamp(redisClient) + period * 60))
  }

  private def getTimestamp(redisClient: RedisClient) = {
    redisClient.time.get.head.get.toLong
  }

}

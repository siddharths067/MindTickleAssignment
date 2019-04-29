package multithreading

import com.redis.RedisClient
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}


/*
*
* BucketPublisher is responsible for publishing results to respective channels
*
* */


class BucketPublisher(period: Long, jsBody: JsValue, webhookResult: JsValue) extends Thread {

  override def run(): Unit = {


    val myQueue = generateWorkerQId
    val redisClient = new RedisClient("localhost", 6379)

    val jsonBody = jsBody
    val triggeredResult = webhookResult
    val prevResult = redisClient.get(getPrevResultKeyId(jsonBody))

    publishPreviousResult(redisClient, jsonBody, prevResult)

    publishCurrentResult(redisClient, jsonBody, triggeredResult)

    // Store Previous result
    updatePreviousResult(redisClient, jsonBody, triggeredResult)

    // Update time period and Add to queue
    redisClient.lpush(myQueue, updateTimePeriod(redisClient, jsonBody))

  }

  private def getTimestamp(redisClient: RedisClient) = {
    // get current timestamp
    redisClient.time.get.head.get.toLong
  }

  private def updateTimePeriod(redisClient: RedisClient, jsonBody: JsValue): JsObject = {
    // Update Timestamp
    jsonBody.as[JsObject] ++ Json.obj("time_period" -> (getTimestamp(redisClient) + period * 60))
  }

  private def generateWorkerQId: String = {
    "worker_queue_" + period.toString
  }

  private def getChannelKey(jsonBody: JsValue) = {
    "Channel" + "prevReqRes" + getRequestId(jsonBody)
  }


  private def updatePreviousResult(redisClient: RedisClient, jsonBody: JsValue, resultBody: JsValue): Boolean = {
    // Updates previous result
    redisClient.set(getPrevResultKeyId(jsonBody), resultBody.toString)
  }

  private def publishCurrentResult(redisClient: RedisClient, jsonBody: JsValue, resultBody: JsValue): Option[Long] = {
    // Output and Publish Request Result
    Logger.logger.debug("The Result is " + resultBody.toString)
    redisClient.publish(getChannelKey(jsonBody), "The Result is " + resultBody.toString)
  }

  private def publishPreviousResult(redisClient: RedisClient, jsonBody: JsValue, prevResult: Option[String]): Any = {
    // If Previous Result is defined output and publish it
    if (prevResult.isDefined)
      Logger.logger.debug("Previous Valid Result was " + prevResult.get.toString)
    if (prevResult.isDefined)
      redisClient.publish(getChannelKey(jsonBody), "Previoud Valid Result was " + prevResult.get.toString)
  }

  private def getPrevResultKeyId(jsonBody: JsValue) = {
    "prevReqRes" + getRequestId(jsonBody)
  }

  private def getRequestId(jsonBody: JsValue): Long = {
    (jsonBody \ "reqId").get.toString.toLong
  }

}

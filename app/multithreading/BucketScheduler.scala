package multithreading

import com.redis.RedisClient
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}

class BucketScheduler(period: Long) extends Thread {

  override def run(): Unit = {
    Logger.logger.debug("Scheduler Launcher for Bucket " + period)
    val myQueue = generateWorkerQId
    val redisClient = new RedisClient("localhost", 6379)
    while (true) {
      /*
      No Need for Waiting for all Publishers, Publishing is an independent operation.
      The Queue for an instantiated Bucket Handler will always be non empty since we immediately
      Add the updated element at the end of the loop
      */

      val jsonBody = Json.parse(getQHead(myQueue, redisClient))
      Logger.logger.debug("Json Body fetched from queue " + jsonBody.toString())

      val requestStamp = getJsonTimePeriod(jsonBody)

      // If no backlog and this request is early sleep out the time
      waitTillNextRequestTime(redisClient, requestStamp)
      new Worker(period, jsonBody).start()

      // Update Time Period and Proceed
      redisClient.lpush(myQueue, updateTimePeriod(redisClient, jsonBody))

    }
  }

  private def waitTillNextRequestTime(redisClient: RedisClient, requestStamp: Long): Unit = {
    // Added one second padding since OS scheduler work independently, hopefully that would cover it
    val waitTimeInMilliseconds = ((requestStamp - getTimestamp(redisClient)) - 1) * 1000
    this.synchronized {
      if (waitTimeInMilliseconds > 0)
        this.wait(waitTimeInMilliseconds)
      this.notifyAll()
    }
  }

  private def getTimestamp(redisClient: RedisClient) = {
    // get current timestamp
    redisClient.time.get.head.get.toLong
  }

  private def getJsonTimePeriod(jsonBody: JsValue): Long = {
    (jsonBody \ "time_period").get.toString().toLong
  }

  private def getQHead(myQueue: String, redisClient: RedisClient): String = {
    // Removed when Bucket Scheduler was added
    // If Number of threads is more than minimum needed to avoid delay, kill threads
    val headElement = redisClient.rpop(myQueue)
    /*if (headElement.isEmpty)
      this.join()*/
    headElement.get
  }

  private def generateWorkerQId: String = {
    "worker_queue_" + period.toString
  }


  private def updateTimePeriod(redisClient: RedisClient, jsonBody: JsValue): JsObject = {
    // Update Timestamp
    jsonBody.as[JsObject] ++ Json.obj("time_period" -> (getTimestamp(redisClient) + period * 60))
  }

}

package multithreading

import java.io.IOException
import java.net.{SocketTimeoutException, URI}
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.dispatch.ExecutionContexts
import com.redis.RedisClient
import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.parsing.json.JSONObject
import scala.util.{Failure, Success, Try}


class Worker(period: Long) extends Thread {
  /*

  This method would although allow property validation, it will fail if user forgets the inheritance
  structure and also add dot notation for specification, removed for the sake of ease of use

    def getNested(obj: JsValue, properties: List[String]):JsValue =
      if(properties.size == 0)
        obj
      else getNested((obj \ properties.head).get, properties.tail)
    */

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def get(url: String,
          connectTimeout: Int = 5000,
          readTimeout: Int = 5000,
          requestMethod: String = "GET") = {
    import java.net.{URL, HttpURLConnection}
    val connection = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod(requestMethod)
    val inputStream = connection.getInputStream
    val content = Source.fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close
    content
  }

  override def run(): Unit = {
    Logger.debug("Thread Launched " + this.getId)
    val myQueue = "worker_queue_" + period.toString
    val redisClient = new RedisClient("localhost", 6379)
    while (true) {
      val timestamp = getTimestamp(redisClient)
      val jsonBody = Json.parse(getQHead(myQueue, redisClient))
      Logger.debug("Json Body fetched from queue " + jsonBody.toString())
      requestNewWorker(timestamp, jsonBody)
      val requestStamp = getJsonTimePeriod(jsonBody)
      Logger.debug("Current request stamp " + requestStamp)
      waitTillNextRequestTime(redisClient, requestStamp)
      val observedUrl = getJsonObservedUrl(jsonBody)
      val resultBody = try {
        Json.parse(get(cleanJsonQuotes(observedUrl)))
      }
      catch {
        case ioExcept: IOException => {
          ioExcept.printStackTrace();
          JsNull
        }
        case socketTimeout: SocketTimeoutException => {
          socketTimeout.printStackTrace();
          JsNull
        }

      }
      if (resultBody == JsNull) {
        Logger.debug("Recovering from error, action delayed")
        redisClient.lpush(myQueue, updateTimePeriod(redisClient, jsonBody))
      } else {
        Logger.debug("Result Fetched " + resultBody)
        val propertyList = getJsonCriteriaList(jsonBody)
        val valList = getJsonPropertyCriteraValues(jsonBody).map(x => cleanJsonQuotes(x.toString()))
        val tempres = (getPropertyRecursively(resultBody, propertyList.head)).head.toString()
        val resultList = propertyList.map(x => getFirstElementFromPropertyMatch(resultBody, x) toString)
        if (resultList == valList)
          Logger.debug("The Result is " + resultBody.toString())
        val newSize = redisClient.lpush(myQueue, updateTimePeriod(redisClient, jsonBody))
      }
    }
    Logger.debug("Loop Exited")
  }

  private def getFirstElementFromPropertyMatch(resultBody: JsValue, x: JsValue) = {
    (getPropertyRecursively(resultBody, x)).toList.head
  }

  private def updateTimePeriod(redisClient: RedisClient, jsonBody: JsValue): JsObject = {
    jsonBody.as[JsObject] ++ Json.obj(("time_period" -> (getTimestamp(redisClient) + period * 60)))
  }

  private def getPropertyRecursively(resultBody: JsValue, x: JsValue): Seq[JsValue] = {
    resultBody \\ cleanJsonQuotes(x.toString())
  }

  private def getJsonPropertyCriteraValues(jsonBody: JsValue) = {
    (jsonBody \ "cr_values").get.as[List[JsValue]]
  }

  private def getJsonCriteriaList(jsonBody: JsValue) = {
    (jsonBody \ "cr").get.as[List[JsValue]]
  }

  private def cleanJsonQuotes(observedUrl: String): String = {
    observedUrl.substring(1, observedUrl.size - 1)
  }

  private def getJsonObservedUrl(jsonBody: JsValue): String = {
    (jsonBody \ "observed_url").get.toString()
  }

  private def waitTillNextRequestTime(redisClient: RedisClient, requestStamp: Long) = {
    // Added one second padding since OS scheduler work independently, hopefully that would cover it
    val waitTimeInMilliseconds = ((requestStamp - getTimestamp(redisClient)) - 1) * 1000
    this.synchronized {
      if (waitTimeInMilliseconds > 0)
        this.wait(waitTimeInMilliseconds)
      this.notifyAll()
    }
  }

  private def requestNewWorker(timestamp: Long, jsonBody: JsValue): Unit = {
    if (getJsonTimePeriod(jsonBody) < timestamp) {
      Logger.debug("Requesting a new worker ")
      new Worker(period).start()
    }
  }

  private def getJsonTimePeriod(jsonBody: JsValue): Long = {
    (jsonBody \ "time_period").get.toString().toLong
  }

  private def getQHead(myQueue: String, redisClient: RedisClient): String = {
    val headElement = redisClient.rpop(myQueue)
    if (headElement.isEmpty)
      this.join()
    headElement.get
  }

  private def getTimestamp(redisClient: RedisClient) = {
    redisClient.time.get.head.get.toLong
  }
}
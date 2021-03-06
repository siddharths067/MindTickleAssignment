package multithreading

import java.io.IOException
import java.net.SocketTimeoutException

import play.api.Logger
import play.api.libs.json.{JsNull, JsValue, Json}

import scala.io.Source


class Worker(period: Long, requestJsonBody: JsValue) extends Thread {
  /*

  This method would although allow property validation, it will fail if user forgets the inheritance
  structure and also add dot notation for specification, removed for the sake of ease of use

    def getNested(obj: JsValue, properties: List[String]):JsValue =
      if(properties.size == 0)
        obj
      else getNested((obj \ properties.head).get, properties.tail)
    */

  override def run(): Unit = {

    Logger.logger.debug("Worker Launched " + this.getId)

    val jsonBody = requestJsonBody
      // Get data from observation url
      val observedUrl = getJsonObservedUrl(jsonBody)
      val resultBody = fetchRemoteAPIResult(observedUrl)

      // Get Data from Webhook URL
      val webhookUrl = fetchJsonWebhookUrl(jsonBody)
      val triggeredResult = fetchRemoteAPIResult(webhookUrl)

      if (resultBody == JsNull || triggeredResult == JsNull) {
        Logger.logger.debug("Error handler launched ")
        new BucketErrorHandler(period, jsonBody).start()

      } else {
        Logger.logger.debug("Result Fetched " + triggeredResult)

        // Get Property Lists and their target list
        val propertyList = getJsonCriteriaList(jsonBody)
        val valList = getJsonPropertyCriteriaValues(jsonBody).map(x => cleanJsonQuotes(x.toString()))

        // Fetch body parameters from observation URL
        val resultList = fetchRemoteResultProperties(resultBody, propertyList)
        if (resultList == valList) {
          new BucketPublisher(period, jsonBody, triggeredResult).start()
        }

      }
  }


  private def fetchJsonWebhookUrl(jsonBody: JsValue): String = {
    (jsonBody \ "webhook").get.toString()
  }

  private def fetchRemoteResultProperties(resultBody: JsValue, propertyList: List[JsValue]): List[String] = {
    // Finds criteria properties in result
    propertyList.map(x => getFirstElementFromPropertyMatch(resultBody, x) toString)
  }


  private def fetchRemoteAPIResult(observedUrl: String): JsValue = {
    // Gets remote API result
    try {
      Json.parse(get(cleanJsonQuotes(observedUrl)))
    }
    catch {

      // On Connectivity Errors Backoff the request and delay its processing

      case ioExcept: IOException =>
        ioExcept.printStackTrace()
        JsNull

      case socketTimeout: SocketTimeoutException =>
        socketTimeout.printStackTrace()
        JsNull

      case unusualException: Throwable =>
        unusualException.printStackTrace()
        JsNull

    }
  }


  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def get(url: String,
          connectTimeout: Int = 5000,
          readTimeout: Int = 5000,
          requestMethod: String = "GET"): String = {
    // Actual Implementation of remote URL fetch
    import java.net.{HttpURLConnection, URL}
    val connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod(requestMethod)
    val inputStream = connection.getInputStream
    val content = Source.fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close()
    content
  }

  private def getFirstElementFromPropertyMatch(resultBody: JsValue, x: JsValue) = {
    getPropertyRecursively(resultBody, x).toList.head
  }



  private def getPropertyRecursively(resultBody: JsValue, x: JsValue): Seq[JsValue] = {
    resultBody \\ cleanJsonQuotes(x.toString())
  }

  private def getJsonPropertyCriteriaValues(jsonBody: JsValue) = {
    (jsonBody \ "cr_values").get.as[List[JsValue]]
  }

  private def getJsonCriteriaList(jsonBody: JsValue) = {
    (jsonBody \ "cr").get.as[List[JsValue]]
  }

  private def cleanJsonQuotes(observedUrl: String): String = {
    if (observedUrl.charAt(0) == '\"' && observedUrl.charAt(observedUrl.length - 1) == '\"')
      observedUrl.substring(1, observedUrl.length - 1)
    else observedUrl
  }

  private def getJsonObservedUrl(jsonBody: JsValue): String = {
    (jsonBody \ "observed_url").get.toString()
  }

}
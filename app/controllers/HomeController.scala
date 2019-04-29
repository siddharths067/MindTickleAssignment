package controllers

import akka.actor.ActorSystem
import com.redis._
import javax.inject._
import multithreading.BucketScheduler
import play.api.libs.json._
import play.api.mvc._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(system: ActorSystem, cc: ControllerComponents) extends AbstractController(cc) {
  implicit val akkaSystem: ActorSystem = system

  /**
    * Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def index() = Action {
    val redis = new RedisClient("localhost", 6379)
    val futurePong = redis.ping
    println(futurePong.get + "Received")
    Ok("Result Received")
  }


  /*
  *
  * Criteria Submission Logic
  *
  * */
  def submitCriteria(): Action[JsValue] = Action(parse.json) {
    request => {
      val redisClient = new RedisClient("localhost", 6379)
      val futureTimeStamp = redisClient.time.get.head.get

      // Update time period to the next observation time
      val reqId = redisClient.incr("reqCntr")
      val newJsonBody = generateWorkerQElement(request, futureTimeStamp, reqId)
      pushIntoQueue(redisClient, newJsonBody, (request.body \ "time_period").get.toString())
      Ok("Request Received Successfully")
    }
  }

  private def generateWorkerQElement(request: Request[JsValue], futureTimeStamp: String, reqId: Option[Long]): JsObject = {
    (request.body.as[JsObject] ++
      Json.obj("time_period" -> initializeNextHitTimestamp(request, futureTimeStamp))) ++
      Json.obj("reqId" -> reqId.get.toString.toLong)
  }

  private def initializeNextHitTimestamp(request: Request[JsValue], futureTimeStamp: String): Json.JsValueWrapper = {
    ((request.body \ "time_period").get.toString().toLong * 60 + futureTimeStamp.toLong)
  }

  def pushIntoQueue(redisClient: RedisClient, jsonBody: JsValue, period: String): Unit = {
    // Segregate Requests into bucket
    redisClient.lpush("worker_queue_" + period, jsonBody.toString())

    val incr = redisClient.incr("requestCountForQ" + period.toString)
    // Check if the request was the first in the bucket
    if (incr.get == 1)
      new BucketScheduler(period.toLong).start()
  }
}

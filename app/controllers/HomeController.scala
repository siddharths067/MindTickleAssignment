package controllers

import akka.actor.ActorSystem
import com.redis._
import javax.inject._
import multithreading.Worker
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
  def index() = Action { request => {
    val redis = new RedisClient("localhost", 6379)
    val futurePong = redis.ping
    println(futurePong.get + "Received")
    Ok("Result Received")
  }
  }

  def submitCriteria(): Action[JsValue] = Action(parse.json) {
    request => {
      val redisClient = new RedisClient("localhost", 6379)
      val futureTimeStamp = redisClient.time.get.head.get
      val jsonBody = request.body
      // Update time period to the next observation time
      val newJsonBody = request.body.as[JsObject] ++ Json.obj("time_period" -> ((request.body \ "time_period").get.toString().toLong * 60 + futureTimeStamp.toLong))
      pushIntoQueue(redisClient, newJsonBody, (request.body \ "time_period").get.toString())
      Ok("Request Received Successfully")
    }
  }

  def pushIntoQueue(redisClient: RedisClient, jsonBody: JsValue, period: String): Unit = {
    // Segregate Requests into bucket
    val queueNewSize = redisClient.lpush("worker_queue_" + period, jsonBody.toString())

    val incr = redisClient.incr("requestCountForQ" + period.toString)
    // Check if the request was the first in the bucket
    if (incr.get == 1)
      new Worker(period.toLong).start()
  }
}

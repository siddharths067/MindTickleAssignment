package controllers

import akka.actor.{ActorSystem, Status}
import javax.inject._
import play.api._
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import com.redis._
import multithreading.Worker

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(system: ActorSystem, cc: ControllerComponents) extends AbstractController(cc) {
  implicit val akkaSystem = system

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

  def submitCriteria() = Action(parse.json) {
    request => {
      val redisClient = new RedisClient("localhost", 6379)
      /*
      println(request.body.toString())
      (request.body \ "observed_url").asOpt[String].map {
        value => Ok("Criteria Submitted for " + value)
      }.getOrElse{
        BadRequest("Missing Parameter")
      } */

      val futureTimeStamp = redisClient.time.get.head.get
      val jsonBody = request.body
      val newJsonBody = request.body.as[JsObject] ++ Json.obj("time_period" -> ((request.body \ "time_period").get.toString().toLong * 60 + futureTimeStamp.toLong))

      pushIntoQueue(redisClient, newJsonBody, (request.body \ "time_period").get.toString())
      Ok("Request Received Successfully")
    }
  }

  def pushIntoQueue(redisClient: RedisClient, jsonBody: JsValue, period: String) = {
    val queueNewSize = redisClient.lpush("worker_queue_" + period, jsonBody.toString())
    Logger.debug("The New size of queue is " + queueNewSize.get)
    val incr = redisClient.incr("requestCountForQ"+period.toString)
    if (incr.get == 1)
      new Worker(period.toLong).start()
  }
}

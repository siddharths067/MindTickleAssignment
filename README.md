# MindTickleAssignment
An Initial Build of Zapier Like Integration Service

#### Components and Projects Used

* Play Framework - Scala 
* Scala Build Tool - SBT
* Redis (hosted at default port)
* Postman for API Testing

### Overview of Directory

* app/controllers/HomeController.scala has the controller logic
https://github.com/siddharths067/MindTickleAssignment/blob/master/app/controllers/HomeController.scala

* app/multithreading/Worker.scala has the Worker logic
https://github.com/siddharths067/MindTickleAssignment/blob/master/app/multithreading/Worker.scala

### Input Format

```$xslt
{
	"observed_url" : "https://samples.openweathermap.org/data/2.5/weather?q=London,uk&appid=b6907d289e10d714a6e88b30761fae22",
	"webhook" : "http://dummy.restapiexample.com/api/v1/employees",
	"cr": ["temp_max", "temp_min"],
	"cr_values":["281.15", "279.15"],
	"time_period" : 1,
	"operation" : "pull"
}
```

  * observed_url stores the url to observe (REST API - assumed)
  * webhook stores the url to trigger 
  * cr stores the criterias to be checked
  * cr values store the respective criteria values
  * time_period stores the observation interval in minutes
  
### Logic of the System

The Logic of the system is fairly straightforward.

* The input is taken from /submit route
* All the requests are separated into different buckets on the basis of time period.
i.e all requests that are of time period 2 get stored in a single worker_queue_2
* An additional counter of total bucket count is maintained centrally
* If the corresponding request is the first request in the bucket a new thread is launched
* New threads are requested every time the request being processed is late, this makes sure the 
system always has minimum number of threads, i.e **when no further requests are incoming we only 
have minimum number of active threads so that the time needed to process them plus the time needed
to fetch them from the remote server never delays the next request**

* To maintain this policy we also kill threads if they are more than needed to avoid delay
This ensures that the threads are always locked on a url to fire at an instant on appropriate times

* **The System can be scaled horizontally by additionally partitioning time periods
in separate redis instances.**

* Right now the project outputs the result of the fetched url in a Log and a Redis Channel,
Although Kafka would be a better for Log Aggregation use case

### Execution

* flush all keys in your redis instance using command
   ```flushall```

    #### Open SBT in Project Directory


     $sbt
     
     $run

* Use Postman to test request at localhost:9000/submit , use example request from above
 * Postman collection https://www.getpostman.com/collections/9ed99c4008ca9f6f633c
 
 
   #### OUTPUT
   * Method 1: View the Debug Log in the SBT shell
   * Method 2: Subscribe to the channel ChannelprevReqResN in redis where N is your request ID
   N=1 if the request was the first one to be fired. We generate the requestID in the controller
   itself so it can always be returned instantaneously to the user through UI
   
### Scope for Improvement

  * The process I use for requesting new threads can in cases of a very very large request influx
  at a single moment can cause rapid expansion till one to one mapping is reached. By this time we
  could theoretically lead to some threads who when detect an empty queue now would start to kill themselves
  till we end up with a one to one mapping.
  **We don't need one to one mapping, so the number of threads in the system will further continue to shrink till an 
  equilibrium is reached, influx in such cases could lead to an unstable system that could oscillate
  for a time by creating and killing threads until it decays to a minima**
  
  * Apache Kafka would be a better choice for log aggregation in the PUB/SUB
  
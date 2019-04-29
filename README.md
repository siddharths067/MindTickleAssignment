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

* app/multithreading/BucketScheduler.scala has the scheduler for each bucket

* app/multithreading/BucketPublisher.scala has the publisher worker logic

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


* If the request was the first in the bucket a corresponding BucketScheduler is launched,
All throughout the workers we maintain the monotonicity of the timeperiods of each request in the 
Worker Queue, by always taking the current timestamp and incrementing at the end. 
Each Bucket scheduler launches a worker when needed, the worker then does the API calls and checks
the validity of the response according to the user condition provided. If and only if an error occurs
does the bucket scheduler delays the request processing. If successfull without any error the bucket
scheduler then passes on the job to the BucketPublisher which then publishes the result to the channel

* **The System can be scaled horizontally by additionally partitioning time periods
in separate redis instances.**

* Right now the project outputs the result of the fetched url in a Log and a Redis Subscriber 
Channel, Although Kafka would be a better for Log Aggregation use case

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
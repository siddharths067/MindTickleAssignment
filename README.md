# MindTickleAssignment
An Initial Build of Zapier Like Integration Service using Redis for Queuing

#### Components Used

* Play Framework - Scala 
* Redis (hosted at default port)
* Postman for API Testing

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
* New threads are requests every time the request being processed is late, this makes sure the 
system always has minimum number of threads, i.e **when no further requests are incoming all we only 
have minimum number of active threads so that the time needed to process them plus the time needed
to fetch them from the remote server never delays the next request**

* To maintain this policy we also kill threads if they are more than needed to avoid delay
This ensures that the threads are always locked on a url to fire at an instant on appropriate times

* The System can be scaled horizontally by additionally partitioning time periods
in separate redis instances.

* Right now the project outputs the result of the fetched url in a Log, although one can always
directly use a publisher/subscriber or log aggregation utility like Kafka

### Execution

* flushall keys in yuor redis instance 

    #### Open SBT in Project Directory


     $sbt
     
     $run

* Use Postman to test request at localhost:9000/submit , use example request from above
 * Postman collection https://www.getpostman.com/collections/9ed99c4008ca9f6f633c
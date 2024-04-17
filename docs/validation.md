# Validation

squbs Validation provides a [pekko HTTP](http://doc.pekko.io/) directive for data validation by using [Accord Validation Library](http://wix.github.io/accord/). Currently this is Scala only feature, Java version will be added in future versions of squbs.
  
## Dependencies

Add the following dependencies to your build.sbt or scala build file:

```scala
"org.squbs" %% "squbs-pattern" % squbsVersion,
"com.wix" %% "accord-core" % "0.7.1"
```  
  
## Usage
  
Given that an implicit `Person` validator is in the scope, `validate` directive can be used as other [pekko HTTP Directives](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/directives/index.html):

  
```scala
import ValidationDirectives._
validate(person) { 
    ...
}
```  

## Example

Here is a sample `Person` class and corresponding validator (please see [Accord Validation Library](http://wix.github.io/accord/) for more validator usage examples).

```scala
case class Person(firstName: String, lastName: String, middleName: Option[String] = None, age: Int)

object SampleValidators {

  import com.wix.accord.dsl._
  implicit val personValidator = com.wix.accord.dsl.validator[ Person ] { p =>
                p.firstName as "First Name" is notEmpty
                p.lastName as "Last Name" is notEmpty
                p.middleName.each is notEmpty // If exists, should not be empty.
                p.age should be >= 0
              }
}
```

Now you can use the `validate` directive as follows: 
 
```scala
def route =
 path("person") {
   post {
     entity(as[Person]) { person =>
       import ValidationDirectives._
       // importing the person validator
       import SampleValidators._
       validate(person) {
           complete {
             person
           }
       }
     }
   }
 }
```
 
If a validation rejection happens, a `400 Bad Request` is returned with the response body containing the comma separated list of field(s) causing validation rejection.  Using the above example, if the request body contains the following:
  
```
{
    "firstName" : "John",
    "lastName" : "",
    "age" : -1
}
```
 
then, the response body would contain:
  
```
Last Name, age 
```
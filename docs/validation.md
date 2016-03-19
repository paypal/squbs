#Validation

squbs Validation provides a [Spray](http://spray.io) directive for data validation by using [Accord Validation Library](http://wix.github.io/accord/). Since Spray directives are currently Scala only, there is no equivalent in Java code until future versions of squbs.
  
##Dependencies

Add the following dependency to your build.sbt or scala build file:

```
"org.squbs" %% "squbs-pattern" % squbsVersion
```  
  
##Usage
  
Given that an implicit `Person` validator is in the scope, `validate` directive can be used as other [Spray Directives](http://spray.io/documentation/1.2.3/spray-routing/key-concepts/directives/):     
  
```scala
import ValidationDirectives._
validate(person) { 
    ...
}
```  

##Example

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
           validate(person) {
             respondWithMediaType(`application/json`) {
               complete {
                 person
               }
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
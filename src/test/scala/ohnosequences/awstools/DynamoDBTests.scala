package ohnosequences.awstools.dynamodb
import org.junit.Test
import org.junit.Assert._

import java.io.File
import com.amazonaws.services.dynamodb.datamodeling._
import annotation.Annotation

class DynamoDBTests {

 // @Test
  def policyTests {
    val ddb = DynamoDB.create(new File("gridServer/AwsCredentials.properties"))

//    @DynamoDBTable(tableName = "test2")
//    class Test2(var id: String = "", var name: String = "") {
//
//      def this() = this(id = "", name = "")
//
//      @DynamoDBHashKey
//      @DynamoDBAutoGeneratedKey
//      def getId = id
//      def setId(id: String) {this.id = id}
//      def withId(id: String) = {this.id = id; this}
//
//      @DynamoDBAttribute
//      def getName = name
//      def setName(name: String) {this.name = name}
//      def withName(name: String) = {this.name = name; this}
//    }


    val test = new User()
    test.setId("123")
    test.setName("testName")
    val mapper = new DynamoDBMapper(ddb.ddb)
    mapper.save(test)

    val test2 = mapper.load(classOf[User], "123")

    ddb.shutdown()


    assertEquals("testName", test2.getName)
  }

  @Test
  def mappingTest {

    println("test")
    class AttributeNames(names: List[String]) extends Annotation

    @AttributeNames(names = List("name", "age"))
    case class User(name: String, age: Int)

    println(classOf[User].getDeclaredAnnotations.toList)
    val user = User.apply("test", 34)
   // classOf[User].
  }

}
package ohnosequences.awstools.ec2

import java.io.{IOException, PrintWriter, File}

import ohnosequences.awstools.regions._

import com.amazonaws.auth._
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ec2.{ AmazonEC2, AmazonEC2Client }
import com.amazonaws.services.ec2.model._

import scala.util.Try
import scala.collection.JavaConversions._
import com.amazonaws.AmazonServiceException


case class ScalaEC2Client(val asJava: AmazonEC2) extends AnyVal { ec2 =>


  def keyPairExists(name: String): Try[Boolean] = Try {
    asJava.describeKeyPairs(
      new DescribeKeyPairsRequest().withKeyNames(name)
    ).getKeyPairs.nonEmpty
  }

  def createKeyPair(name: String): Try[KeyPair] = Try {
    asJava.createKeyPair(
      new CreateKeyPairRequest().withKeyName(name)
    ).getKeyPair
  }

  // def deleteSecurityGroup(name: String, attempts: Int = 0): Boolean = {
  //   try {
  //     asJava.deleteSecurityGroup(new DeleteSecurityGroupRequest()
  //       .withGroupName(name)
  //     )
  //     true
  //   } catch {
  //     case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.InUse") => {
  //       if(attempts > 0) {
  //         Thread.sleep(2000)
  //         println("security group: " + name + " in use, waiting...")
  //         deleteSecurityGroup(name, attempts-1)
  //       } else {
  //         false
  //       }
  //     }
  //     case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.NotFound") => true
  //   }
  // }
  //
  // def createSecurityGroup(name: String): Unit = {
  //   try {
  //     asJava.createSecurityGroup(new CreateSecurityGroupRequest()
  //       .withGroupName(name)
  //       .withDescription(name)
  //     )
  //   } catch {
  //     case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.Duplicate") => ()
  //   }
  // }

  // def requestSpotInstances(amount: Int, price: Double, specs: AnyLaunchSpecs, timeout: Int = 36000): List[SpotInstanceRequest] = {
  //   asJava.requestSpotInstances(new RequestSpotInstancesRequest()
  //     .withSpotPrice(price.toString)
  //     .withInstanceCount(amount)
  //     .withLaunchSpecification(specs.toAWS)
  //   ).getSpotInstanceRequests.map{ request =>
  //     SpotInstanceRequest(ec2.asJava, request.getSpotInstanceRequestId)
  //   }.toList
  // }

  def runInstances(amount: Int, launchSpecs: AnyLaunchSpecs): Seq[Instance] = {
    val request = launchSpecs
      .toRunInstancesRequest
      .withMinCount(amount)
      .withMaxCount(amount)
    // TODO: .withPlacement?

    asJava.runInstances(request)
      .getReservation
      .getInstances
      .map { Instance(ec2.asJava, _) }
  }

  def getCurrentSpotPrice(instanceType: AnyInstanceType, productDescription: String = "Linux/UNIX"): Double = {
    asJava.describeSpotPriceHistory(
      new DescribeSpotPriceHistoryRequest()
        .withStartTime(new java.util.Date())
        .withInstanceTypes(instanceType.name)
        .withProductDescriptions(productDescription)
    ).getSpotPriceHistory
     .map{ _.getSpotPrice.toDouble }
     .fold(0D){ math.max(_, _) }
  }


  def listInstancesByFilters(filters: InstanceFilter*): Seq[Instance] = {
    asJava.describeInstances(
      new DescribeInstancesRequest().withFilters(filters.map(_.toEC2Filter))
    ).getReservations.flatMap(_.getInstances).map { Instance(ec2.asJava, _) }
  }


  // def listRequestsByFilters(filters: InstanceFilter*): List[SpotInstanceRequest] = {
  //   asJava.describeSpotInstanceRequests(
  //     new DescribeSpotInstanceRequestsRequest().withFilters(filters.map(_.toEC2Filter))
  //   ).getSpotInstanceRequests.map { request =>
  //     SpotInstanceRequest(ec2.asJava, request.getSpotInstanceRequestId)
  //   }.toList
  // }


  def cancelSpotRequest(requestId: String): Unit = {
    asJava.cancelSpotInstanceRequests(new CancelSpotInstanceRequestsRequest(List(requestId)))
  }

  // FIXME: EC2MetadataClient is deprecated
  // def getCurrentInstanceId: Option[String] = {
  //   try {
  //     val m = new com.amazonaws.internal.EC2MetadataClient()
  //     Some(m.readResource("/latest/meta-data/instance-id"))
  //   } catch {
  //     case t: IOException => None
  //
  //   }
  // }
  //
  // def getCurrentInstance: Option[Instance] = getCurrentInstanceId.flatMap(getInstanceById(_))

  // def getInstanceById(instanceId: String): Option[Instance] = {
  //   getEC2InstanceById(instanceId).map { Instance(ec2.asJava, _) }
  // }
  //
  // def getEC2InstanceById(instanceId: String): Option[model.Instance] = {
  //   try {
  //   asJava.describeInstances(new DescribeInstancesRequest()
  //     .withInstanceIds(instanceId)
  //   ).getReservations.flatMap(_.getInstances).headOption
  //   } catch {
  //     case e: AmazonServiceException if e.getErrorCode().equals("InvalidInstanceID.NotFound") => None
  //   }
  // }

  // def getEC2SpotRequestsById(requestsId: String): Option[SpotInstanceRequest] = {
  //   asJava.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest()
  //     .withSpotInstanceRequestIds(requestsId)
  //   ).getSpotInstanceRequests.headOption
  // }

  def getAllAvailableZones: Set[String] = {
    ec2.asJava.describeAvailabilityZones(
      new DescribeAvailabilityZonesRequest()
        .withFilters(
          new Filter("state", List("available"))
        )
    ).getAvailabilityZones
      .map{ _.getZoneName }.toSet
  }

}

package ohnosequences.awstools.ec2

import java.io.File

import com.amazonaws.auth.PropertiesCredentials
import com.amazonaws.services.ec2.{AmazonEC2Client, AmazonEC2}
import com.amazonaws.services.ec2.model._

import scala.collection.JavaConversions._
import java.net.{URL, NoRouteToHostException}


object InstanceSpecs {

  implicit def getLaunchSpecs(specs: InstanceSpecs) = {
    val launchSpecs = new LaunchSpecification()
    launchSpecs.setSecurityGroups(specs.securityGroups)
    launchSpecs.setInstanceType(specs.instanceType.toAWS)
    launchSpecs.setImageId(specs.amiId)
    launchSpecs.setKeyName(specs.keyName)
    launchSpecs.setUserData(Utils.base64encode(specs.userData))
    launchSpecs
  }
}


case class InstanceSpecs(
  instanceType   : ohnosequences.awstools.ec2.InstanceType,
  amiId          : String,      
  securityGroups : List[String] = List(),
  keyName        : String = "",
  userData       : String = "")


class EC2(val ec2: AmazonEC2) {

  def requestSpotInstances(amount: Int, price: Double, specs: InstanceSpecs, timeout: Int = 36000): List[ohnosequences.awstools.ec2.SpotInstanceRequest] = {
    ec2.requestSpotInstances(new RequestSpotInstancesRequest()
      .withSpotPrice(price.toString)
      .withInstanceCount(amount)
      .withLaunchSpecification(specs)
      .withLaunchGroup("grid")              //TODO!!!

     // .withValidUntil(new Date(System.currentTimeMillis() + timeout))
    ).getSpotInstanceRequests.map(SpotInstanceRequest(ec2, _)).toList
  }

  def runInstances(amount: Int, specs: InstanceSpecs): List[ohnosequences.awstools.ec2.Instance] = {
    val runRequest = new RunInstancesRequest(specs.amiId, amount, amount)
      .withInstanceType(specs.instanceType.toAWS)
      .withKeyName(specs.keyName)
      .withUserData(Utils.base64encode(specs.userData))
      .withSecurityGroups(specs.securityGroups)

    ec2.runInstances(runRequest).getReservation.getInstances.map(Instance(ec2, _)).toList
  }

  def getCurrentSpotPrice(instanceType: ohnosequences.awstools.ec2.InstanceType, productDescription: String) = {
    ec2.describeSpotPriceHistory(
      new DescribeSpotPriceHistoryRequest()
        .withStartTime(new java.util.Date())
        .withInstanceTypes(instanceType.toString)
        .withProductDescriptions(productDescription)
    ).getSpotPriceHistory.map(_.getSpotPrice.toDouble).fold(1D)(math.min(_, _))
  }


  def createTags(resourceId: String, tags: ohnosequences.awstools.ec2.Tag*) {
    ec2.createTags(new CreateTagsRequest().withResources(resourceId).withTags(tags.map(_.toECTag)))
  }

  def listInstancesByFilters(filters: ohnosequences.awstools.ec2.Filter*): List[ohnosequences.awstools.ec2.Instance] = {
    ec2.describeInstances(
      new DescribeInstancesRequest().withFilters(filters.map(_.toEC2Filter))
    ).getReservations().flatMap(_.getInstances).map(Instance(ec2, _)).toList
  }



  def listRequestsByFilters(filters: ohnosequences.awstools.ec2.Filter*): List[ohnosequences.awstools.ec2.SpotInstanceRequest] = {
    ec2.describeSpotInstanceRequests(
      new DescribeSpotInstanceRequestsRequest().withFilters(filters.map(_.toEC2Filter))
    ).getSpotInstanceRequests.map(SpotInstanceRequest(ec2, _)).toList
  }


  def terminateInstance(instanceId: String) {
    ec2.terminateInstances(new TerminateInstancesRequest(List(instanceId)))
  }

  def cancelSpotRequest(requestId: String) {
    ec2.cancelSpotInstanceRequests(new CancelSpotInstanceRequestsRequest(List(requestId)))
  }

  def shutdown() {
    ec2.shutdown()
  }

  def getCurrentInstanceId: Option[String] = {
    try {
      val currentIdURL = new URL("http://169.254.169.254/latest/meta-data/instance-id")
      Some(io.Source.fromURL(currentIdURL).mkString)
    } catch {
      case t: NoRouteToHostException => None

    }
  }

  def getCurrentInstance: Option[ohnosequences.awstools.ec2.Instance] = getCurrentInstanceId.flatMap(getInstanceById(_))

  def getInstanceById(instanceId: String): Option[ohnosequences.awstools.ec2.Instance] = {
    val instance = ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId)).getReservations.flatMap(_.getInstances).headOption
    instance.map(Instance(ec2, _))

  }

}

object EC2 {
  def create(credentialsFile: File): EC2 = {
    val ec2Client = new AmazonEC2Client(new PropertiesCredentials(credentialsFile))
    ec2Client.setEndpoint("http://ec2.eu-west-1.amazonaws.com")
    new EC2(ec2Client)
  }


}

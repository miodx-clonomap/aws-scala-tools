package ohnosequences.awstools.ec2

import java.io.{PrintWriter, File}

import com.amazonaws.auth.{BasicAWSCredentials, AWSCredentials, PropertiesCredentials}
import com.amazonaws.services.ec2.{AmazonEC2Client, AmazonEC2}
import com.amazonaws.services.ec2.model._

import scala.collection.JavaConversions._
import java.net.{URL, NoRouteToHostException}
import com.amazonaws.AmazonServiceException

import ohnosequences.awstools.{ec2 => awstools}
import com.amazonaws.services.ec2.{model => amazon}


object InstanceSpecs {

  implicit def getLaunchSpecs(specs: InstanceSpecs) = {
    (new LaunchSpecification()
      .withSecurityGroups(specs.securityGroups)
      .withInstanceType(specs.instanceType.toAWS)
      .withImageId(specs.amiId)
      .withKeyName(specs.keyName)
      .withUserData(Utils.base64encode(specs.userData))
      )
  }
}


case class InstanceSpecs(instanceType: awstools.InstanceType,
                         amiId: String,
                         securityGroups: List[String] = List(),
                         keyName: String = "",
                         userData: String = "")

case class InstanceStatus(instanceStatus: String, systemStatus: String)

class EC2(val ec2: AmazonEC2) {
  awstoolsEC2 =>

  class Instance(instanceId: String) {

    private def getEC2Instance(): amazon.Instance = awstoolsEC2.getEC2InstanceById(instanceId) match {
      case None => {
        throw new Error("Invalid instance of Instance class")
      }
      case Some(instance) => instance
    }

    def terminate() {
      awstoolsEC2.terminateInstance(instanceId)
    }

    def createTag(tag: ohnosequences.awstools.ec2.Tag) {
      awstoolsEC2.createTags(instanceId, List(tag))
    }

    def createTags(tags: List[awstools.Tag]) {
      awstoolsEC2.createTags(instanceId, tags)
    }

    def getTagValue(tagName: String): Option[String] = {
      getEC2Instance().getTags.find(_.getKey == tagName).map(_.getValue)

    }

    def getInstanceId() = instanceId

    def getSSHCommand(): Option[String] = {
      val instance = getEC2Instance()
      val keyPairFile = instance.getKeyName + ".pem"
      val publicDNS = instance.getPublicDnsName
      if (!publicDNS.isEmpty) {
        Some("ssh -i " + keyPairFile + " ec2-user@" + publicDNS)
      } else {
        None
      }
    }


    def getState(): String = {
      getEC2Instance().getState().getName
    }

    def getInstanceStatus(): Option[awstools.InstanceStatus] = {
      val statuses = ec2.describeInstanceStatus(new DescribeInstanceStatusRequest()
        .withInstanceIds(instanceId)
        ).getInstanceStatuses()
      if (statuses.isEmpty) None
      else {
        val is = statuses.head
        Some(awstools.InstanceStatus(
            is.getInstanceStatus().getStatus()
          , is.getSystemStatus().getStatus()
          )
        )
      }
    }

    def getPublicDNS(): Option[String] = {
      val dns = getEC2Instance().getPublicDnsName()
      if (dns.isEmpty) None else Some(dns)
    }
  }

  class SpotInstanceRequest(requestId: String) {

    def getSpotInstanceRequestId() = requestId


    private def getEC2Request(): amazon.SpotInstanceRequest = awstoolsEC2.getEC2SpotRequestsById(requestId) match {
      case None => {
        throw new Error("Invalid instance of SpotInstanceRequest class")
      }
      case Some(requests) => requests
    }

    def getTagValue(tagName: String): Option[String] = {
      getEC2Request().getTags.find(_.getKey == tagName).map(_.getValue)
    }

    def getInstanceId(): Option[String] = {
      val id = getEC2Request().getInstanceId
      if(id.isEmpty) None else Some(id)
    }

    def createTags(tags: List[awstools.Tag]) {
      awstoolsEC2.createTags(requestId, tags)
    }

    def getState(): String = {
      getEC2Request().getState()
    }

    def getStatus(): String = {
      getEC2Request().getState
    }

  }


  def isKeyPairExists(name: String): Boolean = {
    try {
      val pairs = ec2.describeKeyPairs(new DescribeKeyPairsRequest()
        .withKeyNames(name)
      ).getKeyPairs
      // println("here keypaurs " + pairs)
      !pairs.isEmpty
    } catch {
      case e: Throwable => false
    }
  }

  def createKeyPair(name: String, file: Option[File]) {
    if (!isKeyPairExists(name)) {
      val keyPair = ec2.createKeyPair(new CreateKeyPairRequest()
        .withKeyName(name)
      ).getKeyPair

      file.foreach { file =>
        val keyContent = keyPair.getKeyMaterial
        val writer = new PrintWriter(file)
        writer.print(keyContent)
        writer.close()

        //chmod 400
        file.setWritable(false, false)
        file.setReadable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
      }
    }
  }

  def deleteKeyPair(name: String) {
    ec2.deleteKeyPair(new DeleteKeyPairRequest()
      .withKeyName(name)
    )
  }

  def deleteSecurityGroup(name: String) {
    try {
      ec2.deleteSecurityGroup(new DeleteSecurityGroupRequest()
        .withGroupName(name)
      )
    } catch {
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.InUse") => ()
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.NotFound") => ()
    }
  }

  def enableSSHPortForGroup(name: String) {
    enablePortForGroup(name, 22)
  }

  def enablePortForGroup(name: String, port: Int) {
    try {
      ec2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest()
        .withGroupName(name)
        .withIpPermissions(new IpPermission()
        .withFromPort(port)
        .withToPort(port)
        .withIpRanges("0.0.0.0/0")
        .withIpProtocol("tcp")
      )
      )
    } catch {
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidPermission.Duplicate") => ()
    }

  }

  def createSecurityGroup(name: String) {
    try {
      ec2.createSecurityGroup(new CreateSecurityGroupRequest()
        .withGroupName(name)
        .withDescription(name)
      )
    } catch {
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.Duplicate") => ()
    }
  }

  def requestSpotInstances(amount: Int, price: Double, specs: InstanceSpecs, timeout: Int = 36000): List[SpotInstanceRequest] = {
    ec2.requestSpotInstances(new RequestSpotInstancesRequest()
      .withSpotPrice(price.toString)
      .withInstanceCount(amount)
      .withLaunchSpecification(specs)
    ).getSpotInstanceRequests.map{ request =>
      new SpotInstanceRequest(request.getSpotInstanceRequestId)
    }.toList
  }

  def runInstances(amount: Int, specs: InstanceSpecs): List[Instance] = {
    val runRequest = new RunInstancesRequest(specs.amiId, amount, amount)
      .withInstanceType(specs.instanceType.toAWS)
      .withKeyName(specs.keyName)
      .withUserData(Utils.base64encode(specs.userData))
      .withSecurityGroups(specs.securityGroups)

    ec2.runInstances(runRequest).getReservation.getInstances.toList.map {
      instance =>
        new Instance(instance.getInstanceId)
    }
  }

  def getCurrentSpotPrice(instanceType: awstools.InstanceType, productDescription: String): Double = {
    val price = ec2.describeSpotPriceHistory(
      new DescribeSpotPriceHistoryRequest()
        .withStartTime(new java.util.Date())
        .withInstanceTypes(instanceType.toString)
        .withProductDescriptions(productDescription)
    ).getSpotPriceHistory.map(_.getSpotPrice.toDouble).fold(0D)(math.max(_, _))

    math.min(1, price)
  }


  def createTags(resourceId: String, tags: List[awstools.Tag]) {
    ec2.createTags(new CreateTagsRequest()
      .withResources(resourceId)
      .withTags(tags.map(_.toECTag))
    )
  }

  def listInstancesByFilters(filters: awstools.Filter*): List[Instance] = {
    ec2.describeInstances(
      new DescribeInstancesRequest().withFilters(filters.map(_.toEC2Filter))
    ).getReservations.flatMap(_.getInstances).map { instance =>
        new Instance(instance.getInstanceId)
    }.toList
  }


  def listRequestsByFilters(filters: awstools.Filter*): List[SpotInstanceRequest] = {
    ec2.describeSpotInstanceRequests(
      new DescribeSpotInstanceRequestsRequest().withFilters(filters.map(_.toEC2Filter))
    ).getSpotInstanceRequests.map { request =>
      new SpotInstanceRequest(request.getSpotInstanceRequestId)
    }.toList
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

  def getCurrentInstance: Option[Instance] = getCurrentInstanceId.flatMap(getInstanceById(_))

  def getInstanceById(instanceId: String): Option[Instance] = {
    getEC2InstanceById(instanceId).map {
      ec2Instance =>
        new Instance(ec2Instance.getInstanceId)
    }
  }

  def getEC2InstanceById(instanceId: String): Option[amazon.Instance] = {
    ec2.describeInstances(new DescribeInstancesRequest()
      .withInstanceIds(instanceId)
    ).getReservations.flatMap(_.getInstances).headOption
  }

  def getEC2SpotRequestsById(requestsId: String): Option[amazon.SpotInstanceRequest] = {
    ec2.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest()
      .withSpotInstanceRequestIds(requestsId)
    ).getSpotInstanceRequests.headOption
  }

}

object EC2 {

  def create(credentialsFile: File): EC2 = {
    create(new PropertiesCredentials(credentialsFile))
  }

  def create(accessKey: String, secretKey: String): EC2 = {
    create(new BasicAWSCredentials(accessKey, secretKey))
  }

  private def create(credentials: AWSCredentials): EC2 = {
    val ec2Client = new AmazonEC2Client(credentials)
    ec2Client.setEndpoint("http://ec2.eu-west-1.amazonaws.com")
    new EC2(ec2Client)
  }

}

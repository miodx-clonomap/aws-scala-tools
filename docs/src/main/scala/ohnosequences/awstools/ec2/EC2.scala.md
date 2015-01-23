
```scala
package ohnosequences.awstools.ec2

import java.io.{IOException, PrintWriter, File}

import ohnosequences.awstools.regions.Region._
import ohnosequences.awstools.{ec2 => awstools}

import com.amazonaws.auth._
import com.amazonaws.services.ec2.{AmazonEC2Client, AmazonEC2}
import com.amazonaws.services.ec2.model._

import scala.collection.JavaConversions._
import com.amazonaws.AmazonServiceException

import com.amazonaws.services.ec2.{model => amazon}
import com.amazonaws.internal.StaticCredentialsProvider
import scala.Some


object InstanceSpecs {

  implicit def getLaunchSpecs(specs: InstanceSpecs) = {
    val ls = new LaunchSpecification()
      .withSecurityGroups(specs.securityGroups)
      .withInstanceType(specs.instanceType)
      .withImageId(specs.amiId)
      .withKeyName(specs.keyName)
      .withMonitoringEnabled(specs.instanceMonitoring)
      .withBlockDeviceMappings(specs.deviceMapping.map{ case (key, value) =>
        new BlockDeviceMapping()
          .withDeviceName(key)
          .withVirtualName(value)
      })
      .withUserData(Utils.base64encode(specs.userData))

    specs.instanceProfile match {
      case Some(name) => ls.withIamInstanceProfile(new IamInstanceProfileSpecification().withName(name))
      case None => ls
    }
  }
}


//case class DeviceMapping() {
//
//}
//
//object DeviceMapping {
//  def fromAWS()
//}


case class InstanceSpecs(instanceType: awstools.InstanceType,
                         amiId: String,
                         keyName: String = "",
                         userData: String = "",
                         instanceProfile: Option[String] = None,
                         securityGroups: List[String] = List(),
                         instanceMonitoring: Boolean = false,
                         deviceMapping: Map[String, String] = Map[String, String]())


case class InstanceStatus(val instanceStatus: String, val systemStatus: String)

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

    def getAMI(): String = {
      val instance = getEC2Instance()
      instance.getImageId()
    }

    def getInstanceType(): awstools.InstanceType = {
      val instance = getEC2Instance()
      awstools.InstanceType.fromName(instance.getInstanceType)
    }


    def getState(): String = {
      getEC2Instance().getState().getName
    }

    def getStatus(): Option[awstools.InstanceStatus] = {
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

  def deleteSecurityGroup(name: String, attempts: Int = 0): Boolean = {
    try {
      ec2.deleteSecurityGroup(new DeleteSecurityGroupRequest()
        .withGroupName(name)
      )
      true
    } catch {
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.InUse") => {
        if(attempts > 0) {
          Thread.sleep(2000)
          println("security group: " + name + " in use, waiting...")
          deleteSecurityGroup(name, attempts-1)
        } else {
          false
        }
      }
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidGroup.NotFound") => true
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
    val preRequest = new RunInstancesRequest(specs.amiId, amount, amount)
      .withInstanceType(specs.instanceType)
      .withKeyName(specs.keyName)
      .withUserData(Utils.base64encode(specs.userData))
      .withSecurityGroups(specs.securityGroups)

     // add IAM instance profile if needed
    val request = specs.instanceProfile match {
      case None => preRequest
      case Some(name) => preRequest.withIamInstanceProfile(
        new IamInstanceProfileSpecification().withName(name)
      )
    }

    ec2.runInstances(request).getReservation.getInstances.toList.map {
      instance => new Instance(instance.getInstanceId)
    }
  }

  def getCurrentSpotPrice(instanceType: awstools.InstanceType, productDescription: String = "Linux/UNIX"): Double = {
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
    try {
      ec2.terminateInstances(new TerminateInstancesRequest(List(instanceId)))
    } catch {
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidInstanceID.NotFound") => ()
    }
  }

  def cancelSpotRequest(requestId: String) {
    ec2.cancelSpotInstanceRequests(new CancelSpotInstanceRequestsRequest(List(requestId)))
  }

  def shutdown() {
    ec2.shutdown()
  }

  def getCurrentInstanceId: Option[String] = {
    try {
      val m = new com.amazonaws.internal.EC2MetadataClient()
      Some(m.readResource("/latest/meta-data/instance-id"))
    } catch {
      case t: IOException => None

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
    try {
    ec2.describeInstances(new DescribeInstancesRequest()
      .withInstanceIds(instanceId)
    ).getReservations.flatMap(_.getInstances).headOption
    } catch {
      case e: AmazonServiceException if e.getErrorCode().equals("InvalidInstanceID.NotFound") => None
    }
  }

  def getEC2SpotRequestsById(requestsId: String): Option[amazon.SpotInstanceRequest] = {
    ec2.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest()
      .withSpotInstanceRequestIds(requestsId)
    ).getSpotInstanceRequests.headOption
  }

}

object EC2 {

  def create(): EC2 = {
    create(new InstanceProfileCredentialsProvider())
  }

  def create(credentialsFile: File): EC2 = {
    create(new StaticCredentialsProvider(new PropertiesCredentials(credentialsFile)))
  }

  def create(accessKey: String, secretKey: String): EC2 = {
    create(new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
  }

  def create(credentials: AWSCredentialsProvider, region: ohnosequences.awstools.regions.Region = Ireland): EC2 = {
    val ec2Client = new AmazonEC2Client(credentials)
    ec2Client.setRegion(region)
    new EC2(ec2Client)
  }

}

```


------

### Index

+ src
  + main
    + scala
      + ohnosequences
        + awstools
          + autoscaling
            + [AutoScaling.scala][main/scala/ohnosequences/awstools/autoscaling/AutoScaling.scala]
            + [AutoScalingGroup.scala][main/scala/ohnosequences/awstools/autoscaling/AutoScalingGroup.scala]
          + cloudwatch
            + [CloudWatch.scala][main/scala/ohnosequences/awstools/cloudwatch/CloudWatch.scala]
          + dynamodb
            + [DynamoDB.scala][main/scala/ohnosequences/awstools/dynamodb/DynamoDB.scala]
            + [DynamoObjectMapper.scala][main/scala/ohnosequences/awstools/dynamodb/DynamoObjectMapper.scala]
            + [Utils.scala][main/scala/ohnosequences/awstools/dynamodb/Utils.scala]
          + ec2
            + [EC2.scala][main/scala/ohnosequences/awstools/ec2/EC2.scala]
            + [Filters.scala][main/scala/ohnosequences/awstools/ec2/Filters.scala]
            + [InstanceType.scala][main/scala/ohnosequences/awstools/ec2/InstanceType.scala]
            + [Utils.scala][main/scala/ohnosequences/awstools/ec2/Utils.scala]
          + regions
            + [Region.scala][main/scala/ohnosequences/awstools/regions/Region.scala]
          + s3
            + [Bucket.scala][main/scala/ohnosequences/awstools/s3/Bucket.scala]
            + [S3.scala][main/scala/ohnosequences/awstools/s3/S3.scala]
          + sns
            + [SNS.scala][main/scala/ohnosequences/awstools/sns/SNS.scala]
            + [Topic.scala][main/scala/ohnosequences/awstools/sns/Topic.scala]
          + sqs
            + [Queue.scala][main/scala/ohnosequences/awstools/sqs/Queue.scala]
            + [SQS.scala][main/scala/ohnosequences/awstools/sqs/SQS.scala]
        + logging
          + [Logger.scala][main/scala/ohnosequences/logging/Logger.scala]
          + [S3Logger.scala][main/scala/ohnosequences/logging/S3Logger.scala]
  + test
    + scala
      + ohnosequences
        + awstools
          + [DynamoDBTests.scala][test/scala/ohnosequences/awstools/DynamoDBTests.scala]
          + [EC2Tests.scala][test/scala/ohnosequences/awstools/EC2Tests.scala]
          + [InstanceTypeTests.scala][test/scala/ohnosequences/awstools/InstanceTypeTests.scala]
          + [RegionTests.scala][test/scala/ohnosequences/awstools/RegionTests.scala]
          + [S3Tests.scala][test/scala/ohnosequences/awstools/S3Tests.scala]
          + [SNSTests.scala][test/scala/ohnosequences/awstools/SNSTests.scala]
          + [SQSTests.scala][test/scala/ohnosequences/awstools/SQSTests.scala]

[main/scala/ohnosequences/awstools/autoscaling/AutoScaling.scala]: ../autoscaling/AutoScaling.scala.md
[main/scala/ohnosequences/awstools/autoscaling/AutoScalingGroup.scala]: ../autoscaling/AutoScalingGroup.scala.md
[main/scala/ohnosequences/awstools/cloudwatch/CloudWatch.scala]: ../cloudwatch/CloudWatch.scala.md
[main/scala/ohnosequences/awstools/dynamodb/DynamoDB.scala]: ../dynamodb/DynamoDB.scala.md
[main/scala/ohnosequences/awstools/dynamodb/DynamoObjectMapper.scala]: ../dynamodb/DynamoObjectMapper.scala.md
[main/scala/ohnosequences/awstools/dynamodb/Utils.scala]: ../dynamodb/Utils.scala.md
[main/scala/ohnosequences/awstools/ec2/EC2.scala]: EC2.scala.md
[main/scala/ohnosequences/awstools/ec2/Filters.scala]: Filters.scala.md
[main/scala/ohnosequences/awstools/ec2/InstanceType.scala]: InstanceType.scala.md
[main/scala/ohnosequences/awstools/ec2/Utils.scala]: Utils.scala.md
[main/scala/ohnosequences/awstools/regions/Region.scala]: ../regions/Region.scala.md
[main/scala/ohnosequences/awstools/s3/Bucket.scala]: ../s3/Bucket.scala.md
[main/scala/ohnosequences/awstools/s3/S3.scala]: ../s3/S3.scala.md
[main/scala/ohnosequences/awstools/sns/SNS.scala]: ../sns/SNS.scala.md
[main/scala/ohnosequences/awstools/sns/Topic.scala]: ../sns/Topic.scala.md
[main/scala/ohnosequences/awstools/sqs/Queue.scala]: ../sqs/Queue.scala.md
[main/scala/ohnosequences/awstools/sqs/SQS.scala]: ../sqs/SQS.scala.md
[main/scala/ohnosequences/logging/Logger.scala]: ../../logging/Logger.scala.md
[main/scala/ohnosequences/logging/S3Logger.scala]: ../../logging/S3Logger.scala.md
[test/scala/ohnosequences/awstools/DynamoDBTests.scala]: ../../../../../test/scala/ohnosequences/awstools/DynamoDBTests.scala.md
[test/scala/ohnosequences/awstools/EC2Tests.scala]: ../../../../../test/scala/ohnosequences/awstools/EC2Tests.scala.md
[test/scala/ohnosequences/awstools/InstanceTypeTests.scala]: ../../../../../test/scala/ohnosequences/awstools/InstanceTypeTests.scala.md
[test/scala/ohnosequences/awstools/RegionTests.scala]: ../../../../../test/scala/ohnosequences/awstools/RegionTests.scala.md
[test/scala/ohnosequences/awstools/S3Tests.scala]: ../../../../../test/scala/ohnosequences/awstools/S3Tests.scala.md
[test/scala/ohnosequences/awstools/SNSTests.scala]: ../../../../../test/scala/ohnosequences/awstools/SNSTests.scala.md
[test/scala/ohnosequences/awstools/SQSTests.scala]: ../../../../../test/scala/ohnosequences/awstools/SQSTests.scala.md

```scala
package ohnosequences.awstools.sns

import com.amazonaws.services.sns.model._
import com.amazonaws.services.sns.AmazonSNS
import scala.util.Try

case class ScalaSNSClient(val asJava: AmazonSNS) extends AnyVal { sns =>

  def getOrCreateTopic(name: String): Try[Topic] = Try {
    asJava.createTopic(name)
  }.map { response =>
    Topic(asJava, response.getTopicArn)
  }

  // NOTE: SNS topics are represented simply by their ARNs, so there is no way to get a topic by its name
  // NOTE: also creating or deleting a topic that doesn't exist does not result in an error

}

```




[main/scala/ohnosequences/awstools/autoscaling/client.scala]: ../autoscaling/client.scala.md
[main/scala/ohnosequences/awstools/autoscaling/filters.scala]: ../autoscaling/filters.scala.md
[main/scala/ohnosequences/awstools/autoscaling/package.scala]: ../autoscaling/package.scala.md
[main/scala/ohnosequences/awstools/autoscaling/PurchaseModel.scala]: ../autoscaling/PurchaseModel.scala.md
[main/scala/ohnosequences/awstools/ec2/AMI.scala]: ../ec2/AMI.scala.md
[main/scala/ohnosequences/awstools/ec2/client.scala]: ../ec2/client.scala.md
[main/scala/ohnosequences/awstools/ec2/instances.scala]: ../ec2/instances.scala.md
[main/scala/ohnosequences/awstools/ec2/InstanceType-AMI.scala]: ../ec2/InstanceType-AMI.scala.md
[main/scala/ohnosequences/awstools/ec2/InstanceType.scala]: ../ec2/InstanceType.scala.md
[main/scala/ohnosequences/awstools/ec2/LaunchSpecs.scala]: ../ec2/LaunchSpecs.scala.md
[main/scala/ohnosequences/awstools/ec2/package.scala]: ../ec2/package.scala.md
[main/scala/ohnosequences/awstools/package.scala]: ../package.scala.md
[main/scala/ohnosequences/awstools/regions/aliases.scala]: ../regions/aliases.scala.md
[main/scala/ohnosequences/awstools/regions/package.scala]: ../regions/package.scala.md
[main/scala/ohnosequences/awstools/s3/address.scala]: ../s3/address.scala.md
[main/scala/ohnosequences/awstools/s3/client.scala]: ../s3/client.scala.md
[main/scala/ohnosequences/awstools/s3/package.scala]: ../s3/package.scala.md
[main/scala/ohnosequences/awstools/s3/transfers.scala]: ../s3/transfers.scala.md
[main/scala/ohnosequences/awstools/sns/client.scala]: client.scala.md
[main/scala/ohnosequences/awstools/sns/package.scala]: package.scala.md
[main/scala/ohnosequences/awstools/sns/subscribers.scala]: subscribers.scala.md
[main/scala/ohnosequences/awstools/sns/topics.scala]: topics.scala.md
[main/scala/ohnosequences/awstools/sqs/client.scala]: ../sqs/client.scala.md
[main/scala/ohnosequences/awstools/sqs/messages.scala]: ../sqs/messages.scala.md
[main/scala/ohnosequences/awstools/sqs/package.scala]: ../sqs/package.scala.md
[main/scala/ohnosequences/awstools/sqs/queues.scala]: ../sqs/queues.scala.md
[test/scala/ohnosequences/awstools/autoscaling.scala]: ../../../../../test/scala/ohnosequences/awstools/autoscaling.scala.md
[test/scala/ohnosequences/awstools/instanceTypes.scala]: ../../../../../test/scala/ohnosequences/awstools/instanceTypes.scala.md
[test/scala/ohnosequences/awstools/package.scala]: ../../../../../test/scala/ohnosequences/awstools/package.scala.md
[test/scala/ohnosequences/awstools/sqs.scala]: ../../../../../test/scala/ohnosequences/awstools/sqs.scala.md
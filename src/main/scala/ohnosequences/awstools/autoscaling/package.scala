package ohnosequences.awstools

import com.amazonaws.auth._
import com.amazonaws.services.autoscaling.{ AmazonAutoScaling, AmazonAutoScalingClientBuilder }
import com.amazonaws.services.autoscaling.model._
import com.amazonaws.{ ClientConfiguration, PredefinedClientConfigurations }
import com.amazonaws.waiters._
import ohnosequences.awstools.regions._


package object autoscaling {

  def clientBuilder: AmazonAutoScalingClientBuilder =
    AmazonAutoScalingClientBuilder.standard()

  def defaultClient: AmazonAutoScaling =
    AmazonAutoScalingClientBuilder.defaultClient()

  @deprecated("Use autoscaling.clientBuilder or autoscaling.defaultClient instead", since = "0.19.0")
  def AutoScalingClient(
    region: AwsRegionProvider = new DefaultAwsRegionProviderChain(),
    credentials: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain(),
    configuration: ClientConfiguration = PredefinedClientConfigurations.defaultConfig()
  ): AmazonAutoScaling = {
    clientBuilder
      .withCredentials(credentials)
      .withClientConfiguration(configuration)
      .withRegion(region.getName)
      .build()
  }

  // Implicits
  implicit def toScalaAutoScalingClient(autoscaling: AmazonAutoScaling):
    ScalaAutoScalingClient =
    ScalaAutoScalingClient(autoscaling)

  /* `TagDescription` is essentially the same as `Tag`, but for some strange reason in Amazon SDK they are not related anyhow */
  implicit def tagDescriptionToTag(td: TagDescription): Tag =
    new Tag()
      .withKey(td.getKey)
      .withPropagateAtLaunch(td.isPropagateAtLaunch)
      .withResourceId(td.getResourceId)
      .withResourceType(td.getResourceType)
      .withValue(td.getValue)

  /* Waiting for the group to transition to a certain state. This is useful, for example, immediately after creating a group */
  type GroupWaiter = Waiter[DescribeAutoScalingGroupsRequest]

  implicit class waitersOps(val waiter: GroupWaiter) extends AnyVal {

    def withName(groupName: String): Unit = {

      waiter.run(
        new WaiterParameters(
          new DescribeAutoScalingGroupsRequest()
            .withAutoScalingGroupNames(groupName)
        )
      )
    }
  }
}

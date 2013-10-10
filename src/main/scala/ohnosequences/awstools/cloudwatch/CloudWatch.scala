package ohnosequences.awstools.cloudwatch

import java.io.File

import com.amazonaws.auth._
import com.amazonaws.services.cloudwatch.{model, AmazonCloudWatchClient, AmazonCloudWatch}
import com.amazonaws.services.cloudwatch.model.{GetMetricStatisticsRequest, StandardUnit, MetricDatum, PutMetricDataRequest}
import com.amazonaws.regions.Regions
import com.amazonaws.internal.StaticCredentialsProvider

class CloudWatch(val cw: AmazonCloudWatch) {

  def shutdown() {
    cw.shutdown()
  }

  def putMetricData(metric: String, namespace: String, value: Double) {
    cw.putMetricData(
      new PutMetricDataRequest()
        .withNamespace(namespace)
        .withMetricData(
          new MetricDatum()
            .withMetricName(metric)
            .withUnit(StandardUnit.Count)
            .withValue(value)
        )
    )
  }

  def getStats(metric: String) = {
    cw.getMetricStatistics(
      new GetMetricStatisticsRequest()
        .withMetricName(metric)
    )
  }



}

object CloudWatch {

  def create(): CloudWatch = {
    create(new InstanceProfileCredentialsProvider())
  }


  def create(credentialsFile: File): CloudWatch = {
    create(new StaticCredentialsProvider(new PropertiesCredentials(credentialsFile)))
  }

  def create(accessKey: String, secretKey: String): CloudWatch = {
    create(new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
  }

  def create(credentials: AWSCredentialsProvider): CloudWatch = {
    val cwClient = new AmazonCloudWatchClient(credentials)
    cwClient.setRegion(com.amazonaws.regions.Region.getRegion(Regions.EU_WEST_1))
    new CloudWatch(cwClient)
  }
}

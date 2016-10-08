package ohnosequences.awstools.sqs

import java.io.File
import java.net.URL

import scala.util.Try
import scala.collection.JavaConversions._

import ohnosequences.awstools.regions.Region._

import com.amazonaws.services.sqs._
import com.amazonaws.services.sqs.model._
import com.amazonaws.auth._
import com.amazonaws.AmazonServiceException
import com.amazonaws.internal.StaticCredentialsProvider


case class ScalaSQSClient(val asJava: AmazonSQS) extends AnyVal { sqs =>

  def createOrGet(queueName: String): Try[Queue] = Try {
    val response: CreateQueueResult = sqs.asJava.createQueue(queueName)
    Queue(sqs.asJava, new URL(response.getQueueUrl))
  }

  def get(queueName: String): Try[Queue] = Try {
    val response: GetQueueUrlResult = sqs.asJava.getQueueUrl(queueName)
    Queue(sqs.asJava, new URL(response.getQueueUrl))
  }

  // def list(namePrefix: String): Try[Seq[Queue]] = Try {
  //   val response: ListQueuesResult = sqs.asJava.listQueues(namePrefix)
  //   response.getQueueUrls.map { url => Queue(sqs.asJava, new URL(url)) }
  // }
}

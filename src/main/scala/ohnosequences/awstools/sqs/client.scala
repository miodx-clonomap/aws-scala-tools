package ohnosequences.awstools.sqs

import com.amazonaws.services.sqs._
import com.amazonaws.services.sqs.model._
import scala.util.Try
import scala.collection.JavaConverters._
import java.net.URL


case class ScalaSQSClient(val asJava: AmazonSQS) extends AnyVal { sqs =>

  /* This may fail if the queue with this name was recently deleted (within 60s) */
  def getOrCreateQueue(queueName: String): Try[Queue] = Try {
    val response: CreateQueueResult = sqs.asJava.createQueue(queueName)
    Queue(sqs.asJava, new URL(response.getQueueUrl))
  }

  /* This may fail if the queue does not exist */
  def getQueue(queueName: String): Try[Queue] = Try {
    val response: GetQueueUrlResult = sqs.asJava.getQueueUrl(queueName)
    Queue(sqs.asJava, new URL(response.getQueueUrl))
  }

  def listQueues(namePrefix: String): Try[Seq[Queue]] = Try {
    val response: ListQueuesResult = sqs.asJava.listQueues(namePrefix)
    response.getQueueUrls.asScala.map { url => Queue(sqs.asJava, new URL(url)) }
  }
}

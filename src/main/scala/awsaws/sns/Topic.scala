package awsaws.sns

import awsaws.sqs.Queue

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model._
import com.amazonaws.auth.policy.{Resource, Principal, Statement, Policy}
import com.amazonaws.auth.policy.Statement.Effect
import com.amazonaws.auth.policy.actions.SQSActions
import com.amazonaws.auth.policy.conditions.ConditionFactory
import com.amazonaws.services.sqs.model.QueueAttributeName

case class Topic(sns: AmazonSNS, topicArn: String, name: String) {

  def publish(message: String) {
    sns.publish(new PublishRequest(topicArn, message))
  }

  def setAttribute(name: String, value: String) {
    sns.setTopicAttributes(new SetTopicAttributesRequest(topicArn, name, value))
  }

  def subscribeQueue(queue: Queue) {

    sns.subscribe(new SubscribeRequest(topicArn, "sqs", queue.getArn))

    val policyId = queue.getArn + "\\SQSDefaultPolicy"

    val policy = new Policy(policyId).withStatements(new Statement(Effect.Allow)
      .withPrincipals(Principal.AllUsers)
      .withActions(SQSActions.SendMessage)
      .withResources(new Resource(queue.getArn))
      .withConditions(ConditionFactory.newSourceArnCondition(topicArn))
    )

    queue.setAttributes(Map(QueueAttributeName.Policy.toString -> policy.toJson))

  }

  override def toString = {
    "[ name=" + name + "; " + "arn=" + topicArn + " (" + sns.listSubscriptions() + ") ]"
  //  sns.listSubscriptions()
  }

  def delete() {
    sns.deleteTopic(new DeleteTopicRequest(topicArn))
  }
}

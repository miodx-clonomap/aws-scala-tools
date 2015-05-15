package ohnosequences.awstools.dynamodb

import com.amazonaws.AmazonClientException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import ohnosequences.logging.Logger
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class RepeatConfiguration(attemptThreshold: Int = 100,
                               initialTimeout: Duration = Duration(500, MILLISECONDS),
                               timeoutThreshold: Duration = Duration(1, MINUTES),
                               coefficient: Double = 1.5) {
  def nextTimeout(timeout: Long): Long = {
    math.max(timeoutThreshold.toMillis, (coefficient * timeout).toLong)
  }
}

object DynamoDBUtils {


  /**
   * writes request in batch mode, always tries to write maximum -- 25
   * @param ddb AWS DynamoDB client
   * @param table table name
   * @param requests list of request
   * @param logger
   */
  def writeWriteRequests(ddb: AmazonDynamoDB, table: String, requests: List[WriteRequest], logger: Logger): Try[Unit] = {


    @tailrec
    def writeWriteRequestsRec(requests: List[WriteRequest]): Try[Unit] = {

      if (requests == null || requests.isEmpty) {
        Success(())
      } else {
        val (left, right) = requests.splitAt(25)
        val javaLeftList: java.util.List[WriteRequest] = left


        val res: Try[Option[BatchWriteItemResult]] = try {
          Success(Some(ddb.batchWriteItem(Map(table -> javaLeftList))))
        } catch {
          case p: ProvisionedThroughputExceededException => //non fatal; retry
            logger.warn(p)
            //
            Success(None)
          case amazon: AmazonClientException => Failure(amazon) //report
        }

        res match {
          case Success(None) => {
            //repeat
            writeWriteRequestsRec(requests)
          }
          case Success(Some(r)) => {
            val newLeft: List[WriteRequest] = Option(r.getUnprocessedItems).map { mapOperations =>
              val unprocessedList = mapOperations.get(table)
              if (unprocessedList == null) {
                List[WriteRequest]()
              } else {
                unprocessedList.toList
              }
            }.getOrElse(left)

            writeWriteRequestsRec(newLeft ++ right)
          }
          case Failure(t) => Failure(t)
        }
      }
    }
    writeWriteRequestsRec(requests)
  }

  /**
   * writes request in batch mode
   * @param ddb AWS DynamoDB client
   * @param table table name
   * @param requests list of request
   * @param logger
   */
  def writeWriteRequestsBatchBuff(ddb: AmazonDynamoDB, table: String, buffer: List[WriteRequest], requests: List[WriteRequest], logger: Logger): Try[Unit] = {

    @tailrec
    def writeWriteRequestsBatchBuffRec(buffer: List[WriteRequest], requests: List[WriteRequest]): Try[Unit] = {
      if (buffer == null || buffer.isEmpty) {
        if (requests == null || requests.isEmpty) {
          Success(())
        } else {
          val (left, right) = requests.splitAt(25)
          writeWriteRequestsBatchBuffRec(left, right)
        }
      } else {
        //Try {

        val javaLeftList: java.util.List[WriteRequest] = buffer

        val res: Try[Option[BatchWriteItemResult]] = try {
          Success(Some(ddb.batchWriteItem(Map(table -> javaLeftList))))
        } catch {
          case p: ProvisionedThroughputExceededException => //non fatal; retry
            logger.warn(p)
            //
            Success(None)
          case amazon: AmazonClientException => Failure(amazon) //report
        }

        res match {
          case Success(None) => {
            //repeat
            writeWriteRequestsBatchBuffRec(buffer, requests)
          }
          case Success(Some(r)) => {
            val newLeft: List[WriteRequest] = Option(r.getUnprocessedItems).map { mapOperations =>
              val unprocessedList = mapOperations.get(table)
              if (unprocessedList == null) {
                List[WriteRequest]()
              } else {
                unprocessedList.toList
              }
            }.getOrElse(buffer)
            writeWriteRequestsBatchBuffRec(newLeft, requests)
          }
          case Failure(t) => Failure(t)
        }
      }
    }

    writeWriteRequestsBatchBuffRec(List[WriteRequest](), requests)
  }

  def repeatDynamoDBAction[T](actionName: String,
                              logger: Option[Logger],
                              repeatConfiguration: RepeatConfiguration
                             )
                             (action: => Try[T]): Try[T] = {
    @tailrec
    def repeatDynamoDBActionRec(attempt: Int, timeout: Long): Try[T] = {
      if (attempt > repeatConfiguration.attemptThreshold) {
        Failure(new Error("attempt threshold is reached for " + actionName))
      } else {

        val rawRes:Either[Throwable, Try[T]] = try {
          logger.foreach { _.debug(actionName) }
          val res: Try[T] = action
          Right(action)
        } catch {
          case NonFatal(t) => {
            //Failure(new Error(actionName + " failed", t))
            Left(t)
          }
        }

        rawRes match {
          case Left(t) => {
            Failure(new Error(actionName + " failed", t))
          }
          case Right(Failure(p : ProvisionedThroughputExceededException)) => {
            logger.foreach {
              _.warn("got ProvisionedThroughputExceededException during execution of " + actionName)
            }
            Thread.sleep(timeout)
            repeatDynamoDBActionRec(attempt + 1, repeatConfiguration.nextTimeout(timeout))
          }
          case Right(Failure(NonFatal(t))) => {
            Failure(new Error(actionName + " failed", t))
          }
          case Right(rest) => rest
        }

      }
    }
    repeatDynamoDBActionRec(1, repeatConfiguration.initialTimeout.toMillis)
  }



  def isEmpty(ddb: AmazonDynamoDB,
              table: String,
              logger: Option[Logger] = None,
              repeatConfiguration: RepeatConfiguration = RepeatConfiguration()): Try[Boolean] = {
    repeatDynamoDBAction("checking table " + table,
      logger,
      repeatConfiguration
    ) {
      Try {
        val count: Int = ddb.scan(new ScanRequest()
          .withTableName(table)
          .withSelect(Select.COUNT)
          .withLimit(1)
        ).getCount
        count == 0
      }
    }
  }

  @tailrec
  def writeWriteRequestsNonBatch(ddb: AmazonDynamoDB, table: String, requests: List[WriteRequest], logger: Logger): Try[Unit] = {
    requests match {
      case null => Success(())
      case Nil => Success(())
      case head :: tail => {

        val itemWritten = try {
          ddb.putItem(table, head.getPutRequest.getItem)
          Success(true)
        } catch {
          case p: ProvisionedThroughputExceededException => {
            //non fatal
            Success(false)
          }
          case a: AmazonClientException => Failure(a)
        }

        itemWritten match {
          case Failure(f) => Failure(f)
          case Success(true) => {
            writeWriteRequestsNonBatch(ddb, table, tail, logger)
          }
          case Success(false) => writeWriteRequestsNonBatch(ddb, table, requests, logger)
        }
      }
    }
  }



  def deleteTable(ddb: AmazonDynamoDB, table: String): Try[Unit] = {
    Try {
      ddb.deleteTable(new DeleteTableRequest().withTableName(table))
    }
  }


  def waitForResource[A](resourceCheck: => Option[A], iterationsThreshold: Int = 100) : Option[A] = {

    @tailrec
    def waitForResourceRec(iteration: Int): Option[A] = {
      if (iteration > iterationsThreshold) {
        None
      } else {
        val current = resourceCheck
        resourceCheck match {
          case None => {
            Thread.sleep(5000)
            waitForResourceRec(iteration + 1)
          }
          case Some(res) => {
            Some(res)
          }
        }
      }
    }
    waitForResourceRec(1)
  }

  def changeThroughput(ddb: AmazonDynamoDB,
                       table: String,
                       readThroughput: Long = 1,
                       writeThroughput: Long = 1
                        ): Try[Unit] = {
    Try {
      ddb.updateTable(new UpdateTableRequest()
        .withTableName(table)
        .withProvisionedThroughput(new ProvisionedThroughput()
        .withReadCapacityUnits(readThroughput)
        .withWriteCapacityUnits(writeThroughput)
        )
      )
    }
  }


  def createTable(ddb: AmazonDynamoDB,
                  tableName: String,
                  hash: AttributeDefinition,
                  range: Option[AttributeDefinition] = None,
                  logger: Logger,
                  writeThroughput: Long = 1,
                  readThroughput: Long = 1,
                  waitForCreation: Boolean = true
                   ): Boolean =  {
    try {
      ddb.describeTable(new DescribeTableRequest()
        .withTableName(tableName)
      )
      logger.warn("warning: table " + tableName + " already exists")
      true
    } catch {
      case e: ResourceNotFoundException => {
        //create table
        var request = new CreateTableRequest()
          .withTableName(tableName)
          .withProvisionedThroughput(new ProvisionedThroughput()
            .withReadCapacityUnits(readThroughput)
            .withWriteCapacityUnits(writeThroughput)
          )

        range match {
          case Some(rng) => {
            request = request
              .withKeySchema(
                new KeySchemaElement(hash.getAttributeName, "HASH"),
                new KeySchemaElement(rng.getAttributeName, "RANGE")
              )
              .withAttributeDefinitions(
                new AttributeDefinition(hash.getAttributeName, hash.getAttributeType),
                new AttributeDefinition(rng.getAttributeName, rng.getAttributeType)
              )
          }
          case None => {
            request = request
              .withKeySchema(
                new KeySchemaElement(hash.getAttributeName, "HASH")
              )
              .withAttributeDefinitions(
                new AttributeDefinition(hash.getAttributeName, hash.getAttributeType)
              )
          }
        }

        ddb.createTable(request)

        if (waitForCreation) {
          !waitForResource {
            logger.info("waiting for table " + tableName)
            getTableState(ddb, tableName).flatMap{
              case "ACTIVE" => Some("ACTIVE")
              case _ => None
            }
          }.isEmpty
        } else {
          true
        }
      }
    }
  }

  def getTableState(ddb: AmazonDynamoDB, name: String): Option[String] = {
    try {
      val r = ddb.describeTable(new DescribeTableRequest()
        .withTableName(name)
      ).getTable.getTableStatus
      Some(r)
    } catch {
      case r: ResourceNotFoundException => None
    }
  }



  def getItem(ddb: AmazonDynamoDB,
              logger: Option[Logger],
              tableName: String,
              key: Map[String, AttributeValue],
              attributesToGet: Seq[String],
              repeatConfiguration: RepeatConfiguration = RepeatConfiguration()
               ): Try[Map[String, AttributeValue]] = {

    repeatDynamoDBAction("getting item with key " + key + " from table " + tableName,
      logger,
      repeatConfiguration
    ) {
      val rawItem = ddb.getItem(new GetItemRequest()
        .withTableName(tableName)
        .withKey(key)
        .withAttributesToGet(attributesToGet)
      ).getItem
      if (Option(rawItem).isDefined) {
        Success(rawItem.toMap)
      } else {
        Failure(new Error("key " + key + " doesn't exist"))
      }
    }
  }

  def putItem(ddb: AmazonDynamoDB,
              logger: Option[Logger],
              tableName: String,
              item: Map[String, AttributeValue],
              repeatConfiguration: RepeatConfiguration = RepeatConfiguration()): Try[Unit] = {

    repeatDynamoDBAction("putting item to the table " + tableName,
      logger,
      repeatConfiguration
    ){
      ddb.putItem(tableName, item)
      Success(())
    }
  }


  //todo next token
  def countKeysPerHash(ddb: AmazonDynamoDB,
                       logger: Option[Logger],
                       tableName: String,
                       hashKeyName: String,
                       hashValue: AttributeValue,
                       repeatConfiguration: RepeatConfiguration = RepeatConfiguration()): Try[Int] = {

    val conditions = new java.util.HashMap[String, Condition]()
    conditions.put(hashKeyName, new Condition()
      .withAttributeValueList(hashValue)
      .withComparisonOperator(ComparisonOperator.EQ)
    )

    repeatDynamoDBAction("count items with hash " + hashKeyName + " in the table " + tableName,
      logger,
      repeatConfiguration
    ){
      val r = ddb.query(new QueryRequest()
        .withTableName(tableName)
        .withKeyConditions(conditions)
        .withSelect(Select.COUNT)
      ).getCount
      Success(r)
    }
  }

  //todo next token
  def queryPerHash(ddb: AmazonDynamoDB,
                  logger: Option[Logger],
                  tableName: String,
                  hashKeyName: String,
                  hashValue: AttributeValue,
                  attributesToGet: Seq[String],
                  repeatConfiguration: RepeatConfiguration = RepeatConfiguration()): Try[List[Map[String, AttributeValue]]] = {

    val conditions = new java.util.HashMap[String, Condition]()
    conditions.put(hashKeyName, new Condition()
      .withAttributeValueList(hashValue)
      .withComparisonOperator(ComparisonOperator.EQ)
    )

    repeatDynamoDBAction("quering items with hash " + hashKeyName + " in the table " + tableName,
      logger,
      repeatConfiguration
    ){
      val r = ddb.query(new QueryRequest()
        .withTableName(tableName)
        .withKeyConditions(conditions)
        .withAttributesToGet(attributesToGet)
      ).getItems.toList.map(_.toMap)
      Success(r)
    }
  }

  def list(ddb: AmazonDynamoDB,
           logger: Option[Logger],
           tableName: String,
           lastKey: Option[Map[String, AttributeValue]],
           attributesToGet: Seq[String],
           limit: Option[Int],
           repeatConfiguration: RepeatConfiguration = RepeatConfiguration()): Try[(Option[Map[String, AttributeValue]], List[Map[String, AttributeValue]])] = {

    repeatDynamoDBAction("list items in the table " + tableName,
      logger,
      repeatConfiguration
    ) {
      val request = new ScanRequest()
        .withTableName(tableName)
        .withAttributesToGet(attributesToGet)
      limit.foreach { l => request.setLimit(l)}
      lastKey match {
        case Some(key) => request.withExclusiveStartKey(key)
        case None => ()
      }

      val scanRes = ddb.scan(request)
      val resultItems: List[Map[String, AttributeValue]] = scanRes.getItems.map(_.toMap).toList
      val newLastKey: Option[Map[String, AttributeValue]] = Option(scanRes.getLastEvaluatedKey) match {
        case Some(key) if !key.isEmpty => {
          Some(key.toMap)
        }
        case _ => None
      }
      Success((newLastKey, resultItems))
    }
  }

  def deleteItem(ddb: AmazonDynamoDB,
                 logger: Option[Logger],
                 tableName: String,
                 key: Map[String, AttributeValue],
                 repeatConfiguration: RepeatConfiguration = RepeatConfiguration()): Try[Unit] = {

    repeatDynamoDBAction("deleting item with key " + key + " from the table " + tableName,
      logger,
      repeatConfiguration
    ) {
      val rawItem = ddb.deleteItem(new DeleteItemRequest()
        .withTableName(tableName)
        .withKey(key)
      )
      Success(())
    }
  }

}
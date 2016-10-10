package ohnosequences.awstools.s3

// import com.amazonaws.services.s3.model.{ Region => _ , _ }
import com.amazonaws.{AmazonClientException, AmazonServiceException}
import com.amazonaws.services.s3.transfer._
import com.amazonaws.services.s3.model.{ S3Object => _, _ }
import com.amazonaws.event._
import com.amazonaws.event.{ProgressListener => PListener, ProgressEvent => PEvent}

import java.io.File

import scala.collection.JavaConversions._
import scala.util.Try


case class TransferListener(transfer: Transfer) extends PListener {

  def progressChanged(progressEvent: PEvent): Unit = {
    import ProgressEventType._
    progressEvent.getEventType match {
      // case TRANSFER_STARTED_EVENT  => println("Started")
      case TRANSFER_CANCELED_EVENT  => println(s"${transfer.getDescription} is canceled")
      case TRANSFER_COMPLETED_EVENT => println(s"${transfer.getDescription} is completed")
      case TRANSFER_FAILED_EVENT    => println(s"${transfer.getDescription} is failed")
      // case TRANSFER_PART_COMPLETED_EVENT  => println("Completed part: "+ transfer.getProgress.getBytesTransferred)
      case _ => ()
    }
  }
}


// This is used for adding metadata to the S3 objects that we are uploading
case class s3MetadataProvider(metadataMap: Map[String, String]) extends ObjectMetadataProvider {

  def provideObjectMetadata(file: java.io.File, metadata: ObjectMetadata): Unit = {
    metadata.setUserMetadata(metadataMap)
  }
}


case class TransferManagerOps(asJava: TransferManager) {

  // by default shutdownNow shuts down the S3 client as well
  def shutdown(shutDownS3Client: Boolean = false): Unit =
    asJava.shutdownNow(shutDownS3Client)


  def download(
    s3Address: AnyS3Address,
    destination: File
  ): Try[File] = {
    println(s"""Dowloading object
      |from: ${s3Address}
      |to: ${destination.getCanonicalPath}
      |""".stripMargin
    )

    lazy val transfer: Transfer = s3Address match {
      case S3Object(bucket, key) => asJava.download(bucket, key, destination)
      case S3Folder(bucket, key) => asJava.downloadDirectory(bucket, key, destination)
    }

    Try {
      transfer.addProgressListener(TransferListener(transfer))
      transfer.waitForCompletion

      // if this was a virtual directory, the destination actually differs:
      s3Address match {
        case S3Object(_, key) => destination
        case S3Folder(_, key) => new File(destination, key)
      }
    }
  }

  def upload(
    file: File,
    s3Address: AnyS3Address,
    userMetadata: Map[String, String] = Map()
  ): Try[AnyS3Address] = {
    println(s"""Uploading object
      |from: ${file.getCanonicalPath}
      |to: ${s3Address}
      |""".stripMargin
    )

    lazy val transfer: Transfer = if (file.isDirectory) {
      asJava.uploadDirectory(
        s3Address.bucket,
        s3Address.key,
        file,
        true, // includeSubdirectories
        s3MetadataProvider(userMetadata)
      )
    } else {
      val request = new PutObjectRequest(
        s3Address.bucket,
        s3Address.key,
        file
      )

      val metadata = new ObjectMetadata()
      metadata.setUserMetadata(userMetadata)

      asJava.upload( request.withMetadata(metadata) )
    }

    Try {
      transfer.addProgressListener(TransferListener(transfer))
      transfer.waitForCompletion
      s3Address
    }
  }

}

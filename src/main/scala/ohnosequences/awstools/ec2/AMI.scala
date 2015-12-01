package ohnosequences.awstools.ec2

import ohnosequences.awstools.regions

/* ## [Amazon Machine Images (AMI)](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ComponentsAMIs.html) */


sealed abstract class Architecture(val wordSize: Int)
case object x86_32 extends Architecture(32)
case object x86_64 extends Architecture(64)


/* ### [Linux AMI Virtualization Types](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/virtualization_types.html) */
sealed trait AnyVirtualization
/* All current generation instance types support HVM AMIs.
   The CC2, CR1, HI1, and HS1 previous generation instance types support HVM AMIs. */
case object HVM extends AnyVirtualization //; type HVM = HVM.type
/* The C3 and M3 current generation instance types support PV AMIs.
   The C1, HI1, HS1, M1, M2, and T1 previous generation instance types support PV AMIs. */
case object PV extends AnyVirtualization //; type PV = PV.type


/* ### [Storage for the Root Device](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ComponentsAMIs.html#storage-for-the-root-device) */
sealed trait AnyStorageType
case object EBS extends AnyStorageType //; type EBS = EBS.type
case object InstanceStore extends AnyStorageType //; type InstanceStore = InstanceStore.type


// TODO: [Launch permissions](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ComponentsAMIs.html#launch-permissions)


/* Just some base trait: */
trait AnyAMI {

  val id: String
}

trait AnyLinuxAMI extends AnyAMI {

  val version: String

  type Region <: regions.Region
  val  region: Region

  type Arch <: Architecture
  val  arch: Arch

  type Virt <: AnyVirtualization
  val  virt: Virt

  type Storage <: AnyStorageType
  val  storage: Storage
}

// Amazon Linux AMI 2015.09.1
// See http://aws.amazon.com/amazon-linux-ami/
trait AnyAmazonLinuxAMI extends AnyLinuxAMI {

  final val version: String = "2015.09.1"

  type Arch = x86_64.type
  final val arch: Arch = x86_64

  final lazy val id: String = s"ami-${idNum}"

  private lazy val idNum: String = {
    val r: regions.Region = region
    val v: AnyVirtualization = virt
    val s: AnyStorageType = storage
    import regions.Region._

    r match {
      case NorthernVirginia  => v match {
        case HVM => s match {
          case EBS           => "60b6c60a"
          case InstanceStore => "66b6c60c"
        }
        case PV  => s match {
          case EBS           => "5fb8c835"
          case InstanceStore => "30b6c65a"
        }
      }
      case Oregon => v match {
        case HVM => s match {
          case EBS           => "f0091d91"
          case InstanceStore => "31342050"
        }
        case PV  => s match {
          case EBS           => "d93622b8"
          case InstanceStore => "960317f7"
        }
      }
      case NorthernCalifornia => v match {
        case HVM => s match {
          case EBS           => "d5ea86b5"
          case InstanceStore => "ede78b8d"
        }
        case PV  => s match {
          case EBS           => "56ea8636"
          case InstanceStore => "c6eb87a6"
        }
      }
      case Ireland => v match {
        case HVM => s match {
          case EBS           => "bff32ccc"
          case InstanceStore => "54e03f27"
        }
        case PV  => s match {
          case EBS           => "95e33ce6"
          case InstanceStore => "54e53a27"
        }
      }
      case Frankfurt => v match {
        case HVM => s match {
          case EBS           => "bc5b48d0"
          case InstanceStore => "8d4a59e1"
        }
        case PV  => s match {
          case EBS           => "794a5915"
          case InstanceStore => "ef445783"
        }
      }
      case Singapore => v match {
        case HVM => s match {
          case EBS           => "c9b572aa"
          case InstanceStore => "deb176bd"
        }
        case PV  => s match {
          case EBS           => "34bd7a57"
          case InstanceStore => "66bf7805"
        }
      }
      case Tokyo => v match {
        case HVM => s match {
          case EBS           => "383c1956"
          case InstanceStore => "4232172c"
        }
        case PV  => s match {
          case EBS           => "393c1957"
          case InstanceStore => "4532172b"
        }
      }
      case Sydney => v match {
        case HVM => s match {
          case EBS           => "48d38c2b"
          case InstanceStore => "cad986a9"
        }
        case PV  => s match {
          case EBS           => "ced887ad"
          case InstanceStore => "cfd887ac"
        }
      }
      case SaoPaulo => v match {
        case HVM => s match {
          case EBS           => "6817af04"
          case InstanceStore => "7b15ad17"
        }
        case PV  => s match {
          case EBS           => "7d15ad11"
          case InstanceStore => "4f13ab23"
        }
      }
      case Beijing => v match {
        case HVM => s match {
          case EBS           => "43a36a2e"
          case InstanceStore => "52a0693f"
        }
        case PV  => s match {
          case EBS           => "18ac6575"
          case InstanceStore => "41a46d2c"
        }
      }
      case GovCloud => v match {
        case HVM => s match {
          case EBS           => "c2b5d7e1"
          case InstanceStore => "f4b5d7d7"
        }
        case PV  => s match {
          case EBS           => "feb5d7dd"
          case InstanceStore => "f6b5d7d5"
        }
      }
    }
  }
}

case class AmazonLinuxAMI[
  R <: regions.Region,
  V <: AnyVirtualization,
  S <: AnyStorageType
](val region: R,
  val virt: V,
  val storage: S
) extends AnyAmazonLinuxAMI {

  type Region = R
  type Virt = V
  type Storage = S
}

package ohnosequences.awstools.ec2

import com.amazonaws.services.ec2.model

sealed trait AnyInstanceType {
  type Family <: InstanceType.Family
  val  family: Family

  final lazy val size: String = this.toString
  final lazy val name: String = s"${family.prefix}.${size}"
}

case object AnyInstanceType {
  import InstanceType._

  type ofGeneration[G <: AnyGeneration] = AnyInstanceType { type Family <: G }
  type ofFamily[F <: Family] = AnyInstanceType { type Family = F }

  implicit def toJavaInstanceType(t: AnyInstanceType):
    model.InstanceType =
    model.InstanceType.fromValue(t.name)
}

sealed class InstanceType[
  F <: InstanceType.Family
](val family: F) extends AnyInstanceType {
  type Family = F
}

case object InstanceType {

  // This is taken from http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instance-types.html
  // TODO: write tests that check that the string name corresponds to the object name

  sealed trait AnyGeneration
  trait CurrentGeneration extends AnyGeneration
  trait PreviousGeneration extends AnyGeneration

  sealed class Family { family: AnyGeneration =>

    lazy val prefix: String = this.toString
  }

  /* ### Current Generation Instances */

  /* #### General purpose */
  case object t2 extends Family with CurrentGeneration {
    case object nano   extends InstanceType(t2)
    case object micro  extends InstanceType(t2)
    case object small  extends InstanceType(t2)
    case object medium extends InstanceType(t2)
    case object large  extends InstanceType(t2)
  }
  case object m4 extends Family with CurrentGeneration {
    case object     large  extends InstanceType(m4)
    case object    xlarge  extends InstanceType(m4)
    case object  `2xlarge` extends InstanceType(m4)
    case object  `4xlarge` extends InstanceType(m4)
    case object `10xlarge` extends InstanceType(m4)
    case object `16xlarge` extends InstanceType(m4)
  }
  case object m3 extends Family with CurrentGeneration {
    case object medium    extends InstanceType(m3)
    case object    large  extends InstanceType(m3)
    case object   xlarge  extends InstanceType(m3)
    case object `2xlarge` extends InstanceType(m3)
  }

  /* #### Compute optimized */
  case object c4 extends Family with CurrentGeneration {
    case object    large  extends InstanceType(c4)
    case object   xlarge  extends InstanceType(c4)
    case object `2xlarge` extends InstanceType(c4)
    case object `4xlarge` extends InstanceType(c4)
    case object `8xlarge` extends InstanceType(c4)
  }
  case object c3 extends Family with CurrentGeneration {
    case object    large  extends InstanceType(c3)
    case object   xlarge  extends InstanceType(c3)
    case object `2xlarge` extends InstanceType(c3)
    case object `4xlarge` extends InstanceType(c3)
    case object `8xlarge` extends InstanceType(c3)
  }

  /* #### Memory optimized */
  case object r3 extends Family with CurrentGeneration {
    case object    large  extends InstanceType(r3)
    case object   xlarge  extends InstanceType(r3)
    case object `2xlarge` extends InstanceType(r3)
    case object `4xlarge` extends InstanceType(r3)
    case object `8xlarge` extends InstanceType(r3)
  }
  case object x1 extends Family with CurrentGeneration {
    case object `16xlarge` extends InstanceType(x1)
    case object `32xlarge` extends InstanceType(x1)
  }

  /* #### Storage optimized */
  case object i2 extends Family with CurrentGeneration {
    case object   xlarge  extends InstanceType(i2)
    case object `2xlarge` extends InstanceType(i2)
    case object `4xlarge` extends InstanceType(i2)
    case object `8xlarge` extends InstanceType(i2)
  }
  case object d2 extends Family with CurrentGeneration {
    case object   xlarge  extends InstanceType(d2)
    case object `2xlarge` extends InstanceType(d2)
    case object `4xlarge` extends InstanceType(d2)
    case object `8xlarge` extends InstanceType(d2)
  }

  /* #### Accelerated computing */
  case object p2 extends Family with CurrentGeneration {
    case object    xlarge  extends InstanceType(p2)
    case object  `8xlarge` extends InstanceType(p2)
    case object `16xlarge` extends InstanceType(p2)
  }
  case object g2 extends Family with CurrentGeneration {
    case object `2xlarge` extends InstanceType(g2)
    case object `8xlarge` extends InstanceType(g2)
  }


  /* ### Previous Generation Instances */

  /* #### General purpose */
  case object m1 extends Family with PreviousGeneration {
    case object small  extends InstanceType(m1)
    case object medium extends InstanceType(m1)
    case object  large extends InstanceType(m1)
    case object xlarge extends InstanceType(m1)
  }

  /* #### Compute optimized */
  case object c1 extends Family with PreviousGeneration {
    case object medium extends InstanceType(c1)
    case object xlarge extends InstanceType(c1)
  }
  case object cc2 extends Family with PreviousGeneration {
    case object `8xlarge` extends InstanceType(cc2)
  }

  /* #### Memory optimized */
  case object m2 extends Family with PreviousGeneration {
    case object   xlarge  extends InstanceType(m2)
    case object `2xlarge` extends InstanceType(m2)
    case object `4xlarge` extends InstanceType(m2)
  }
  case object cr1 extends Family with PreviousGeneration {
    case object `8xlarge` extends InstanceType(cr1)
  }

  /* #### Storage optimized */
  case object hi1 extends Family with PreviousGeneration {
    case object `4xlarge` extends InstanceType(hi1)
  }
  case object hs1 extends Family with PreviousGeneration {
    case object `8xlarge` extends InstanceType(hs1)
  }

  /* #### GPU instances */
  case object cg1 extends Family with PreviousGeneration {
    case object `4xlarge` extends InstanceType(cg1)
  }

  /* #### Micro instances */
  case object t1 extends Family with PreviousGeneration {
    case object micro extends InstanceType(t1)
  }

}

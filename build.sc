import mill._
import mill.scalalib.publish._
import scalalib._
import scalafmt._
import coursier.maven.MavenRepository

// learned from https://github.com/OpenXiangShan/fudian/blob/main/build.sc
val defaultVersions = Map(
  "chisel" -> ("org.chipsalliance", "7.3.0", false),
  "chisel-plugin" -> ("org.chipsalliance", "7.3.0", true),
  "scalatest" -> ("org.scalatest", "3.2.10", false),
)

val commonScalaVersion = "2.13.17"

def getVersion(dep: String) = {
  val (org, ver, cross) = defaultVersions(dep)
  val version = sys.env.getOrElse(dep + "Version", ver)
  if (cross)
    ivy"$org:::$dep:$version"
  else
    ivy"$org::$dep:$version"
}

trait CommonModule extends ScalaModule {
  def scalaVersion = commonScalaVersion

  override def scalacOptions =
    Seq("-deprecation", "-feature", "-language:reflectiveCalls")
}

object ps2
    extends CommonModule
    with ScalafmtModule {
  override def ivyDeps = super.ivyDeps() ++ Agg(
    getVersion("chisel"),
  )

  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    getVersion("chisel-plugin")
  )

  object test extends ScalaTests with TestModule.ScalaTest {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      getVersion("scalatest")
    )
  }

}

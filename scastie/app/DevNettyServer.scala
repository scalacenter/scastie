import java.io.File
import play.api.{Play, DefaultApplication, Mode}
import play.core.ApplicationProvider
import scala.util.{Try, Properties}

/**
 */
object DevNettyServer extends App {
  new NettyServer(Mode.Dev)
}

object ProdNettyServer extends App {
  new NettyServer(Mode.Prod)
}

class NettyServer(mode: Mode.Value) {
  if (Properties.propIsSet("config.file"))
    System.clearProperty("config.resource")

  "-Dfile.encoding=utf-8 -Djava.net.preferIPv4Stack=true -Dlogger.resource=logback-test.xml"
    .split(" ")
    .foreach { prop =>
      prop.split("=") match {
        case Array(name, value) => System.setProperty(name.drop(2), value)
      }
    }

  new play.core.server.NettyServer(
    new StaticApplication(new File(System.getProperty("user.dir")), mode),
    Option(System.getProperty("http.port"))
      .map(Integer.parseInt)
      .orElse(Some(9000)),
    Option(System.getProperty("https.port")).map(Integer.parseInt),
    Option(System.getProperty("http.address")).getOrElse("0.0.0.0"),
    mode
  )
}

class StaticApplication(applicationPath: File, mode: Mode.Value)
    extends ApplicationProvider {
  val application = new DefaultApplication(applicationPath,
                                           this.getClass.getClassLoader,
                                           None,
                                           mode)

  Play.start(application)

  def get  = Try(application)
  def path = applicationPath
}

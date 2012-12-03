import java.io.File
import play.core.server.NettyServer
import play.core.StaticApplication

/**
  */
object DevNettyServer extends App {
  "-Dfile.encoding=utf-8 -Djava.net.preferIPv4Stack=true -Dlogger.resource=logback-test.xml"
      .split(" ").foreach { prop =>
    prop.split("=") match {
      case Array(name, value) => System.setProperty(name.drop(2), value)
    }
  }
  new NettyServer(
    new StaticApplication(new File(System.getProperty("user.dir"))),
    Option(System.getProperty("http.port")).map(Integer.parseInt(_)).getOrElse(9000),
    Option(System.getProperty("https.port")).map(Integer.parseInt(_)),
    Option(System.getProperty("http.address")).getOrElse("0.0.0.0")
  )
}

import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import java.net.URL

object BrowserStack {
  val username = "";
  val key = "";
  val browserStackUrl =
    s"https://$username:$key@hub-cloud.browserstack.com/wd/hub"

  val caps = new DesiredCapabilities()
  // ...

  val driver = new RemoteWebDriver(new URL(browserStackUrl), caps)
  // ...
  driver.quit()
}

// export CHROME_DRIVER = /path/to/chromedriver
// export CHROMIUM = /path/to/chromium
// import org.openqa.selenium._
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}

trait BrowserSetup {
  // private val host = "https://scastie.scala-lang.org"
  private val host = "http://localhost:8080"

  sys.env.get("CHROME_DRIVER").foreach(chromeDriverPath =>
    System.setProperty("webdriver.chrome.driver", chromeDriverPath)
  )

  private val chromeOptions = new ChromeOptions()
  sys.env.get("CHROMIUM").foreach(chromiumPath =>
    chromeOptions.setBinary(chromiumPath)
  )

  val driver = new ChromeDriver(chromeOptions)

  def goToPage(path: String): Unit = {
    driver.get(host + path)
  }
}
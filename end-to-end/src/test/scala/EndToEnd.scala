import org.scalatest._

// import collection.JavaConverters._
import org.openqa.selenium.By

class EndToEnd extends FunSuite with BrowserSetup with BoundingBoxHelpers {
  test("end-to-end") {
    goToPage("/")

    val welcomeModal = driver.findElementById("welcome-modal")
    welcomeModal.findElement(new By.ByClassName("modal-close")).click()

    driver.findElement(By.xpath("//*[@title='Show help Menu']")).click()

    val help = driver.findElementById("long-help")
    val modal = help.findElement(new By.ByClassName("modal-window"))

    val window = driver.manage.window

    modal.isInside(window)

    driver.quit
  }
}
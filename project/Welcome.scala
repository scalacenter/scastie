import sbtwelcome._

object Welcome {

    val logo = 
        """| ____                _   _  
           |/ ___|  ___ __ _ ___| |_(_) ___ 
           |\___ \ / __/ _` / __| __| |/ _ \
           | ___) | (_| (_| \__ \ |_| |  __/
           ||____/ \___\__,_|___/\__|_|\___|
        """.stripMargin

    val tasks = Seq(
        UsefulTask(
            "startAll",
            "Start all backend services and build the frontend bundle"
        ),
        UsefulTask(
            "startAllProd",
            "Start all backend services and build the frontend for production"
        ),
        UsefulTask(
            "~client/fastLinkJS",
            "Continuously compile the frontend (Scala.js) in fast mode and watch for changes"
        ),
        UsefulTask(
            "test",
            "Run all tests"
        ),
        UsefulTask(
            "<module>/test",
            "Run all tests in a module"
        ),
        UsefulTask(
            "<module>/testOnly [ClassName] -- -z \"test description\"",
            "Run a specific test case in a module."
        )
    )
}
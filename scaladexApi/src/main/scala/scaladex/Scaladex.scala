package scaladex

case class Project(
    logo: String,
    description: String,
    organization: String,
    repository: String,
    artifacts: List[String]
)

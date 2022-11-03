package scastie.metals

import scala.meta.internal.metals._
import scala.meta.pc.SymbolSearchVisitor
import scala.meta.pc.SymbolSearch
import java.net.URI
import scala.meta.pc.ParentSymbols
import java.{util => ju}
import scala.meta.pc.SymbolDocumentation
import org.eclipse.lsp4j.Location

class ScastieSymbolSearch(docs: Docstrings, classpathSearch: ClasspathSearch) extends SymbolSearch {
    override def search(
        query: String,
        buildTargetIdentifier: String,
        visitor: SymbolSearchVisitor
    ): SymbolSearch.Result = {
      classpathSearch.search(WorkspaceSymbolQuery.exact(query), visitor)
    }

    override def searchMethods(
        query: String,
        buildTargetIdentifier: String,
        visitor: SymbolSearchVisitor
    ): SymbolSearch.Result = {
      classpathSearch.search(WorkspaceSymbolQuery.exact(query), visitor)
    }

    def definition(symbol: String, source: URI): ju.List[Location] = {
      ju.Collections.emptyList()
    }

    def definitionSourceToplevels(
        symbol: String,
        sourceUri: URI
    ): ju.List[String] = {
      ju.Collections.emptyList()
    }

    override def documentation(
        symbol: String,
        parents: ParentSymbols
    ): ju.Optional[SymbolDocumentation] =
      docs.documentation(symbol, parents)
  }

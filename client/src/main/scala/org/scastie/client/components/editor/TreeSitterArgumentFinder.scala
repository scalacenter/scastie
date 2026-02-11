package org.scastie.client.components.editor

import typings.webTreeSitter.mod.{Tree, SyntaxNode}
import scala.annotation.tailrec
import scalajs.js

object TreeSitterArgumentFinder {

  case class ArgumentContext(
    openPos: Int,
    closePos: Int,
    activeParam: Int
  )

  def findArgumentContext(tree: Tree, cursorPos: Int): Option[ArgumentContext] = {
    try {
      val node = tree.rootNode.descendantForIndex(cursorPos.toDouble, cursorPos.toDouble)
      findArgumentContextFromNode(node, cursorPos)
    } catch {
      case _: Throwable => None
    }
  }

  private def getSafeParent(node: SyntaxNode): Option[SyntaxNode] = {
    val p = node.parent
    if (p == null || js.isUndefined(p)) None
    else Some(p.asInstanceOf[SyntaxNode])
  }

  @tailrec
  private def findArgumentContextFromNode(node: SyntaxNode, cursorPos: Int): Option[ArgumentContext] = {
    findArgumentList(node) match {
      case Some(argList) if hasNestedArgumentsAtCursor(argList, cursorPos) =>
        getSafeParent(argList) match {
            case Some(parent) => findArgumentContextFromNode(parent, cursorPos)
            case None => None
          }
      
      case Some(argList) =>
        Some(ArgumentContext(
          openPos = argList.startIndex.toInt,
          closePos = argList.endIndex.toInt,
          activeParam = countActiveParameter(argList, cursorPos)
        ))
      
      case None => None
    }
  }

  @tailrec
  private def findArgumentList(node: SyntaxNode): Option[SyntaxNode] = {
    if (node == null || js.isUndefined(node)) {
      None
    } else if (node.`type` == "arguments" || node.`type` == "type_arguments") {
      Some(node)
    } else {
      getSafeParent(node) match {
        case Some(parent) => findArgumentList(parent)
        case None => None
      }
    }
  }
  
  private def hasNestedArgumentsAtCursor(node: SyntaxNode, cursorPos: Int): Boolean = {
    getChildren(node).exists(containsArgumentsAtCursor(_, cursorPos))
  }
  
  private def containsArgumentsAtCursor(node: SyntaxNode, cursorPos: Int): Boolean = {
    val isArgumentNode = node.`type` == "arguments" || node.`type` == "type_arguments"
    val containsCursor = cursorPos >= node.startIndex.toInt && cursorPos <= node.endIndex.toInt
    
    (isArgumentNode && containsCursor) || getChildren(node).exists(containsArgumentsAtCursor(_, cursorPos))
  }

  private def countActiveParameter(argListNode: SyntaxNode, cursorPos: Int): Int = {
    getChildren(argListNode)
      .filterNot(_.`type` == "type_arguments")
      .count { child =>
        child.startIndex.toInt < cursorPos && child.`type` == ","
      }
  }

  private def getChildren(node: SyntaxNode): Iterator[SyntaxNode] = {
    (0 until node.childCount.toInt).iterator
      .map(i => node.child(i.toDouble))
      .filter(child => child != null && !js.isUndefined(child))
  }

  def isCursorInArguments(tree: Tree, cursorPos: Int): Boolean = {
    findArgumentContext(tree, cursorPos).isDefined
  }
}

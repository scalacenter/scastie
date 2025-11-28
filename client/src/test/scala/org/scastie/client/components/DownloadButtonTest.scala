package org.scastie.client.components
+
+import utest._
+import org.scastie.api._
+
+object DownloadButtonTest extends TestSuite {
+
+  private val scalaTargetDefault = ScalaTarget.Scala2
+  private val scalaTargetScalaCli = ScalaTarget.ScalaCli
+
+  def tests = Tests {
+    test("filename derived from snippetId") {
+      val snippetId = SnippetId("foo/bar", None)
+      val props = DownloadButton(snippetId, scalaTargetDefault, "code", "scala")
+      val vdom = DownloadButton.render(props)
+      assert(vdom.toString().contains("foo-bar.scala.zip"))
+    }
+
+    test("normal download uses fullUrl without preventDefault") {
+      val snippetId = SnippetId("abc", None)
+      val props = DownloadButton(snippetId, scalaTargetDefault, "code", "scala")
+      val vdom = DownloadButton.render(props)
+      val s = vdom.toString()
+      assert(s.contains("/api/download/"))
+      assert(!s.contains("href=\"#\""))
+    }
+
+    test("scala-cli flow uses click handler path") {
+      val snippetId = SnippetId("abc", None)
+      val props = DownloadButton(snippetId, scalaTargetScalaCli, "code", "scala")
+      val vdom = DownloadButton.render(props)
+      val s = vdom.toString()
+      assert(s.contains("href=\"#\""))
+    }
+  }
+}

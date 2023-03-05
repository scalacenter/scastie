package com.olegych.scastie.sclirunner;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.messages.CancelParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

interface CancellableServer {
  @JsonNotification("blabla/cancelRequest")
  CompletableFuture<Void> cancel(CancelParams params);
}

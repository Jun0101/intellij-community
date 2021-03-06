// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MessagePool {
  private static final int MAX_POOL_SIZE = 100;
  private static final int MAX_GROUP_SIZE = 20;
  private static final int GROUP_TIME_SPAN_MS = 1000;

  private static class MessagePoolHolder {
    private static final MessagePool ourInstance = new MessagePool();
  }

  public static MessagePool getInstance() {
    return MessagePoolHolder.ourInstance;
  }

  private final List<AbstractMessage> myErrors = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<MessagePoolListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final MessageGrouper myGrouper = new MessageGrouper();

  private MessagePool() { }

  public void addIdeFatalMessage(@NotNull IdeaLoggingEvent event) {
    Object data = event.getData();
    LogMessage message = data instanceof LogMessage ? (LogMessage)data : new LogMessage(event);
    if (myErrors.size() < MAX_POOL_SIZE) {
      myGrouper.addToGroup(message);
    }
    else if (myErrors.size() == MAX_POOL_SIZE) {
      String msg = DiagnosticBundle.message("error.monitor.too.many.errors");
      myGrouper.addToGroup(new LogMessage(new LoggingEvent(msg, LogManager.getRootLogger(), Level.ERROR, null, new TooManyErrorsException())));
    }
  }

  public List<AbstractMessage> getFatalErrors(boolean includeReadMessages, boolean includeSubmittedMessages) {
    List<AbstractMessage> result = new ArrayList<>();
    for (AbstractMessage message : myErrors) {
      if ((!message.isRead() && !message.isSubmitted()) ||
          (message.isRead() && includeReadMessages) ||
          (message.isSubmitted() && includeSubmittedMessages)) {
        result.add(message);
      }
    }
    return result;
  }

  public void clearErrors() {
    for (AbstractMessage message : myErrors) {
      message.setRead(true); // expire notifications
    }
    myErrors.clear();
    notifyPoolCleared();
  }

  public void addListener(MessagePoolListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(MessagePoolListener listener) {
    myListeners.remove(listener);
  }

  private void notifyEntryAdded() {
    myListeners.forEach(MessagePoolListener::newEntryAdded);
  }

  private void notifyPoolCleared() {
    myListeners.forEach(MessagePoolListener::poolCleared);
  }

  private void notifyEntryRead() {
    myListeners.forEach(MessagePoolListener::entryWasRead);
  }

  private class MessageGrouper implements Runnable {
    private final List<AbstractMessage> myMessages = new ArrayList<>();
    private Future<?> myAlarm = CompletableFuture.completedFuture(null);

    @Override
    public void run() {
      synchronized (myMessages) {
        if (myMessages.size() > 0) {
          post();
        }
      }
    }

    private void post() {
      AbstractMessage message = myMessages.size() == 1 ? myMessages.get(0) : new GroupedLogMessage(new ArrayList<>(myMessages));
      message.setOnReadCallback(() -> notifyEntryRead());
      myMessages.clear();
      myErrors.add(message);
      notifyEntryAdded();
    }

    private void addToGroup(@NotNull AbstractMessage message) {
      synchronized (myMessages) {
        myMessages.add(message);
        if (myMessages.size() >= MAX_GROUP_SIZE) {
          post();
        }
        else {
          myAlarm.cancel(false);
          myAlarm = AppExecutorUtil.getAppScheduledExecutorService().schedule(this, GROUP_TIME_SPAN_MS, TimeUnit.MILLISECONDS);
        }
      }
    }
  }

  public static class TooManyErrorsException extends Exception {
    private TooManyErrorsException() {
      super(DiagnosticBundle.message("error.monitor.too.many.errors"));
    }
  }
}
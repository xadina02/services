/*
 * Copyright (C) 2014 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.services.webkitservice.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.opendatakit.consts.WebkitServerConsts;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.webkitserver.service.IWebkitServerInterface;

import java.io.File;
import java.io.IOException;

import fi.iki.elonen.SimpleWebServer;

public class OdkWebkitServerService extends Service {

  private static final String LOGTAG = OdkWebkitServerService.class.getSimpleName();
  /**
   * change to true expression if you want to debug this webkit service
   */
  private static void possiblyWaitForWebkitServerServiceDebugger() {
    if ( false ) {
      android.os.Debug.waitForDebugger();
      int len = "for setting breakpoint".length();
    }
  }

  private SimpleWebServer server = null;
  private volatile Thread webServer = null;
  private WebkitServiceInterface servInterface;

  @Override
  public void onCreate() {
    super.onCreate();
    servInterface = new WebkitServiceInterface();

    webServer = new Thread(null, new Runnable() {
      @Override
      public void run() {
        Thread mySelf = Thread.currentThread();
        int retryCount = 0;
        for (; webServer == mySelf;) {
          startServer();
          try {
            retryCount++;
            Thread.sleep(1000);
            if (retryCount % 60 == 0) {
              Log.v(LOGTAG, "Sync.Thread.WebServer -- waking to confirm webserver is working");
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        stopServer();
      }
    }, "WebServer");
    webServer.start();
  }

  @Override
  public IBinder onBind(Intent intent) {
    possiblyWaitForWebkitServerServiceDebugger();
    return servInterface;
  }

  @Override
  public void onDestroy() {
    Thread tmpThread = webServer;
    webServer = null;
    tmpThread.interrupt();
    try {
      // give it time to drain...
      Thread.sleep(200);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    Log.i(LOGTAG, "onTerminate");
    super.onDestroy();
  }

  private synchronized void startServer() {
    if (server == null || !server.isAlive()) {
      stopServer();
      SimpleWebServer testing = new SimpleWebServer(WebkitServerConsts.HOSTNAME, WebkitServerConsts.PORT, new File(ODKFileUtils.getOdkFolder()).getAbsoluteFile(), false);
      //SimpleWebServer testing = new SimpleWebServer();
      try {
        testing.start();
        server = testing;
      } catch (IOException e) {
        Log.v("Sync.Thread.WebServer", "Exception: " + e.toString());
      }
    }
  }

  private synchronized void stopServer() {
    if (server != null) {
      try {
        server.stop();
      } catch (Exception e) {
        // ignore...
      }
      server = null;
    }
  }

  private class WebkitServiceInterface extends IWebkitServerInterface.Stub {

    @Override
    public boolean restart() throws RemoteException {
      stopServer();
      startServer();
      return true;
    }

  }

}

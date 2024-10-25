package org.telegram.ui.Components;

import android.app.IntentService;
import android.content.Intent;
import android.os.Environment;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.castserver.webserver.SimpleWebServer;
import org.telegram.ui.PhotoViewer;

public class WebService extends IntentService {

    public WebService() {
        super("blank");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        SimpleWebServer.stopServer();
        super.onStart(intent, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            /** Running a server on Internal storage.
             *
             * I know the method {@link Environment#getExternalStorageDirectory} is deprecated
             * but it is needed to start the server in the required path.
             */

            SimpleWebServer.runServer(new String[]{
                    "-h",
                    PhotoViewer.getInstance().deviceIp,
                    "-p 8080",
                    "-d",
                    Environment.getExternalStorageDirectory().getAbsolutePath()
            });
            FileLog.d("Service Started on " + PhotoViewer.getInstance().deviceIp + ":8080");
        } catch (Exception e) {
            FileLog.e("Error: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        SimpleWebServer.stopServer();
        FileLog.d("Service destroyed");
        super.onDestroy();
    }
}
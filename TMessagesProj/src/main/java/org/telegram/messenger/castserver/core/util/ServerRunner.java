package org.telegram.messenger.castserver.core.util;

/*
 * #%L
 * NanoHttpd-Webserver
 * %%
 * Copyright (C) 2012 - 2015 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

import org.telegram.messenger.castserver.core.protocols.http.NanoHTTPD;

public class ServerRunner {

    /**
     * logger to log to.
     */
    private static final Logger LOG = Logger.getLogger(ServerRunner.class.getName());
    private static CountDownLatch downLatch;

    public static void executeInstance(NanoHTTPD server) {
        if (downLatch != null) {
            LOG.log(Level.INFO, "Server is already started.\n");
            return;
        }
        try {
            downLatch = new CountDownLatch(1);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException ioe) {
            LOG.log(Level.SEVERE, "Couldn't start server:\n" + ioe);
            System.exit(-1);
        }

        try {
            LOG.log(Level.INFO, "Server started.\n");
            downLatch.await();
        } catch (InterruptedException ignore) {
        } finally {
            LOG.log(Level.INFO, "Server stopped.\n");
            server.stop();
        }
//
//        System.out.println("Server started, Hit Enter to stop.\n");
//
//        try {
//            System.in.read();
//        } catch (Throwable ignored) {
//        }
//
//
//        System.out.println("Server stopped.\n");
    }

    public static void stopServer() {
        if (downLatch == null) {
            LOG.log(Level.SEVERE, "Server not started");
            return;
        }
        downLatch.countDown();
        downLatch = null;
    }

    public static <T extends NanoHTTPD> void run(Class<T> serverClass) {
        try {
            executeInstance(serverClass.newInstance());
        } catch (Exception e) {
            ServerRunner.LOG.log(Level.SEVERE, "Could not create server", e);
        }
    }
}

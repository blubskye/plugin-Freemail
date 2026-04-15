/*
 * IMAPListener.java
 * This file is part of Freemail
 * Copyright (C) 2006,2007,2008 Dave Baker
 * Copyright (C) 2007 Alexander Lehmann
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.freenetproject.freemail.imap;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.IOException;

import org.freenetproject.freemail.AccountManager;
import org.freenetproject.freemail.ServerListener;
import org.freenetproject.freemail.config.ConfigClient;
import org.freenetproject.freemail.config.Configurator;
import org.freenetproject.freemail.utils.Logger;


public class IMAPListener extends ServerListener implements Runnable, ConfigClient {
	private static final int LISTENPORT = 4143;
	// H3: Hard cap on simultaneous IMAP connections from local mail clients.
	private static final int MAX_IMAP_CONNECTIONS = 20;
	private String bindaddress;
	private int bindport;
	private final AccountManager accountManager;

	public IMAPListener(AccountManager accMgr, Configurator cfg) {
		accountManager = accMgr;
		cfg.register(Configurator.IMAP_BIND_ADDRESS, this, "127.0.0.1");
		cfg.register(Configurator.IMAP_BIND_PORT, this, Integer.toString(LISTENPORT));
	}

	@Override
	public void setConfigProp(String key, String val) {
		if(key.equalsIgnoreCase(Configurator.IMAP_BIND_ADDRESS)) {
			this.bindaddress = val;
		} else if(key.equalsIgnoreCase(Configurator.IMAP_BIND_PORT)) {
			this.bindport = Integer.parseInt(val);
		}
	}

	@Override
	public void run() {
		try {
			this.realrun();
		} catch (IOException ioe) {
			Logger.error(this, "Error in IMAP server - "+ioe.getMessage(), ioe);
		}
	}

	public void realrun() throws IOException {
		InetAddress bindAddr = InetAddress.getByName(this.bindaddress);
		// C5: Same non-loopback cleartext credential warning as SMTP.
		if(!bindAddr.isLoopbackAddress()) {
			Logger.error(this, "IMAP server is bound to non-loopback address " +
				bindaddress + ". Credentials are transmitted in cleartext — " +
				"restrict to 127.0.0.1 unless you have an external TLS proxy.");
		}
		sock = new ServerSocket(this.bindport, 10, bindAddr);
		sock.setSoTimeout(60000);
		while(!sock.isClosed()) {
			try {
				// H3: Reap first so the live count is accurate.
				reapHandlers();
				if(countActiveHandlers() >= MAX_IMAP_CONNECTIONS) {
					Socket refused = sock.accept();
					refused.getOutputStream().write(
						"* BYE Server too busy — try again later\r\n".getBytes("UTF-8"));
					refused.getOutputStream().flush();
					refused.close();
					continue;
				}
				Socket clientSocket = sock.accept();
				IMAPHandler newcli = new IMAPHandler(accountManager, clientSocket);
				Thread newthread = new Thread(newcli, "Freemail IMAP Handler for " + clientSocket.getInetAddress());
				newthread.setDaemon(true);
				newthread.start();
				addHandler(newcli, newthread);
			} catch (SocketTimeoutException ste) {

			} catch (IOException ioe) {

			}
		}
	}
}

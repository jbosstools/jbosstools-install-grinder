/*******************************************************************************
 * Copyright (c) 2012-2013 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.tests.installation;

import org.eclipse.swtbot.eclipse.finder.SWTBotEclipseTestCase;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author mistria
 */
public class CheckForUpdatesTest extends SWTBotEclipseTestCase {

	private static int installationTimeout = 60 * 60000;

	@BeforeClass
	public static void setUpBeforeClass() {
		String timeoutPropertyValue = System.getProperty(InstallTest.INSTALLATION_TIMEOUT_IN_MINUTES_PROPERTY);
		if (timeoutPropertyValue != null) {
			installationTimeout = Integer.parseInt(timeoutPropertyValue) * 60000;
		}
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		if (this.bot.activeView().getTitle().equals("Welcome")) {
			this.bot.viewByTitle("Welcome").close();
		}
	}

	@Test
	public void testCheckForUpdates() throws Exception {
		bot.menu("Help").menu("Check for Updates").click();
		bot.shell("Contacting Software Sites");
		this.bot.waitWhile(new ICondition() {
			@Override
			public boolean test() throws Exception {
				return bot.activeShell().getText().equals("Contacting Software Sites");
			}

			@Override
			public void init(SWTBot bot) {
			}

			@Override
			public String getFailureMessage() {
				return "Blocking while calculating deps";
			}
		}, installationTimeout);
		bot.button("Next >").click();
		InstallTest.continueInstall(bot, "Updating Software");
	}
}

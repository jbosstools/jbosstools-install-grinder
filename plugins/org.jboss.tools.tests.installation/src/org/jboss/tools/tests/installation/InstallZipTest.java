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
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is a bot scenario which performs install through p2 UI
 * using the zip repository.
 *
 * @author Pavol Srna
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class InstallZipTest extends SWTBotEclipseTestCase {

	/**
	 * System property expected to receive URL of a p2 ZIP repo to install
	 */
	public static final String ZIP_PROPERTY = "UPDATE_SITE_ZIP";

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
		try{
			this.bot.viewByTitle("Welcome").close();
		} catch (WidgetNotFoundException e){
			//no welcome screen open
		}
	}

	@Test
	public void testInstall() throws Exception {
		String zip = System.getProperty("ZIP");
		Assert.assertNotNull("No ZIP specified, set ZIP system property first", zip);

		installFromZip(zip);
	}

	private void installFromZip(String zip) {
		this.bot.menu("Help").menu("Install New Software...").click();
		this.bot.shell("Install").bot().button("Add...").click();
		this.bot.shell("Add Repository").activate().setFocus();
		this.bot.text(1).setText("jar:file:" + zip + "!/");
		this.bot.button("OK").click();
		this.bot.shell("Install").activate().setFocus();
		this.bot.waitWhile(new ICondition() {

			@Override
			public boolean test() throws Exception {
				return bot.tree().getAllItems()[0].getText().startsWith("Pending...");
			}

			@Override
			public void init(SWTBot bot) {
			}

			@Override
			public String getFailureMessage() {
				return "Could not see categories in tree";
			}
		});

		try{

			SWTWorkbenchBot myBot = new SWTWorkbenchBot();
			SWTBotShell errorShell = myBot.shell("Error Contacting Site");
			errorShell.activate();

			StringBuilder message = new StringBuilder();
			message.append("Could not install from: " + zip);
			message.append("\n");
			message.append(errorShell.bot().label(1).getText());

			errorShell.bot().button("No").click();

			fail(message.toString());

		}catch(WidgetNotFoundException ex){
			//do nothing
		}


		this.bot.button("Select All").click();

		this.bot.button("Next >").click();
		this.bot.waitUntil(new ICondition() {
			@Override
			public boolean test() throws Exception {
				return bot.button("Cancel").isEnabled();
			}

			@Override
			public void init(SWTBot bot) {
			}

			@Override
			public String getFailureMessage() {
				return "Blocking while calculating deps";
			}
		}, installationTimeout);
		this.bot.button("Next >").click();
		try {
			InstallTest.continueInstall(bot, "Installing Software");
		} catch (InstallFailureException ex) {
			StringBuilder message = new StringBuilder();
			message.append("Could not install from: " + zip);
			message.append("\n");
			message.append(ex.getMessage());

			fail(message.toString());
		}

	}





}

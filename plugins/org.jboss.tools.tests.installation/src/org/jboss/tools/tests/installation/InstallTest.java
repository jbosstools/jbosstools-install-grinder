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
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is a bot scenario which performs install through p2 UI.
 * It takes as input a p2 repository URL configured in the UPDATE_SITE
 * system property. By default all features will be installed.
 * If IUs system property is specified - only selected IUs
 * will be installed. IUs system property is a comma separeted string
 * of IU names. For example: "Abridged JBoss Tools 4.0,Hibernate Tools"
 *
 * @author Mickael Istria
 * @author Pavol Srna
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class InstallTest extends SWTBotEclipseTestCase {

	/**
	 * System property expected to receive URL of a p2 repo to install
	 * It the input of the scenario.
	 */
	public static final String UPDATE_SITE_PROPERTY = "UPDATE_SITE";
	public static final String INSTALLATION_TIMEOUT_IN_MINUTES_PROPERTY = "INSTALLATION_TIMEOUT_IN_MINUTES";

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
		String site = System.getProperty("UPDATE_SITE");
		Assert.assertNotNull("No site specified, set UPDATE_SITE system property first", site);

		String IUs = System.getProperty("IUs");//optional property to install only selected IUs

		installFromSite(site, IUs);
	}

	private void installFromSite(String site, String selectedIUs) {
		this.bot.menu("Help").menu("Install New Software...").click();
		this.bot.shell("Install").bot().button("Add...").click();
		this.bot.shell("Add Repository").activate().setFocus();
		this.bot.text(1).setText(site);
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
		}, installationTimeout);

		if(selectedIUs != null){
			//select IUs to install
			for(String iu : selectedIUs.split(",")){
				assertFalse("Unit: \"" + iu + "\" NOT FOUND!", !checkIU(iu));
			}

		} else {
			this.bot.button("Select All").click();
		}

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
			continueInstall(bot);
		} catch (InstallFailureException ex) {
			StringBuilder message = new StringBuilder();
			message.append("Could not install from: " + site);
			message.append("\n");
			message.append(ex.getMessage());

			fail(message.toString());
		}

	}


	public static void continueInstall(final SWTWorkbenchBot bot) throws InstallFailureException {
		continueInstall(bot, "Installing Software");
	}


	public static void continueInstall(final SWTWorkbenchBot bot, final String shellTitle) throws InstallFailureException {
		try {
			bot.radio(0).click();
			bot.button("Finish").click();
			// wait for Security pop-up, or install finished.
			
			bot.waitUntil(new ICondition() {

				@Override
				public boolean test() throws Exception {
					return bot.activeShell().getText().matches("Security Warning|Software Updates");
				}

				@Override
				public void init(SWTBot bot) {
				}

				@Override
				public String getFailureMessage() {
					return null;
				}
			}, installationTimeout);
			if (bot.activeShell().getText().equals("Security Warning")) {
				bot.button("OK").click();
				System.err.println("OK clicked");
				bot.waitUntil(new ICondition() {
					@Override
					public boolean test() throws Exception {
						try {
							boolean stillOpen = bot.shell(shellTitle).isOpen();
							System.err.println("still open? " + stillOpen);
							return !stillOpen;
						} catch (WidgetNotFoundException ex) {
							System.err.println("no shell");
							// Shell already closed
							return true;
						}
					}

					@Override
					public void init(SWTBot bot) {
					}

					@Override
					public String getFailureMessage() {
						return null;
					}
				}, installationTimeout); // 15 more minutes
			}
			SWTBot restartShellBot = bot.shell("Software Updates").bot();
			// Don't restart in test, test executor will do it.
			try {
				// Eclipse 4.2 => "No"
				restartShellBot.button("No").click();
			} catch (WidgetNotFoundException ex) {
				// Eclipse 3.7.x => "Not now"
				restartShellBot.button("Not Now").click();
			}
		} catch (Exception ex) {

			String installDesc = bot.text().getText();
			if (installDesc == null || installDesc.isEmpty()) {
				throw new RuntimeException("Internal error", ex);
			}
			throw new InstallFailureException(installDesc);
		}
	}


	/**
	 * Checks IU (Category, or Feature) in a tree
	 * @param iu to be checked
	 * @return true if checked
	 *
	 * @author Pavol Srna
	 */
	private boolean checkIU(String iu){

		boolean checked = false;
		for(SWTBotTreeItem node : bot.tree().getAllItems()){
			//traverse through all categories
			if(node.getText().equals(iu)){
				node.check();
				checked = true;
				break;
			}else{
				//expand category
				node.expand();
				for(SWTBotTreeItem i : node.getItems()){
					//traverse through category features
					if(i.getText().equals(iu)){
						i.check();
						checked = true;
						break;
					}
				}
			}
		}
		return checked;
	}

}

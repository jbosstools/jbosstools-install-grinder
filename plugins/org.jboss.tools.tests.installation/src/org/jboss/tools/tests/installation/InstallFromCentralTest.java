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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.swtbot.eclipse.finder.SWTBotEclipseTestCase;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotMultiPageEditor;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCheckBox;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is a bot scenario which performs install through JBoss Central.
 *
 * @author Mickael Istria
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class InstallFromCentralTest extends SWTBotEclipseTestCase {

	private static final String EXCLUDED_CONNECTORS_PROPERTY = "org.jboss.tools.tests.installFromCentral.excludeConnectors";
	private static final String EXCLUDED_CONNECTORS_SEPARATOR = ",";
	private static int installationTimeout = 60 * 60000;
	
	/**
	 * This class MUST run in UI Thread and can be used to retrieve data
	 * @author mistria
	 *
	 */
	class DataRetriever implements Runnable {
		private Widget widget;
		private String dataKey;
		private Object data;
		
		public DataRetriever(Widget widget, String dataKey) {
			this.widget = widget;
			this.dataKey = dataKey;
		}
		
		@Override
		public void run() {
			if (this.dataKey == null) {
				this.data = widget.getData();
			} else {
				this.data = widget.getData(this.dataKey);
			}
		}
		
		public Object getResult() {
			return this.data;
		}
	}
	
	private Set<String> excludedConnectors;

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
		this.excludedConnectors = new HashSet<String>();
		String propertyValue = System.getProperty(EXCLUDED_CONNECTORS_PROPERTY);
		if (propertyValue != null) {
			this.excludedConnectors.addAll(Arrays.asList(propertyValue.split(EXCLUDED_CONNECTORS_SEPARATOR)));
		}
		try{
			this.bot.viewByTitle("Welcome").close();
		} catch (WidgetNotFoundException e){
			//no welcome screen open
		}
	}

	@Test
	public void testInstall() throws Exception {
		SWTBotMenu helpMenu = this.bot.menu("Help");
		helpMenu.menu("JBoss Central").click();
		SWTBotMultiPageEditor centralEditor = (SWTBotMultiPageEditor) this.bot.multipageEditorByTitle("JBoss Central");
		centralEditor.show();
		centralEditor.activatePage("Software/Update");
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return bot.checkBox("Show Installed").isEnabled();
			}
			
			@Override
			public String getFailureMessage() {
				return "Could not load catalog";
			}
		}, installationTimeout);
		try {
			int i = 0;
			SWTBotCheckBox check = null;
			while ((check = this.bot.checkBox(i)) != null) {
				if (check.getText() == null || !check.getText().contains("Show Installed")) {
					DataRetriever dataRetriever = new DataRetriever(check.widget, "connectorId");
					Display.getDefault().syncExec(dataRetriever);
					if (dataRetriever.getResult() != null && !this.excludedConnectors.contains(dataRetriever.getResult())) {
						check.click();
					}
				}
				i++;
			}
		} catch (WidgetNotFoundException ex) {
			// last checkbox
		} catch (IndexOutOfBoundsException ex ) {
			// last checkbox
		}
		bot.button("Install").click();
		this.bot.waitUntil(new ICondition() {
			@Override
			public boolean test() throws Exception {
				return bot.activeShell().getText().equals("Install") || bot.activeShell().getText().equals("Problem Occured");
			}

			@Override
			public void init(SWTBot bot) {
			}

			@Override
			public String getFailureMessage() {
				return "Blocking while calculating deps";
			}
		}, installationTimeout); // 15 minutes timeout
		if (this.bot.activeShell().getText().equals("Problem Occured")) {
			String reason = this.bot.text().getText();
			Assert.fail("Could not install Central content from " + System.getProperty("org.jboss.tools.central.discovery") + "\n" + reason);
		}
		this.bot.button("Next >").click();
		InstallTest.continueInstall(bot);
	}

}

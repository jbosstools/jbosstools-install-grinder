/******************************************************************************* 
 * Copyright (c) 2012 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 *     Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/

package org.jboss.tools.tests.installation;


import junit.framework.Assert;

import org.eclipse.swtbot.eclipse.finder.SWTBotEclipseTestCase;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.waits.ICondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is a bot scenario which performs adding sites to the 
 * eclipse available software sites. It doesn't install anything
 * from the site. 
 * @author Pavol Srna
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class AddSiteTest extends SWTBotEclipseTestCase {

	private static int installationTimeout = 60 * 60000;
	
	@Test
	public void testAddSite() throws Exception {
		String addSite = System.getProperty("ADDSITE");
		Assert.assertNotNull("No ADDSITE specified, set ADDSITE system property first", addSite);
		String[] sites = addSite.split(",");
		
		for(String site : sites){
			addSite(site);
		}
	}

	
	private void addSite(String site) {
		
		this.bot.menu("Help").menu("Install New Software...").click();
		this.bot.shell("Install").bot().button("Add...").click();
		this.bot.shell("Add Repository").activate().setFocus();
		
		if(site.endsWith(".zip"))
			this.bot.text(1).setText("jar:file:" + site + "!/");
		else
			this.bot.text(1).setText(site);
		
		this.bot.button("OK").click();
		
		this.bot.shell("Install").activate().setFocus();
		this.bot.waitWhile (new ICondition() {
			
			@Override
			public boolean test() throws Exception {
				if(!bot.activeShell().getText().equals("Install"))
					return false;
				else
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
		
		try{
			
			SWTWorkbenchBot myBot = new SWTWorkbenchBot();
			SWTBotShell errorShell = myBot.shell("Error Contacting Site");
			errorShell.activate();
			
			StringBuilder message = new StringBuilder();
			message.append("Error with site: " + site);
			message.append("\n");
			message.append(errorShell.bot().label(1).getText());
			
			errorShell.bot().button("No").click();
			
			fail(message.toString());
			
		}catch(WidgetNotFoundException ex){
			//do nothing
		}
		this.bot.shell("Install").close();
	}
}

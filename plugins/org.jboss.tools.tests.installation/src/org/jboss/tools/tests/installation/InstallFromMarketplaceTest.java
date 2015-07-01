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

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.allOf;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.withMnemonic;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.withStyle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swtbot.eclipse.finder.SWTBotEclipseTestCase;
import org.eclipse.swtbot.eclipse.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.widgets.AbstractSWTBotControl;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This is a bot scenario which performs install from Marketplace.
 *
 * @author Andrej Podhradsky (apodhrad@redhat.com)
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class InstallFromMarketplaceTest extends SWTBotEclipseTestCase {

	public static final String MARKETPLACE_LABEL_PROPERTY = "MARKETPLACE_LABEL";

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
		try {
			bot.viewByTitle("Welcome").close();
		} catch (WidgetNotFoundException e) {
			// no welcome screen open
		}
	}

	@After
	public void closeAllShells() throws Exception {
		bot.closeAllShells();
	}

	@Test
	public void testInstall() throws Exception {
		String marketplaceLabel = System.getProperty(MARKETPLACE_LABEL_PROPERTY);
		assertNotNull("You have to specify property '" + MARKETPLACE_LABEL_PROPERTY + "'", marketplaceLabel);

		bot.menu("Help").menu("Eclipse Marketplace...").click();
		bot.shell("Eclipse Marketplace").activate();
		bot.waitUntil(Conditions.widgetIsEnabled(bot.textWithLabel("Find:")), installationTimeout);
		bot.textWithLabel("Find:").setText(marketplaceLabel);
		bot.button("Go").click();
		bot.waitUntil(Conditions.widgetIsEnabled(bot.textWithLabel("Find:")), installationTimeout);

		discoveryItem(marketplaceLabel).button("Install").click();

		bot.shell("Eclipse Marketplace").activate();
		bot.waitUntil(Conditions.widgetIsEnabled(bot.button("Confirm >")), installationTimeout);
		bot.button("Confirm >").click();
		bot.shell("Eclipse Marketplace").activate();

		InstallTest.continueInstall(bot, "Installing Software");
	}

	private SWTBotDiscoveryItem discoveryItem(String label) {
		final String className = "org.eclipse.epp.internal.mpc.ui.wizards.DiscoveryItem";
		final Label swtLabel = bot.label(label).widget;
		Composite discoverItem = UIThreadRunnable.syncExec(new Result<Composite>() {

			@Override
			public Composite run() {
				Composite parent;
				Control control = swtLabel;
				do {
					parent = control.getParent();
					control = parent;
				} while (parent != null && !parent.getClass().getName().equals(className));
				return parent;
			}
		});
		if (discoverItem == null) {
			throw new WidgetNotFoundException("Cannot find discovery item '" + label + "'");
		}
		return new SWTBotDiscoveryItem(discoverItem);
	}

	private class SWTBotDiscoveryItem extends AbstractSWTBotControl<Composite> {

		public SWTBotDiscoveryItem(Composite composite) throws WidgetNotFoundException {
			super(composite);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public SWTBotButton button(String mnemonicText) {
			Matcher matcher = allOf(widgetOfType(Button.class), withMnemonic(mnemonicText),
					withStyle(SWT.PUSH, "SWT.PUSH"));
			return new SWTBotButton((Button) bot.widget(matcher, widget));
		}
	}
}

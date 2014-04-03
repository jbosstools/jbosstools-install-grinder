import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.Get;
import groovy.util.AntBuilder;
import java.text.SimpleDateFormat;

void usage() {
    println """
-------------------------------------------------------------
Script to test installation
usage: groovy.sh testInstall.groovy <eclipse_home> <file_containing_list_of_sites|repository_url|CHECK_FOR_UPDATES>(;)*
  <eclipse_home>: an eclipse installation will be performed on
  <file_containing_list_of_sites> a file containing a list of p2-friendly URLs of repositories
                                  separated by spaces or line breaks
  <repository_url>: URL of a p2 repo to install from
  CHECK_FOR_UPDATES: will trigger the check for updates
--------------------------------------------------------------
usage for installing selected units: groovy.sh -DIUs=iu1,iu2,... testInstall.groovy <eclipse_home> <repository_url>
--------------------------------------------------------------
Commandline flags:
	-DIUs=iu1,iu2,...
	-DADDSITE=http://updatesite1/,http://updatesite2/,http://updatesite3/,...
	-DINSTALLATION_TIMEOUT_IN_MINUTES=30
	-DSWTBOT_UPDATE_SITE=http://download.jboss.org/jbosstools/updates/requirements/swtbot/2.1.1.201307101628/
	-DJVM=/qa/tools/opt/jdk1.7.0_last/bin/java
--------------------------------------------------------------

	"""
}


// Takes a repo or a directory.xml URL single parameter
void installUrl(String repoUrl, File eclipseHome, String product) {

	if (repoUrl.endsWith(".xml")) {
		installFromCentral(repoUrl, eclipseHome, product);
	} else if (repoUrl.endsWith(".zip")) {
		installZipRepo(repoUrl, eclipseHome, product);
	} else if (repoUrl.equals("CHECK_FOR_UPDATES")) {
		checkForUpdates(eclipseHome, product);
	} else {
		installRepo(repoUrl, eclipseHome, product);
	}
}


void installZipRepo(String repoUrl, File eclipseHome, String productName){

	Collection<String> additionalVMArgs = [];

	if(new File(repoUrl).isFile()){
		//local file, no need to download
		additionalVMArgs += "-DZIP=" + repoUrl;
	}else{
        // wget zip file
		println("DOWNLOAD FIRST: " + repoUrl);

	    String zipName = repoUrl.substring(repoUrl.lastIndexOf("/")+1);
	    File zip = new File("./" + zipName);
       	new AntBuilder().get(
           	src: repoUrl,
            dest: zip.getAbsolutePath());

		additionalVMArgs += "-DZIP=" + zip.getAbsolutePath();
	}

    // run install zip test
    println("Installing content from " + repoUrl);
	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.InstallZipTest");

}

//site = comma separated list of sites to be added
void addSite(String site, File eclipseHome, String productName){
	Collection<String> additionalVMArgs = [];
	additionalVMArgs += "-DADDSITE=" + site;
	println("Add Software sites (no installation):" + site);
	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.AddSiteTest");
}

//Takes repo URL as single parameter
void installRepo(String repoUrl, File eclipseHome, String productName) {
	println("Installing content from " + repoUrl);
	Collection<String> additionalVMArgs = [];
	additionalVMArgs += "-DUPDATE_SITE=" + repoUrl;
    String ius = System.properties['IUs'];
    if(ius != null){
        ius=ius.replaceAll("\"","");
        println("Units to install:" + ius);
        additionalVMArgs += " -DIUs=\"" + ius + "\"";
    }
	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.InstallTest");
}

void runSWTBotInstallRoutine(File eclipseHome, String productName, Collection<String> additionalVMArgs, String testClassName) {
	String report = "TEST-install-" + new SimpleDateFormat("yyyyMMddh-hmm").format(new Date()) + ".xml";

	// Invoke tests
	Java proc = new org.apache.tools.ant.taskdefs.Java();
	proc.setFork(true);
	proc.setDir(eclipseHome);

	Collection<String> vmArgs = [];
	vmArgs.addAll(additionalVMArgs);
	String osName = System.properties['os.name'].toLowerCase();
	if(osName.contains("mac")){
		vmArgs += "-XstartOnFirstThread";
	}
	vmArgs += "-Dorg.eclipse.swtbot.search.timeout=300000";
	vmArgs += "-Dusage_reporting_enabled=false";
	if (System.getProperty("INSTALLATION_TIMEOUT_IN_MINUTES") != null) {
		vmArgs += "-DINSTALLATION_TIMEOUT_IN_MINUTES=" + System.getProperty("INSTALLATION_TIMEOUT_IN_MINUTES");
	}
	vmArgs += "-Xms256M"; vmArgs += "-Xmx768M"; vmArgs += "-XX:MaxPermSize=512M";

	String debugPort = System.getProperty("debugPort");
	if (debugPort != null) {
	   vmArgs += "-agentlib:jdwp=transport=dt_socket,address=localhost:" + debugPort + ",server=y,suspend=y";
	}

	proc.setJvmargs(vmArgs.join(" "));

	proc.setJar(new File(eclipseHome, "plugins").listFiles().find {it.getName().startsWith("org.eclipse.equinox.launcher_") && it.getName().endsWith(".jar")} );
	Collection<String> args = [];
	args += "-application"; args += "org.eclipse.swtbot.eclipse.junit.headless.swtbottestapplication";
	args += "-testApplication"; args += "org.eclipse.ui.ide.workbench";
	args += "-product"; args += productName;
	args += "-data"; args += "workspace";
	args += "formatter=org.apache.tools.ant.taskdefs.optional.junit.XMLJUnitResultFormatter," + report;
	args += "formatter=org.apache.tools.ant.taskdefs.optional.junit.PlainJUnitResultFormatter";
	args += "-testPluginName"; args += "org.jboss.tools.tests.installation";
	args += "-className"; args += testClassName;
	args += "-consoleLog";
	args += "-debug";
	proc.setArgs(args.join(" "));
	proc.init();
	int returnCode = proc.executeJava();
	if (returnCode != 0) {
		println("An error occured. Most probably because of wrong configuration of environment.");
		System.exit(1);
	}

	File report_file = new File(eclipseHome.getAbsolutePath() + "/" + report);

	report_file.eachLine { line ->
		if (line.contains("failures")) {
			if (line.contains("errors=\"0\" failures=\"0\"")) {
				println("Install SUCCESS. Read " + report + " for more details.");
				return;
			} else {
				println("Failed to install. Read " + report + " for details and see screenshots/");
				System.exit(1);
			}
		}
	}
}

// Takes a Central directory.xml URL single parameter, and assumes the accomanying update site is one path segment up:
// for http://download.jboss.org/jbosstools/discovery/development/4.1.0.Alpha2/jbosstools-directory.xml
// use http://download.jboss.org/jbosstools/discovery/development/4.1.0.Alpha2/
// for http://download.jboss.org/jbosstools/discovery/nightly/core/trunk/jbosstools-directory.xml
// use http://download.jboss.org/jbosstools/discovery/nightly/core/trunk/
void installFromCentral(String discoveryDirectoryUrl, File eclipseHome, String productName) {
	println("Installing content from " + discoveryDirectoryUrl);
  String discoverySiteUrl=discoveryDirectoryUrl.substring(0,discoveryDirectoryUrl.lastIndexOf("/")+1);
  Collection<String >additionalVMArgs = [];
  additionalVMArgs.add("-Djboss.discovery.directory.url=" + discoveryDirectoryUrl);
  additionalVMArgs.add("-Djboss.discovery.site.url=" + discoverySiteUrl);

	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.InstallFromCentralTest");
}

// Check for updates
void checkForUpdates(File eclipseHome, String productName) {
	println("Check for updates");
	runSWTBotInstallRoutine(eclipseHome, productName, "", "org.jboss.tools.tests.installation.CheckForUpdatesTest");
}


//// Launcher script

if (args.length < 2) {
	usage();
	System.exit(2);
}

File eclipseHome = new File(args[0]);

if (!eclipseHome.isDirectory()) {
	usage();
	System.exit(2);
}

if(System.properties['IUs'] && (args.length != 2)){
    println("Installing selected/filtered Units is supported only from one repository_url!");
    usage();
    System.exit(2);
}


println "Preparing tests, installing framework";

String companionRepoLocation = System.getProperty("companionRepo");
if (companionRepoLocation == null) {
	companionRepoLocation = getClass().protectionDomain.codeSource.location.path;
	companionRepoLocation = companionRepoLocation[0..companionRepoLocation.lastIndexOf('/')];
	companionRepoLocation += "repository";
	println companionRepoLocation;
}
// Install test framework
Java proc = new org.apache.tools.ant.taskdefs.Java();

// JBIDE-16304: support option to pass in alternate JVM path, eg., /qa/tools/opt/jdk1.7.0_last/bin/java or /qa/tools/opt/jdk1.6.0_last/bin/java
// If not set fall back to default, which is proc.setJvm("java") so whatever's on the current PATH will be used (probably JDK 6)
// In Jenkins job config, set in Groovy script file > Advanced... > Properties > 
//    JVM=/qa/tools/opt/jdk1.7.0_last/bin/java
String JVM = System.properties['JVM'];
if(JVM != null){
		JVM=JVM.replaceAll("\"","");
		proc.setJvm(JVM);
}

proc.setDir(eclipseHome);
proc.setFork(true);
proc.setJar(new File(eclipseHome, "plugins").listFiles().find({it.getName().startsWith("org.eclipse.equinox.launcher_") && it.getName().endsWith(".jar")}).getAbsoluteFile());
// JBIDE-16269: parameterize the URL from which SWTBot is installed, eg., with 
// In Jenkins job config, set in Groovy script file > Advanced... > Properties > 
//    SWTBOT_UPDATE_SITE=http://download.jboss.org/jbosstools/updates/requirements/swtbot/2.1.1.201307101628/
String SWTBOT_UPDATE_SITE = System.properties['SWTBOT_UPDATE_SITE'];
if(SWTBOT_UPDATE_SITE != null){
		SWTBOT_UPDATE_SITE=SWTBOT_UPDATE_SITE.replaceAll("\"","");
}
else
{
		SWTBOT_UPDATE_SITE="http://download.eclipse.org/technology/swtbot/releases/latest/";
}
println("Install SWTBot from:" + SWTBOT_UPDATE_SITE);
proc.setArgs("-application org.eclipse.equinox.p2.director " +
		"-repository " + SWTBOT_UPDATE_SITE + "," +
		"file:///" + companionRepoLocation + " " +
		"-installIU org.jboss.tools.tests.installation " +
		"-installIU org.eclipse.swtbot.eclipse.test.junit.feature.group " +
		"-consolelog");
proc.init();
int returnCode = proc.executeJava();
if (returnCode != 0) {
	System.exit(3);
}

File iniFile;
String osName = System.properties['os.name'].toLowerCase();
if(osName.contains("mac")){
	//Mac OSX
	iniFile = new File(eclipseHome.getAbsolutePath() + "/Eclipse.app/Contents/MacOS").listFiles().find({it.getName().endsWith(".ini")});
	if(iniFile == null){
	iniFile = new File(eclipseHome.getAbsolutePath() + "/JBoss Developer Studio.app/Contents/MacOS").listFiles().find({it.getName().endsWith(".ini")});
	}
}else{
	iniFile = eclipseHome.listFiles().find({it.getName().endsWith(".ini")});
}
iniLines = iniFile.readLines();
targetIndex = iniLines.findIndexOf {line -> line.startsWith("-product") };
String productName = iniLines[targetIndex + 1];
println ("Product is: " + productName);

// Add software sites (no installation)
String newSite = System.properties['ADDSITE'];
if(newSite != null)
	addSite(newSite, eclipseHome, productName);

// End of 'Add software sites (no installatio)

def sites = [];
args[1..-1].each {
	// Allow semicolon-separated lists as well
	sites.addAll it.split(";")
}
sites.each {
	if (new File(it).isFile()) {

		if(it.endsWith(".zip")){
			installZipRepo(it, eclipseHome, productName);
		}else{
			new File(it).eachLine({ line ->
				installUrl(line, eclipseHome, productName);
			});
		}
	} else {
		installUrl(it, eclipseHome, productName);
	}
}
System.exit(0)

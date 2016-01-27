import groovy.util.AntBuilder
import org.apache.tools.ant.taskdefs.Get

// this script can install many different variations of and Eclipse installation

// interim EPP bundles - don't use the same suffix convention as normal Eclipse and released EPP bundles
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="https://hudson.eclipse.org/packaging/job/mars.epp-tycho-build/325/artifact/org.eclipse.epp.packages/archive/20160121-0844_eclipse-jee-mars-2-RC1-linux.gtk.x86_64.tar.gz" getAndInstallEclipse.groovy
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="https://hudson.eclipse.org/packaging/job/mars.epp-tycho-build/325/artifact/org.eclipse.epp.packages/archive/20160121-0844_eclipse-jee-mars-2-RC1-linux.gtk.x86.tar.gz" getAndInstallEclipse.groovy

// released EPP bundles (both URL formats supported)
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="http://www.eclipse.org/downloads/download.php?r=1&file=/technology/epp/downloads/release/neon/M4/eclipse-jee-neon-M4-linux-gtk-x86_64.tar.gz" getAndInstallEclipse.groovy
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="http://download.eclipse.org/technology/epp/downloads/release/mars/1/eclipse-jee-mars-1-linux-gtk.tar.gz" getAndInstallEclipse.groovy
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="http://download.eclipse.org/technology/epp/downloads/release/mars/R/eclipse-jee-mars-R-linux-gtk.tar.gz" getAndInstallEclipse.groovy

// Eclipse Platform binaries and SDKs
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="http://www.eclipse.org/downloads/download.php?r=1&file=/eclipse/downloads/drops4/S-4.6M4-201512092300/eclipse-platform-4.6M4-linux-gtk-x86_64.tar.gz" getAndInstallEclipse.groovy
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="http://www.eclipse.org/downloads/download.php?r=1&file=/eclipse/downloads/drops4/M-4.5.2RC1-201601131000/eclipse-platform-4.5.2RC1-linux-gtk-x86_64.tar.gz" getAndInstallEclipse.groovy
// groovy -DeclipseCacheDirectory=/tmp/ -DdownloadURL="http://www.eclipse.org/downloads/download.php?r=1&file=/eclipse/downloads/drops4/R-4.5-201506032000/eclipse-SDK-4.5-linux-gtk.tar.gz" getAndInstallEclipse.groovy

Properties allProperties = new Properties()
allProperties.putAll System.properties
args.each {
	String[] split = it.split("=")
	if (split.length == 2) {
		allProperties.put(split[0], split[1])
	}
}

File eclipseCacheDirectory = allProperties['eclipseCacheDirectory'] != null ? new File(allProperties['eclipseCacheDirectory']) : new File(".")

// default when no downloadURL specified is luna SR2 jee EPP bundle
String eclipseFlavour = allProperties['eclipseFlavour'] ?: "jee"
String releaseTrainId = allProperties['releaseTrainId'] ?: "luna"
String versionLabel = allProperties['versionLabel'] ?: "SR2"
String mirrorSite = allProperties['mirror'] ?: "http://www.eclipse.org/downloads/download.php?r=1&file=/technology/epp/downloads/release" // or use http://download.eclipse.org/technology/epp/downloads/release/
String eclipseBundleVersion = allProperties['eclipseBundleVersion']
if (eclipseBundleVersion != null) {
	String[] split = eclipseBundleVersion.split("\\.")
	if (split.length == 2) {
		releaseTrainId = split[0]
		versionLabel = split[1]
	}
}

if (!eclipseCacheDirectory.canWrite()) {
	println ("WARNING: You can't write to " + eclipseCacheDirectory)
	println ("WARNING: Script may fail in case of cache miss")
}

if (new File("eclipse").isDirectory()) {
	new AntBuilder().delete( dir: new File("eclipse").getAbsolutePath() )
}
if (new File("Eclipse.app").isDirectory()) {
	new AntBuilder().delete( dir: new File("Eclipse.app").getAbsolutePath() )
}

String osLabel = System.properties['os.name'].toLowerCase()
String fileExtension = null
if (osLabel.contains("windows")) {
	osLabel = "win32"
	fileExtension = "zip"
} else if (osLabel.contains("linux")) {
	osLabel = allProperties['downloadURL'] && allProperties['downloadURL'].contains(".x86.") ? "linux.gtk" : "linux-gtk"
	fileExtension = "tar.gz"
} else if (osLabel.contains("mac")) {
	osLabel = "macosx-cocoa"
	fileExtension = "tar.gz"
}
String archLabel = System.properties['os.arch'].contains("64") ? (allProperties['downloadURL'] && allProperties['downloadURL'].contains(".x86.") ? ".x86_64" : "-x86_64") : ""

// if downloadURL is set, just use that value for eclipseArchive and downloadURL
String eclipseArchive = allProperties['downloadURL'] != null ? allProperties['downloadURL'].replaceAll(".+/([^/]+\\."+fileExtension+")",'$1') : "eclipse-" + eclipseFlavour + "-" + releaseTrainId + "-" + (versionLabel == "R" && releaseTrainId[0] < 'k' ? "" : (versionLabel + "-")) + osLabel + archLabel + "." + fileExtension
String downloadURL = allProperties['downloadURL'] ?: mirrorSite + "/" + releaseTrainId + "/" + versionLabel +"/" + eclipseArchive

// Support jobs that run on both 32- and 64-bit OS: ensure we're using 64-bit on 64-bit OS for both filename conventions (.x86_64 and -x86-64)
if (System.properties['os.arch'].contains("64")) {
	if (downloadURL.contains(".x86.")) {
		downloadURL = downloadURL.replaceAll(".x86.",".")
		eclipseArchive = eclipseArchive.replaceAll(".x86.",".")
	}

	//given eclipse-jee-mars-1-RC3-linux-gtk.tar.gz, convert to eclipse-jee-mars-1-RC3-linux-gtk-x86_64.tar.gz
	//println(osLabel+"."+fileExtension+", "+osLabel+archLabel+"."+fileExtension)
	downloadURL = downloadURL.replaceAll(osLabel+"."+fileExtension, osLabel+archLabel+"."+fileExtension)
	eclipseArchive = eclipseArchive.replaceAll(osLabel+"."+fileExtension, osLabel+archLabel+"."+fileExtension)
}
println("Downloading: " + eclipseArchive + " from " + downloadURL + " (" + archLabel + ")")

File cachedFile = new File(eclipseCacheDirectory, eclipseArchive)
if (!cachedFile.isFile()) {
	new AntBuilder().get(
		src: downloadURL,
		dest: cachedFile)
}
// Unzip/untar
if (fileExtension.equals("zip")) {
	new AntBuilder().unzip(
		src: cachedFile.getAbsolutePath(),
		dest: new File(".").getAbsolutePath())
} else if (fileExtension.equals("tar.gz")) {
	new AntBuilder().untar(
		overwrite:true,
		compression:"gzip",
		src: cachedFile.getAbsolutePath(),
		dest: new File(".").getAbsolutePath())
}

String executablePath = null
if (new File("eclipse").isDirectory()) {
	executablePath = "eclipse"
} else if (new File("Eclipse.app").isDirectory()) {
	executablePath = "Eclipse.app/Contents/MacOS"
}

// mark eclipse executable
new AntBuilder().chmod(dir:new File(".").getAbsolutePath() + File.separator + executablePath, perm:'+x', includes:"eclipse, eclipse.exe");

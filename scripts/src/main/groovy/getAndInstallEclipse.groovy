import groovy.util.AntBuilder
import org.apache.tools.ant.taskdefs.Get;

Properties allProperties = new Properties();
allProperties.putAll System.properties;
args.each {
	String[] split = it.split("=")
	if (split.length == 2) {
		allProperties.put(split[0], split[1])
	}
}

File eclipseCacheDirectory = allProperties['eclipseCacheDirectory'] != null ? new File(allProperties['eclipseCacheDirectory']) : new File(".");
String eclipseFlavour = allProperties['eclipseFlavour'] ?: "jee";
String releaseTrainId = allProperties['releaseTrainId'] ?: "luna";
String versionLabel = allProperties['versionLabel'] ?: "SR2";
String mirrorSite = allProperties['mirror'] ?: "http://www.eclipse.org/downloads/download.php?r=1&file=/technology/epp/downloads/release"; // or use http://download.eclipse.org/technology/epp/downloads/release/
String eclipseBundleVersion = allProperties['eclipseBundleVersion']
if (eclipseBundleVersion != null) {
	String[] split = eclipseBundleVersion.split("\\.")
	if (split.length == 2) {
		releaseTrainId = split[0]
		versionLabel = split[1]
	}
}

if (!eclipseCacheDirectory.canWrite()) {
	println ("WARNING: You can't write to " + eclipseCacheDirectory);
	println ("WARNING: Script may fail in case of cache miss");
}

if (new File("eclipse").isDirectory()) {
	new AntBuilder().delete( dir: new File("eclipse").getAbsolutePath() );
}

String osLabel = System.properties['os.name'].toLowerCase();
String fileExtension = null;
if (osLabel.contains("windows")) {
	osLabel = "win32";
	fileExtension = "zip";
} else if (osLabel.contains("linux")) {
	osLabel = "linux-gtk";
	fileExtension = "tar.gz";
} else if (osLabel.contains("mac")) {
	osLabel = "macosx-cocoa";
	fileExtension = "tar.gz";
}
String archLabel = System.properties['os.arch'].contains("64") ? "-x86_64" : "";

// if downloadURL is set, just use that value for eclipseArchive and downloadURL
String eclipseArchive = allProperties['downloadURL'] != null ? allProperties['downloadURL'].replaceAll(".+/(eclipse[^/]+\\."+fileExtension+")",'$1') : "eclipse-" + eclipseFlavour + "-" + releaseTrainId + "-" + (versionLabel == "R" && releaseTrainId[0] < 'k' ? "" : (versionLabel + "-")) + osLabel + archLabel + "." + fileExtension;
String downloadURL = allProperties['downloadURL'] ?: mirrorSite + "/" + releaseTrainId + "/" + versionLabel +"/" + eclipseArchive;

// Support jobs that run on both 32- and 64-bit OS: ensure we're using 64-bit on 64-bit OS
if (System.properties['os.arch'].contains("64")) {
	//given eclipse-jee-mars-1-RC3-linux-gtk.tar.gz, convert to eclipse-jee-mars-1-RC3-linux-gtk-x86_64.tar.gz
	//println(osLabel+"."+fileExtension+", "+osLabel+archLabel+"."+fileExtension);
	downloadURL = downloadURL.replaceAll(osLabel+"."+fileExtension, osLabel+archLabel+"."+fileExtension);
	eclipseArchive = eclipseArchive.replaceAll(osLabel+"."+fileExtension, osLabel+archLabel+"."+fileExtension);
}
println("Downloading: " + eclipseArchive + " from " + downloadURL + " (" + archLabel.replace("-","") + ")");

File cachedFile = new File(eclipseCacheDirectory, eclipseArchive);
if (!cachedFile.isFile()) {
	new AntBuilder().get(
		src: downloadURL,
		dest: cachedFile);
}
// Unzip
if (fileExtension.equals("zip")) {
	new AntBuilder().unzip(
		src: cachedFile.getAbsolutePath(),
		dest: new File(".").getAbsolutePath());
} else if (fileExtension.equals("tar.gz")) {
	File tarFile = new File(eclipseCacheDirectory, cachedFile.getName()[0..- (".gz".length() + 1)]);
	if (!tarFile.isFile()) {
		new AntBuilder().gunzip(src: cachedFile.getAbsolutePath(), dest: eclipseCacheDirectory.getAbsolutePath());
	}
	new AntBuilder().untar(
		src: tarFile.getAbsolutePath(),
		dest: new File(".").getAbsolutePath());
}

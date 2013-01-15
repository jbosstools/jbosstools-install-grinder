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
String eclipseFlavour = allProperties['eclipseFlavour'] != null ? allProperties['eclipseFlavour'] : "jee";
String releaseTrainId = allProperties['releaseTrainId'] != null ? allProperties['releaseTrainId'] : "juno";
String versionLabel = allProperties['versionLabel'] != null ? allProperties['versionLabel'] : "SR1";
String mirrorSite = allProperties['mirror'] != null ? allProperties['mirror'] : "http://www.eclipse.org/downloads/download.php?r=1&file=/technology/epp/downloads/release";


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

String eclipseArchive = "eclipse-" + eclipseFlavour + "-" + releaseTrainId + "-" + (versionLabel == "R" ? "" : (versionLabel + "-")) + osLabel + archLabel + "." + fileExtension;
String downloadURL = mirrorSite + "/" + releaseTrainId + "/" + versionLabel +"/" + eclipseArchive;
println("Will retrieve " + eclipseArchive + " from mirror site: " + mirrorSite);

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

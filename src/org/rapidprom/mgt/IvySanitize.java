package org.rapidprom.mgt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * This management script allows you to sanitize the RapidProM lib folder. The
 * script will simply fetch the maximum version of each library, and update
 * every reference in an ivy file to such library. It will remove obsolete
 * libraries. It is relatively hard coded to the (current) structure of
 * rapidprom libraries, and, does not work for the thirdparty libraries!
 * 
 * It takes one argument:
 * 
 * 1. the location of the packages git-based prom (!) folder
 * 
 * @author svzelst
 *
 */
public class IvySanitize {

	private final File libFolder;
	private final String ivyRegex;

	private static final String GIT_FOLDER_NAME = ".git";
	private static final String THIRDPARTY_FOLDER_NAME = "thirdparty";
	private static final String DEPENDENCY_PATTERN = "<dependency";
	private static final String XML_CLOSE = "/>";
	private static final String DEPENDENCY_CLOSE = "</dependency>";
	private static final String ORGANISATION_TAG = "org=\"org.rapidprom\"";
	private static final String NAME_TAG = "name=";
	private static final String REVISION_TAG_START = "rev=\"";
	private static final String PROM_LIB_FOLDER_CONNECTOR = "-";

	public IvySanitize(final File libFolder) {
		this.libFolder = libFolder;
		ivyRegex = "ivy-.*.xml";

	}

	public void apply() {
		assert (libFolder.isDirectory());
		sanitize();
	}

	private void sanitize() {
		Collection<File> obsoleteVersions = new HashSet<>();
		for (File promLibFolder : libFolder.listFiles()) {
			if (promLibFolder.isDirectory() && !promLibFolder.getName().equals(GIT_FOLDER_NAME)) {
				String[] promLibFolderNameArr = getLibraryNameAndVersion(promLibFolder.getName());
				if (isMaxVersion(promLibFolderNameArr[0], promLibFolderNameArr[1])) {
					System.out.println("found maximum library, library: " + promLibFolderNameArr[0] + " version: "
							+ promLibFolderNameArr[1]);
					for (File candidateFolder : libFolder.listFiles()) {
						for (File f : candidateFolder.listFiles()) {
							if (f.getName().matches(ivyRegex)) {
								try {
									String content = FileUtils.readFileToString(f);
									int start = content.indexOf(DEPENDENCY_PATTERN);
									while (start >= 0) {
										int close = getIndexOfClosestEndTag(content, start);
										assert (close < Integer.MAX_VALUE && close > start);
										String dep = content.substring(start, close);
										if (dependencyPointsToLibrary(dep, promLibFolderNameArr[0])) {
											System.out.println("updating " + f.getName());
											content = updateDependency(content, dep, promLibFolderNameArr[1]);
										}
										start = content.indexOf(DEPENDENCY_PATTERN, start + 1);
									}
									IOUtils.write(content, new FileOutputStream(f));
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
					}

				} else {
					// not the *max*
					System.out.println("found obsolete library, library: " + promLibFolderNameArr[0] + " version: "
							+ promLibFolderNameArr[1]);
					obsoleteVersions.add(promLibFolder);
				}
			}
		}
		for (File f : obsoleteVersions) {
			try {
				FileUtils.deleteDirectory(f);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String[] getLibraryNameAndVersion(String folderName) {
		int lastDash = folderName.lastIndexOf("-");
		String[] res = new String[2];
		res[0] = folderName.substring(0, lastDash);
		res[1] = folderName.substring(lastDash + 1);
		return res;
	}

	private boolean isMaxVersion(String library, String version) {
		boolean foundMatch = false;
		for (File promLibFolder : libFolder.listFiles()) {
			if (promLibFolder.isDirectory() && !promLibFolder.getName().equals(GIT_FOLDER_NAME)) {
				String[] promLibFolderNameArr = getLibraryNameAndVersion(promLibFolder.getName());
				if (library.equals(promLibFolderNameArr[0])) {
					foundMatch = true;
					String[] checkingVersion = version.split("\\.");
					String[] thisVersion = promLibFolderNameArr[1].split("\\.");
					for (int i = 0; i < checkingVersion.length; i++) {
						if (Integer.parseInt(thisVersion[i]) > Integer.parseInt(checkingVersion[i])) {
							return false;
						}
					}
				} else if (foundMatch)
					return true;
			}
		}
		return true;
	}

	private String updateDependency(final String ivyFileContent, final String dep, final String version) {
		int revStartIndex = dep.indexOf(REVISION_TAG_START);
		int revEndIndex = revStartIndex + REVISION_TAG_START.length() + 1;
		while (!dep.substring(revEndIndex, revEndIndex + 1).equals("\"")) {
			revEndIndex++;
		}
		String oldRev = dep.substring(revStartIndex, revEndIndex + 1);
		String newRev = REVISION_TAG_START + version + "\"";
		String newDep = dep.replace(oldRev, newRev);
		return ivyFileContent.replaceFirst(dep, newDep);
	}

	private int getIndexOfClosestEndTag(String content, int start) {
		int close = content.indexOf(XML_CLOSE, start) >= 0 ? content.indexOf(XML_CLOSE, start) : Integer.MAX_VALUE;

		close = content.indexOf(DEPENDENCY_CLOSE, start) >= 0
				? Math.min(close, content.indexOf(DEPENDENCY_CLOSE, start)) : close;
		return close;
	}

	private boolean dependencyPointsToLibrary(final String dep, final String lib) {
		boolean result = true;
		result &= dep.contains(ORGANISATION_TAG);
		result &= dep.contains(NAME_TAG + "\"" + lib + "\"");
		return result;
	}

	public static void main(String[] args) {
		File libFolder = new File(args[0]);
		System.out.println("Sanitizing " + libFolder);
		IvySanitize update = new IvySanitize(libFolder);
		update.apply();
	}

}


package uk.ac.liv.mzidlib.util;

import java.io.File;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

//The idea is to see whether the underlying file system is one of a
//network kind (NFS, SMB, CIFS, OpenAFS, GlusterFS) and then to copy
//the file to the local scratch space for faster processing. 
//This implementation is primarily NFS (any version), as this is the
//most common version of networked file system, and it is flagged as
//the one even in the presence of other network enabled file system
//through masking.
public class FileHandler {
    private static final String            NFSFileSystemPrefix = "nfs";
    private static final Logger            LOGGER              = Logger.getLogger(FileHandler.class);
    private static final Map<String, Path> filePaths           = new HashMap<>(5);

    private FileHandler() {
    }

    /**
     * Deletes the given file from the local drive.
     *
     * @param fileName The name of the file to be deleted.
     */
    public static void deleteFiles(final String fileName) {
        try {
            if (!filePaths.isEmpty()) {
                Files.deleteIfExists(filePaths.get(fileName));
            }
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error in deleting file(s) : " + e.getMessage());
            }
        }
    }

    /**
     * This method copies the given file to a local drive if the file resides
     * on a network file system (primarily NFS). If the copy operation fails
     * for some reason (insufficient space, path doesn't exist etc), the original
     * behaviour is retained. The copied file will be purged upon JVM shutdown.
     * <p>
     * The calling method should check for null object.
     *
     * @param filePath       as {@link String} the path of the file to be processed
     * @param processLocal   decides whether the file should be copied to local drive
     * @param autoFileDelete when true the locally copied file is marked for deletion on JVM shutdown
     *                       else the file is marked for manual deletion. Refer {@link #deleteFiles(String)}
     * @return The {@link File} object to be processed
     */
    public static File handleFile(final String filePath, final boolean processLocal, final boolean autoFileDelete) {
        File originalFile = new File(filePath);

        try {
            if (processLocal) {
                URI       rootURI      = new URI("file:///");
                Path      rootPath     = Paths.get(rootURI);
                Path      dirPath      = rootPath.resolve(filePath);
                FileStore dirFileStore = Files.getFileStore(dirPath);

                if (dirFileStore.type().contains(NFSFileSystemPrefix)) {
                    File mzidFile = new File(originalFile.getName());

                    Files.copy(originalFile.toPath(), mzidFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    if (autoFileDelete) {
                        mzidFile.deleteOnExit();
                    } else {
                        filePaths.put(mzidFile.getName(), mzidFile.toPath().toAbsolutePath());
                    }

                    return mzidFile;
                }
            }

            return originalFile;
        } catch (URISyntaxException | IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error in copying file : " + filePath + " : " + e.getMessage());
            }

            return originalFile;
        }
    }
}

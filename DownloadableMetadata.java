/**
Didi Jungreisz - 304993553
Barak Gelman - 204038756
**/

/**
 * Describes a file's metadata: URL, file name, size, and which parts already downloaded to disk.
 *
 * The metadata (or at least which parts already downloaded to disk) is constantly stored safely in disk.
 * When constructing a new metadata object, we first check the disk to load existing metadata.
 *
 * CHALLENGE: try to avoid metadata disk footprint of O(n) in the average case
 * HINT: avoid the obvious bitmap solution, and think about ranges...
 */
class DownloadableMetadata {
    // file name for store metadata 
    private final String metadataFilename;
    
    // file name for downloading
    private final String filename;
    
    // URL for downloading
    private final String url;

    /*
    * constructor for DownloadableMetadata
    * @param url
    */
    DownloadableMetadata(String url) {
        this.url = url;
        this.filename = getName(url);
        this.metadataFilename = getMetadataName(filename);
    }

    /*
    * get the meta file name
    * all metadata will be store in this file
    * @param filename
    */
    public static String getMetadataName(String filename) {
        return filename + ".metadata";
    }

    /*
    * get the file name from download URL
    * @param path
    * @return filename
    */
    public static String getName(String path) {
        return path.substring(path.lastIndexOf('/') + 1, path.length());
    }

    /*
    * get file name for downloading
    */
    String getFilename() {
        return filename;
    }

    /*
    * check the completed
    */
    boolean isCompleted() {
        return true;
    }
    
    /*
    * get the URL
    */
    String getUrl() {
        return url;
    }
}

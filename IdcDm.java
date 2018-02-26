/**
Didi Jungreisz - 304993553
Barak Gelman - 204038756
**/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IdcDm {

    /**
     * Receive arguments from the command-line, provide some feedback and start
     * the download.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int numberOfWorkers = 1;
        Long maxBytesPerSecond = (long) 0;

        if (args.length < 1 || args.length > 3) {
            System.err.printf("usage:\n\tjava IdcDm URL [MAX-CONCURRENT-CONNECTIONS] [MAX-DOWNLOAD-LIMIT]\n");
            System.exit(1);
        } else if (args.length >= 2) {
            numberOfWorkers = Integer.parseInt(args[1]);
            if (args.length == 3) {
                maxBytesPerSecond = Long.parseLong(args[2]);
            }
        }

        String url = args[0];

        System.err.printf("Downloading");
        if (numberOfWorkers > 1) {
            System.err.printf(" using %d connections", numberOfWorkers);
        }

        if (maxBytesPerSecond != 0) {
            System.err.printf(" limited to %d Bps", maxBytesPerSecond);
        }
        System.err.printf("...\n");

        DownloadURL(url, numberOfWorkers, maxBytesPerSecond);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            boolean isSuccess() {
                for (int i = 0; i < m_ThreadList.size(); ++i) {
                    if (!m_ThreadList.get(i).isFinished) return false;
                }
                
                return true;
            }
            
            @Override
            public void run() {
                File file = new File(getMetaFileName(url));
                if (file.exists()) file.delete();
                
                if (isSuccess()) {
                    System.out.println("Downloaded 100.0%");
                    System.out.println("Download succeeded");
                    return;
                }
                
                try {
                    file.createNewFile();
                    String metaData = url + "\n" + totalDownloaded + "\n";
                    Files.write(Paths.get(getMetaFileName(url)), metaData.getBytes(), StandardOpenOption.APPEND);
                } catch (IOException ex) {
                    Logger.getLogger(IdcDm.class.getName()).log(Level.SEVERE, null, ex);
                }

                for (int i = 0; i < m_ThreadList.size(); ++i) {
                    m_ThreadList.get(i).m_Thread.interrupt();
                    m_ThreadList.get(i).generateMetaData();
                }
            }
        });
    }

    // Constants for download's state
    public static final int DOWNLOADING = 0;
    public static final int PAUSED = 1;
    public static final int COMPLETED = 2;
    public static final int CANCELLED = 3;
    public static final int ERROR = 4;

    protected static long totalSize = -1;
    protected static long totalDownloaded = -1;
    protected static int m_state = -1;
    protected static String m_strLastModified;
    protected static ArrayList<HTTPRangeGetter> m_ThreadList = new ArrayList<>();
    static double m_lastPercent = 0.0;

    /*
    * create the metadata file name from URL for downloading
    * @param url
    */
    @SuppressWarnings("null")
    public static String getMetaFileName(String url) {
        MessageDigest m = null;
        try {
            m = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(HTTPRangeGetter.class.getName()).log(Level.SEVERE, null, ex);
        }
        byte[] data = url.getBytes();
        m.update(data, 0, data.length);
        BigInteger i = new BigInteger(1, m.digest());
        return String.format("%1$032X", i) + ".metadata";
    }

    /**
     * Increase the downloaded size
     */
    protected synchronized static void downloaded(long value) {
        totalDownloaded += value;
    }

    /**
     * Initiate the file's metadata, and iterate over missing ranges. For each:
     * 1. Setup the Queue, TokenBucket, DownloadableMetadata, FileWriter,
     * RateLimiter, and a pool of HTTPRangeGetters 2. Join the HTTPRangeGetters,
     * send finish marker to the Queue and terminate the TokenBucket 3. Join the
     * FileWriter and RateLimiter
     *
     * Finally, print "Download succeeded/failed" and delete the metadata as
     * needed.
     *
     * @param url URL to download
     * @param numberOfWorkers number of concurrent connections
     * @param maxBytesPerSecond limit on download bytes-per-second
     */
    private static void DownloadURL(String url, int numberOfWorkers, Long maxBytesPerSecond) {
        HttpURLConnection conn = null;
        try {
            // Open connection to URL
            conn = (HttpURLConnection) (new URL(url)).openConnection();
            conn.setConnectTimeout(HTTPRangeGetter.CONNECT_TIMEOUT);
            conn.setReadTimeout(HTTPRangeGetter.READ_TIMEOUT);

            // Connect to server
            conn.connect();
            m_strLastModified = conn.getHeaderField("Last-Modified");

            // Make sure the response code is in the 200 range.
            if (conn.getResponseCode() / 100 != 2) {
                System.err.println("HTTP CONNECTION FAILED!");
            }

            // Check for valid content length.
            int contentLength = conn.getContentLength();
            if (contentLength < 1) {
                System.err.println("HTTP GET LENGTH FAILED!");
            }

            if (totalSize == -1) {
                totalSize = contentLength;
            }

            // check whether we have list of download threads or not, if not -> init download
            if (m_ThreadList.isEmpty()) {
                File metaFile = new File(getMetaFileName(url));
                if (metaFile.exists() && !metaFile.isDirectory()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(metaFile))) {
                        @SuppressWarnings("UnusedAssignment")
                        String line = "";
                        br.readLine();  // read URL
                        totalDownloaded = Long.parseLong(br.readLine()); // read origianl downloaded length
                        while ((line = br.readLine()) != null) {
                            
                            HTTPRangeGetter aThread = new HTTPRangeGetter(url, new Range(Long.parseLong(line.split("-")[0]), new Long(line.split("-")[1])), new TokenBucket(maxBytesPerSecond, HTTPRangeGetter.CHUNK_SIZE * 1024));
                            m_ThreadList.add(aThread);
                        }
                    }
                } else {
                    if (totalSize > HTTPRangeGetter.CHUNK_SIZE) {
                        // downloading size for each thread
                        int partSize = Math.round(((float) totalSize / numberOfWorkers) / HTTPRangeGetter.CHUNK_SIZE) * HTTPRangeGetter.CHUNK_SIZE;

                        // start/end Byte for each thread
                        int startByte = 0;
                        int endByte = partSize - 1;

                        HTTPRangeGetter aThread = new HTTPRangeGetter(url, new Range(new Long(startByte), new Long(endByte)), new TokenBucket(maxBytesPerSecond, HTTPRangeGetter.CHUNK_SIZE * 1024));
                        m_ThreadList.add(aThread);
                        while (endByte < totalSize) {
                            startByte = endByte + 1;
                            endByte += partSize;
                            aThread = new HTTPRangeGetter(url, new Range((long) startByte, (long) endByte), new TokenBucket(maxBytesPerSecond, HTTPRangeGetter.CHUNK_SIZE * 1024));
                            m_ThreadList.add(aThread);
                        }
                    } else {
                        HTTPRangeGetter aThread = new HTTPRangeGetter(url, new Range(new Long(0), new Long(totalSize)), new TokenBucket(maxBytesPerSecond, HTTPRangeGetter.CHUNK_SIZE * 1024 * 1024));
                        m_ThreadList.add(aThread);
                    }
                }

            }

            m_ThreadList.get(0).isFirst = true;
            for (int i = 0; i < m_ThreadList.size(); ++i) {
                m_ThreadList.get(i).download();
            }

            // waiting for all threads to complete
            for (int i = 0; i < m_ThreadList.size(); ++i) {
                m_ThreadList.get(i).waitFinish();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("DOWNLOAD FAILED!");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

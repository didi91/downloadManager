/**
Didi Jungreisz - 304993553
Barak Gelman - 204038756
**/

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A runnable class which downloads a given url. It reads CHUNK_SIZE at a time
 * and writs it into a BlockingQueue. It supports downloading a range of data,
 * and limiting the download rate using a token bucket.
 */
public class HTTPRangeGetter implements Runnable {

    // chunk size, it is final varible with 4096
    static final int CHUNK_SIZE = 4096;

    // connection timeout it is final varible with 500
    // in case there is no response from server, connection request will be terminate
    static final int CONNECT_TIMEOUT = 500;

    // if have no response for read data from server, read request will be terminate
    static final int READ_TIMEOUT = 2000;

    // URL for download, program will be download file from this URL
    private final String url;

    // range for start and end of download
    private final Range range;

    BufferedInputStream in = null;
    RandomAccessFile raf = null;

    // token bucket handler
    // bandwidth will be controled by this class
    private final TokenBucket tokenBucket;
    protected Thread m_Thread = null;
    protected long downloaded = 0;
    int numRead = -1;
    boolean isFinished = false;
    boolean isFirst = false;

    HTTPRangeGetter(
            String url,
            Range range,
            TokenBucket tokenBucket) {
        this.url = url;
        this.range = range;
        this.tokenBucket = tokenBucket;
    }

    /*
     * generate the meta data when interrupt
     */
    @SuppressWarnings("null")
    public void generateMetaData() {
        if (isFinished) {
            return;
        }

        try {
            raf.close();
        } catch (IOException ex) {
            Logger.getLogger(HTTPRangeGetter.class.getName()).log(Level.SEVERE, null, ex);
        }

        String metaFileName = IdcDm.getMetaFileName(url);
        String m_rangeStr = range.getStart() + "-" + range.getEnd() + "\n";

        try {
            Files.write(Paths.get(metaFileName), m_rangeStr.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException ex) {
            Logger.getLogger(HTTPRangeGetter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean downloadRange() throws IOException, InterruptedException {

        try {
            // open Http connection to URL
            HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            // set the range of byte to download
            String byteRange = range.getStart() + "-" + range.getEnd();
            conn.setRequestProperty("Range", "bytes=" + byteRange);
            conn.setRequestProperty("If-Range", IdcDm.m_strLastModified);

            // connect to server
            conn.connect();

            // Make sure the response code is in the 200 range.
            if (conn.getResponseCode() / 100 != 2) {
                return false;
            }

            try {
                // get the input stream
                in = new BufferedInputStream(conn.getInputStream());
            } catch (IOException e) {
                int responseCode = 0;
                try {
                    responseCode = ((HttpURLConnection) conn).getResponseCode();
                } catch (IOException e1) {

                }

                return responseCode == 416;
            }

            // open the output file and seek to the start location
            raf = new RandomAccessFile(DownloadableMetadata.getName(url), "rw");
            raf.seek(range.getStart());

            byte data[] = new byte[CHUNK_SIZE];
            while ((numRead = in.read(data, 0, CHUNK_SIZE)) != -1) {

                if (!tokenBucket.tokensAvailable(numRead)) {
                    tokenBucket.waitForTokens(numRead);
                }

                // write to buffer
                raf.write(data, 0, numRead);

                // increase the startByte for resume later
                range.setStart(range.getStart() + numRead);

                // increase the downloaded size
                IdcDm.downloaded(numRead);

                double percent = Math.round(IdcDm.totalDownloaded * 10000.0 / IdcDm.totalSize) / 100.0;

                if (isFirst && percent < 100.0 && percent > IdcDm.m_lastPercent) {
                    IdcDm.m_lastPercent = percent;
                    System.out.println("Downloaded " + percent + "%");
                }
            }
        } catch (IOException e) {
            return false;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                }
            }

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }

        return true;
    }

    @Override
    @SuppressWarnings("SleepWhileInLoop")
    public void run() {
        try {
            while (this.downloadRange() == false) {
                Thread.sleep(1000);
            }
            isFinished = true;
        } catch (IOException | InterruptedException e) {

        }
    }

    /**
     * Start or resume the download
     */
    public void download() {
        m_Thread = new Thread(this);
        m_Thread.start();
    }

    /**
     * Waiting for the thread to finish
     *
     * @throws InterruptedException
     */
    public void waitFinish() throws InterruptedException {
        m_Thread = new Thread(this);
        m_Thread.join();
    }
}

import java.net.*;
import java.io.*;
import java.util.concurrent.BlockingQueue;

class HTTPRangeDownloader extends Thread {
    private URL url;
    private int id;
    private BlockingQueue<Chunk> queue;
    private Metadata metadata;
    private String displayRanges;
    private String requestRanges;
    final int SLEEP_TIME = 100;                   // Setting thread's sleep duration to 100 nanoseconds when necessary.
    final int TIMEOUT_TIME = 20 * 1000;           // Setting thread's timeout to 20 seconds (connection & read timeout).

    HTTPRangeDownloader(int id, URL url, int offset, int ending, BlockingQueue<Chunk> queue, Metadata metadata) {
        this.id = id;
        this.url = url;
        this.queue = queue;
        this.metadata = metadata;
        this.displayRanges = metadata.getRanges(offset, ending);
        this.requestRanges = MyTools.convertRangesDisplayToRequest(this.displayRanges);
    }

    public void run() {
        System.out.println(this.getDetails());
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        String[] ranges = this.requestRanges.split(", ");
        boolean exceptionOccurred = false;

        // Making a connection to server to download each continuous range in the ranges allocated for thread.
        for (int i = 0; i < ranges.length && !exceptionOccurred; i++) {
            int chunkSize = metadata.getChunkSize();
            int currentOffset = (Integer.parseInt(ranges[i].substring(0, ranges[i].indexOf('-'))) / chunkSize);
            int currentEnding = (Integer.parseInt(ranges[i].substring(ranges[i].indexOf('-') + 1)) / chunkSize);
            int currentChunks = currentEnding - currentOffset + 1;

            try {
                // Reads metadata chunks that haven't been downloaded yet.
                connection = (HttpURLConnection) this.url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_TIME);
                connection.setReadTimeout(TIMEOUT_TIME);

                // Get ranges of bytes from metadata
                connection.setRequestProperty("Range", "bytes=" + ranges[i]);
                connection.connect();
                inputStream = new BufferedInputStream(connection.getInputStream());

                for (int j = 0; j < currentChunks; j++) {
                    int chunkID = currentOffset + j;
                    int byteLength;
                    if (chunkID == (this.metadata.getLength() - 1)) {
                        byteLength = metadata.getLastChunkInBytes();
                    } else {
                        byteLength = metadata.getChunkSize();
                    }
                    byte[] data = new byte[byteLength];

                    inputStream.readNBytes(data, 0, byteLength);

                    Chunk chunk = new Chunk(chunkID, data);
                    while (!queue.add(chunk)) {
                        MyTools.sleep(SLEEP_TIME);
                    }
                }

            } catch (IOException e) {
                exceptionOccurred = true;
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        System.err.println("[" + this.id + "]'s InputStream refuses to close.");
                    }
                }
            }
        }
        if (exceptionOccurred) {
            System.err.println("[" + this.id + "] Stopped running due to timeout");
        } else {
            System.out.println("[" + this.id + "] Finished downloading");
        }
    }

    private String getDetails() {
        String rangeRef = this.displayRanges.contains(",") ? " ranges " : " range ";
        return "[" + this.id + "] Start downloading" + rangeRef + this.displayRanges + " from:\n" + this.url.toString();
    }
}
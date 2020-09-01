import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class IdcDm {

    public static void main(String[] args) {
        final int CHUNK_SIZE = 262144;               // Setting chunk size to 262144 bytes = 256KB.
        final int DEFAULT_CONNECTIONS = 1;           // Setting default number of connections to 1.
        final int SLEEP_TIME = 150;                  // Setting thread sleep time to 150 nanoseconds when necessary.
        URL[] urls;

        /*
         *  Pre-processing
         */

        // Usage
        if (args.length == 0) {
            System.out.println("usage:\n\tjava IdcDm URL|URL-LIST-FILE [MAX-CONCURRENT-CONNECTIONS]");
            return;
        }

        // Reading first argument.
        boolean firstInputIsURL = MyTools.isURL(args[0]);

        // In case a URL was given as the first input
        if (firstInputIsURL) {
            urls = new URL[1];
            urls[0] = MyTools.createURLFromString(args[0]);
            if (urls[0] == null) {
                System.err.println("Given URL is mal-formatted.");
                return;
            }
        }

        // In case a file path was given as the first input
        else {
            urls = MyTools.getURLsFromFile(args[0]);
            if (urls == null) {
                System.err.println("Given file does not exist or a URL in the given file is mal-formatted.");
                System.err.println("Download failed");
                return;
            }
        }

        /*
         * Preparing download accordingly to the following cases:
         *  - File is downloaded from scratch.
         *  - File was partially downloaded before (i.e. broken download).
         */

        // Paths of metadata and temporary downloaded file.
        String filePath = MyTools.getFileNameFromURL(urls[0]);
        String tmpFilePath = filePath + ".tmp";
        String metadataFilePath = filePath + ".metadata.tmp";
        String metadata2FilePath = filePath + ".metadata_copy.tmp";
        boolean success;

        // If file already exists (file with the same name) in current directory then abort.
        File file = new File(filePath);
        if (file.exists()) {
            System.err.println("File with the same name already exists in current directory.");
            System.err.println("Download failed");
            return;
        }

        // Creating initial connection obtaining file's content-length.
        long fileSizeInBytes = MyTools.getFileContentLength(urls[0]);
        if (fileSizeInBytes == -1) {
            System.err.println("No response from server (check server's URL or internet connection).");
            System.err.println("Download failed");
            return;
        }

        int numberOfChunks = (int) Math.ceil(((double) fileSizeInBytes) / CHUNK_SIZE);
        int lastChunkInBytes = (int) (fileSizeInBytes - (CHUNK_SIZE * (numberOfChunks - 1)));
        int remainingChunks;

        // Metadata files and temporary file.
        File tmpFile = new File(tmpFilePath);
        File metadataFile = new File(metadataFilePath);
        Metadata metadata;


        // Creating metadata file if it doesn't exist (and temporary file as well - is deleted and created again)
        if (!metadataFile.exists()) {
            if (!MyTools.fileCreator(metadataFile)) {
                System.err.println("Metadata file refuses to be created.");
                System.err.println("Download failed");
                return;
            }

            // Deleting previous existing .tmp files of the same file for aesthetics.
            if (tmpFile.exists()) {
                if (!tmpFile.delete()) {
                    System.err.println("Previous .tmp file refuses to be deleted.");
                }
            }

            // Creating metadata object(stores a boolean array as long as the number of chunks in the file).
            remainingChunks = numberOfChunks;
            metadata = new Metadata(remainingChunks, CHUNK_SIZE, lastChunkInBytes);

            boolean serialized = MyTools.serializeMetadata(metadata, metadataFilePath);
            if (!serialized) {
                System.err.println("Metadata refuses to be serialized.");
            }
        }

        // In case the metadata file exists.
        else {

            // Extracting information from metadata file.
            if ((metadata = MyTools.deserializeMetadata(metadataFilePath)) == null) {
                System.err.println("Metadata refuses to be de-serialized.");
                System.err.println("Download failed");
                return;
            }

            success = metadata.isDownloadComplete();
            MyTools.closeAndDelete(null, filePath, tmpFile, metadataFile, success);
            if (success) {
                System.out.println("Download succeeded");
                return;
            }
            remainingChunks = metadata.getRemainingChunkNumber();
        }

        // Creating randomAccessFile as the .tmp file
        RandomAccessFile randomAccessFile = MyTools.createRandomAccessFile(tmpFilePath, fileSizeInBytes);
        if (randomAccessFile == null) {
            System.err.println("Could not create .tmp file (RandomAccessFile).");
            System.err.println("Download failed");
            return;
        }

        // Checks if a limit for concurrent connections was inserted.
        // If maximum connections were given this variable will be reassigned.
        int allowedConnections = DEFAULT_CONNECTIONS;     // Afterwards will be changed to given value
        if (args.length > 1) {                            // Checking if there's a second argument given.
            try {
                // Reading second argument.
                allowedConnections = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Second argument is invalid.");
                System.err.println("Download failed");
                return;
            }
        }

        /*
         *  Initializing threads to download the required file.
         */

        int numberOfConnections = Math.min(remainingChunks, allowedConnections);
        int range = (int) ((double) remainingChunks / numberOfConnections);
        int rangeRemainder = (remainingChunks % numberOfConnections);

        // Initializing HTTPRangeDownloader thread pool
        ExecutorService downloaderPool = Executors.newFixedThreadPool(numberOfConnections);

        // Initializing blocking queue.
        BlockingQueue<Chunk> queue = new LinkedBlockingDeque<>();

        if (numberOfConnections > 1) {
            System.out.println("Downloading using " + numberOfConnections + " connections...");
        } else {
            System.out.println("Downloading...");
        }

        // Initializing each HTTPRangeDownloader thread.
        int currentURL, currentOffset, currentRange, currentEnding = -1;
        for (int i = 0; i < numberOfConnections; i++) {
            currentURL = i % urls.length;
            currentOffset = metadata.getNextChunkToDownload(currentEnding);
            currentRange = range;
            if (i == numberOfConnections - 1) {          // Specially for the last thread
                currentRange = range + rangeRemainder;
            }
            currentEnding = metadata.getLastChunkInRange(currentOffset, currentRange);
            HTTPRangeDownloader downloader = new HTTPRangeDownloader((i + 1), urls[currentURL], currentOffset, currentEnding, queue, metadata);
            downloaderPool.execute(downloader);
        }
        downloaderPool.shutdown();

        /* The fileWriter part:
         * Polling the BlockingQueue.
         * Writing to the .tmp file.
         * Updating the metadata.
         */

        // Waiting for queue to fill-up.
        while (queue.isEmpty()) {
            MyTools.sleep(SLEEP_TIME);
            if(downloaderPool.isTerminated()){
                break;
            }
        }
        int downloadStatus = metadata.downloadStatus();
        System.out.println("Downloaded " + downloadStatus + "%");

        while (!metadata.isDownloadComplete() && (!downloaderPool.isTerminated() || !queue.isEmpty())) {
            Chunk currentChunk;


            while (((currentChunk = queue.poll()) == null) && (!downloaderPool.isTerminated() || !queue.isEmpty())) {

                MyTools.sleep(SLEEP_TIME);

                if (downloaderPool.isTerminated() && queue.isEmpty()) {
                    break;
                }
            }
            if (currentChunk == null) {
                break;
            }


            int currentID = currentChunk.getId();
            byte[] currentData = currentChunk.getData();


            // Writing to RandomAccessFile
            try {
                long seekPosition = currentID * CHUNK_SIZE;
                randomAccessFile.seek(seekPosition);
                randomAccessFile.write(currentData);
            } catch (IOException e) {
                System.err.println("Error occurred when writing to .tmp File");
                return;
            }

            // Duplicating metadata;
            metadata.downloadedChunk(currentID);
            if (!MyTools.serializeMetadata(metadata, metadata2FilePath)) {
                System.err.println("Metadata refuses to be serialized into metadata_copy file.");
            }

            // Renaming updated metadata.
            MyTools.rename(metadata2FilePath, metadataFilePath);

            int currentStatus = metadata.downloadStatus();
            if (downloadStatus != currentStatus) {
                System.out.println("Downloaded " + currentStatus + "%");
                downloadStatus = currentStatus;
            }
        }

        // Waiting for thread-pool to download file, and when download completes it shutdowns.
        try {
            if (!downloaderPool.awaitTermination(5, TimeUnit.SECONDS)) {
                downloaderPool.shutdownNow();
            }
        } catch (InterruptedException ignored) {}
        while (!downloaderPool.isTerminated()) {
            MyTools.sleep(SLEEP_TIME);
        }

        success = metadata.isDownloadComplete();
        MyTools.closeAndDelete(randomAccessFile, filePath, tmpFile, metadataFile, success);
        // Closing allocated resources and deleting unnecessary .tmp files.
        if (success) {
            System.out.println("Download succeeded");
        } else {
            System.err.println("Download failed");
        }
    }
}

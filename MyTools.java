import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

class MyTools {

    /* Function validating if a given string is a URL.
     * Input: a string representing a URL.
     * Output: Boolean variable indicating if the given input fits a URL pattern.
     */
    static boolean isURL(String urlString) {
        try {
            URL url = new URL(urlString);
            url.toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /* Function creating URL object from given string representing URL.
     * Input: A string representing a URL.
     * Output: A URL object.
     * Assumptions: - Given string is a well formed URLs.
     */
    static URL createURLFromString(String stringURL) {
        try {
            return new URL(stringURL);
        } catch (MalformedURLException e) {
            return null;
        }
    }


    /* Function extracting URL address from a given file.
     * Input: A string representing the file's path.
     * Output: An array of type URL of all the URLs in the file.
     * Assumptions: - Given file path exists.
     * 			    - All data written in given file are well formed URLs.
     */
    static URL[] getURLsFromFile(String filePath) {
        List<String> urlsList;
        URL[] urls;

        try {
            urlsList = Files.readAllLines(Paths.get(filePath));
        } catch (IOException ignored) {
            return null;
        }
        urls = new URL[urlsList.size()];
        for (int i = 0; i < urlsList.size(); i++) {
            urls[i] = MyTools.createURLFromString(urlsList.get(i));
            if (urls[i] == null) {
                return null;
            }
        }
        return urls;
    }

    /* Function returns the content length of a given URL file.
     * Input: An url.
     * Output: Integer of the length of the file returned by the url.
     * Assumptions: HTTP response of the URL server has a Content-length header.
     */
    static long getFileContentLength(URL url) {
        HttpURLConnection connection = null;
        long contentLength;
        try {
            connection = (HttpURLConnection) url.openConnection();
            contentLength = connection.getContentLength();
            if (contentLength < 0) {     // If file size is not return (unknown) we cannot have concurrent connections.
                return -1;
            }
        } catch (IOException e) {
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return contentLength;
    }


    /* Function returns file name of a given URL.
     * Input: The url.
     * Output: The string corresponding to the file URL.
     * Assumption: The url belongs to a file.
     */
    static String getFileNameFromURL(URL url) {
        String urlString = url.toString();
        return urlString.substring(urlString.lastIndexOf('/') + 1);
    }


    /* Function returns a created file.
     * Input: A file.
     * Output: A boolean indicator implying for the creation of the file.
     */
    static boolean fileCreator(File file) {
        boolean created;
        try {
            created = file.createNewFile();
        } catch (IOException e) {
            return false;
        }
        return created;
    }


    /* Function returns a boolean variable that corresponds to success of serializing a given object to given file path;
     * Input: A metadata object and a file path.
     * Output: Boolean index of serialization status.
     */
    static boolean serializeMetadata(Metadata metadata, String metadataFilePath) {
        FileOutputStream fileOut = null;
        ObjectOutputStream out = null;
        boolean completed = true;
        try {
            fileOut = new FileOutputStream(metadataFilePath);
            out = new ObjectOutputStream(fileOut);
            out.writeObject(metadata);
        } catch (IOException e) {
            completed = false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (fileOut != null) {
                    fileOut.close();
                }
            } catch (IOException e) {
                System.err.println("During serialization, FileOutputStream or ObjectOutputStream would not close.");
                completed = false;
            }
        }
        return completed;
    }


    /* Function de-serializing an object from file path;
     * Input: A file path.
     * Output: The de-serialized object or null if an error occurred.
     */
    static Metadata deserializeMetadata(String metadataFilePath) {
        Metadata metadata;
        FileInputStream fileIn = null;
        ObjectInputStream in = null;
        try {
            fileIn = new FileInputStream(metadataFilePath);
            in = new ObjectInputStream(fileIn);
            metadata = (Metadata) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println(e.toString());
            metadata = null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (fileIn != null) {
                    fileIn.close();
                }
            } catch (IOException e) {
                System.err.println("During deserialization, FileInputStream or ObjectInputStream would not close.");
                metadata = null;
            }
        }
        return metadata;
    }


    /* Function creates a RandomAccessFile.
     * Input: A file path.
     * Output: The created randomAccessFile or null if an error occurred.
     */
    static RandomAccessFile createRandomAccessFile(String tmpFilePath, long sizeOfFileInBytes) {
        RandomAccessFile randomAccessFile;
        try {
            randomAccessFile = new RandomAccessFile(tmpFilePath, "rw");
            randomAccessFile.setLength(sizeOfFileInBytes);
        } catch (IOException e) {
            return null;
        }
        return randomAccessFile;
    }


    /* Function closes open resources, deletes unnecessary files,  and prints necessary comments when program is successfully completed.
     * Input: RandomAccessFile of destination file, filePath of the destination file, tmpFile of the temporary file, and metadataFile for the metadata file.
     */
    static void closeAndDelete(RandomAccessFile randomAccessFile, String filePath, File tmpFile, File metadataFile, boolean downloadCompleted) {
        try {
            if (randomAccessFile != null) {
                randomAccessFile.close();
            }
        } catch (IOException e) {
            System.err.println("RandomAccessFile refuses to close.");
        }
        if (downloadCompleted) {
            File file = new File(filePath);
            if (!tmpFile.renameTo(file)) {
                System.err.println(".tmp file refuses to be renamed.");
            }
            if (!metadataFile.delete()) {
                System.err.println("Metadata file refuses to be deleted");
            }
        }
    }


    /* Function is given a string corresponding the format to display to user and converts it to the format
     * an HTTP connection header demands.
     * Input: Given ranges described in the required UI format.
     * Output: The required corresponding HTTP range request format.
     * Assumption: Given string represent a ranges.
     */
    static String convertRangesDisplayToRequest(String displayRanges) {
        String requestRanges = displayRanges.replaceAll(" - ", "-");
        requestRanges = requestRanges.replaceAll("\\(", "");
        requestRanges = requestRanges.replaceAll("\\)", "");
        return requestRanges;
    }


    /* Function is given nanoseconds to make a thread sleep that amount of time.
     * Input: An integer corresponding to the number of nanoseconds to sleep.
     * In an InterruptedException we do nothing.
     */
    static void sleep(int nanoSeconds) {
        try {
            Thread.sleep(0, nanoSeconds);
        } catch (InterruptedException ignored) {
        }
    }


    /* Function receives a source file name and destination file name and renames the
     * source file's name to the destination file's name.
     * Input : The paths of the source and destination files.
     * Output: A boolean variable that corresponds to the succession of the renaming.
     */
    static boolean rename(String sourcePath, String destinationPath) {
        Path src = Paths.get(sourcePath);
        Path dst = Paths.get(destinationPath);
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException ignored) {
        }
        return false;
    }
}
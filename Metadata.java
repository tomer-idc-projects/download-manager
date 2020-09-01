class Metadata implements java.io.Serializable {
    private boolean[] chunks;
    private int chunkSize;
    private int lastChunkInBytes;

    Metadata(int numberOfChunks, int chunkSize, int lastChunkInBytes) {
        chunks = new boolean[numberOfChunks];     // Boolean Array to represent status of each chunk of file (T: downloaded, F: Missing).
        this.chunkSize = chunkSize;             // Number in bytes each chunk contains (except last chunk).
        this.lastChunkInBytes = lastChunkInBytes; // Number in bytes of the last chunks.
    }


    /* Function sets a chunk's value in the array to T.
     * Input: AN integer corresponding to the chunk's location in the array.
     */
    void downloadedChunk(int chunkID) {
        chunks[chunkID] = true;
    }


    /* Function checks if all cells in array have value T.
     * Output: A boolean variable corresponding to the required answer.
     */
    boolean isDownloadComplete() {
        for (boolean chunk : chunks) {
            if (!chunk) {
                return false;
            }
        }
        return true;
    }


    /* Function gets the percentage of cells in the array with value T out of the total array's cells.
     * Output: An integer corresponding to the required percentage.
     */
    int downloadStatus() {
        int completedChunks = 0;
        for (boolean chunk : chunks) {
            if (chunk) {
                completedChunks++;
            }
        }
        return ((completedChunks * 100) / this.chunks.length);
    }


    /*  Function gets the ranges in bytes to be downloaded for a range request header.
     *  Input: Integers corresponding to the first and last chunks' locations in the array from where to achieve the ranges.
     *  Output: A string corresponding to the ranges to be downloaded from the required range.
     */
    String getRanges(int offset, int ending) {
        StringBuilder ranges = new StringBuilder();
        int continuous = 0;
        boolean notFirstInRange = false;

        for (int i = offset; i <= ending; i++) {
            if (!chunks[i]) {
                if (continuous == 0) {
                    if (notFirstInRange) {
                        ranges.append(", ");
                    }
                    ranges.append("(");
                    ranges.append((i * this.chunkSize));
                    ranges.append(" - ");
                    notFirstInRange = true;
                    continuous++;
                }
//                // For the last chunk in the range.
                if (i == ending) {
                    if (i == chunks.length - 1) {
                        ranges.append((i * this.chunkSize) + this.lastChunkInBytes - 1);
                    } else {
                        ranges.append((i + 1) * this.chunkSize - 1);
                    }
                    ranges.append(")");
                }
            } else {
                if (continuous != 0) {
                    ranges.append(((i * chunkSize) - 1));
                    ranges.append(")");
                    continuous = 0;
                }
            }
        }
        return ranges.toString();
    }


    /* Function gets the number of chunks in the array with value F.
     * Output: An integer corresponding to the required number.
     */
    int getRemainingChunkNumber() {
        int remainingChunks = 0;
        for (boolean chunk : chunks) {
            if (!chunk) {
                remainingChunks++;
            }
        }
        return remainingChunks;
    }


    /* Function gets the next chunk's location in the array with value F.
     * Input: An integer corresponding to the current location in the array.
     * Output: An integer corresponding to required answer (if no such chunk exists, returns -1).
     */
    int getNextChunkToDownload(int currentPosition) {
        for (int i = (currentPosition + 1); i < chunks.length; i++) {
            if (!chunks[i]) {
                return i;
            }
        }
        return -1;
    }


    /* Function gets the last chunk's location in array with value F given start position and chunk number in range.
     * Input:  - An integer corresponding to the starting location of the range in the array.
     *         - An integer corresponding to the number of chunks allocated for the range.
     * Output: An integer corresponding to required answer (if no such range exists, returns -1).
     */
    int getLastChunkInRange(int startingChunk, int chunksInRange) {
        int rangeCounter = 0;
        for (int i = startingChunk; i < chunks.length; i++) {
            if (!chunks[i]) {
                rangeCounter++;
            }
            if (rangeCounter == chunksInRange) {
                return i;
            }
        }
        return -1;
    }


    /* Function gets the next length of the array.
     * Output: An integer corresponding to required answer.
     */
    int getLength() {
        return chunks.length;
    }


    /* Function gets the number of bytes the last chunk in the array contains.
     * Output: An integer corresponding to required answer.
     */
    int getLastChunkInBytes() {
        return this.lastChunkInBytes;
    }


    /* Function gets the number of bytes a chunks in the array contains.
     * Output: An integer corresponding to required answer.
     */
    int getChunkSize() {
        return this.chunkSize;
    }
}

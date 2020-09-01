class Chunk {
    private int id;             // An integer for the uid of the chunk
    private byte[] data;        // An array of bytes for the data the chunk contains.

    Chunk(int id, byte[] data) {
        this.id = id;
        this.data = data;
    }

    // Returns the ID of the chunk.
    int getId() {
        return this.id;
    }

    // Returns the data of the chunk.
    byte[] getData() {
        return this.data;
    }

}

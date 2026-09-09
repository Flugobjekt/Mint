package abomination;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.openhft.hashing.LongHashFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LinearRegionFileTestSuite {
    // Layout reference: LinearRegionFileFormatTools/mclinear.py at
    // 654e2e13205c4f53aaa96fe56bfa6058c9d0a4e7, not a separate Linear v3 format.
    private static final long SIGNATURE = 0xc3ff13183cca9d9aL;
    private static final byte[] PAYLOAD = {10, 0, 0, 1, 2, 3, 0};
    private static final ChunkPos POS = new ChunkPos(-31, 66);
    private static final int INDEX = 1 + 2 * 32;
    @TempDir Path directory;

    private LinearRegionFile open(Path path) throws Exception {
        return new LinearRegionFile(null, path, directory, RegionFileVersion.VERSION_DEFLATE, false, 1);
    }

    private static byte[] compress(byte[] bytes) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zstd = new ZstdOutputStream(output, 1)) {
            zstd.write(bytes);
        }
        return output.toByteArray();
    }

    private static byte[] decompress(byte[] bytes) throws Exception {
        try (var zstd = new ZstdInputStream(new ByteArrayInputStream(bytes))) {
            return zstd.readAllBytes();
        }
    }

    @Test
    void writerUsesUpstreamV2LayoutAndRoundTrips() throws Exception {
        Path path = directory.resolve("r.-1.2.linear");
        try (var region = open(path)) {
            region.write(POS, ByteBuffer.wrap(PAYLOAD));
        }
        ByteBuffer file = ByteBuffer.wrap(Files.readAllBytes(path));
        assertEquals(SIGNATURE, file.getLong());
        assertEquals(3, file.get());
        file.getLong();
        assertEquals(8, file.get());
        assertEquals(-1, file.getInt());
        assertEquals(2, file.getInt());
        assertEquals(26, file.position());
        byte[] bitmap = new byte[128];
        file.get(bitmap);
        assertEquals(1 << (7 - INDEX % 8), bitmap[INDEX / 8] & 255);
        assertEquals(0, file.get()); // Empty feature dictionary.
        int[] sizes = new int[64];
        long[] hashes = new long[64];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = file.getInt();
            assertEquals(1, file.get());
            hashes[i] = file.getLong();
        }
        for (int i = 0; i < sizes.length; i++) {
            byte[] compressed = new byte[sizes[i]];
            file.get(compressed);
            if (i != 0) {
                assertEquals(0, sizes[i]);
                continue;
            }
            assertEquals(LongHashFunction.xx().hashBytes(compressed), hashes[i]);
            ByteBuffer bucket = ByteBuffer.wrap(decompress(compressed));
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    boolean present = x == 1 && z == 2;
                    assertEquals(present ? PAYLOAD.length + 8 : 0, bucket.getInt());
                    bucket.getLong();
                    if (present) {
                        byte[] chunk = new byte[PAYLOAD.length];
                        bucket.get(chunk);
                        assertArrayEquals(PAYLOAD, chunk);
                    }
                }
            }
            assertFalse(bucket.hasRemaining());
        }
        assertEquals(SIGNATURE, file.getLong());
        assertFalse(file.hasRemaining());
        try (var region = open(path)) {
            assertArrayEquals(PAYLOAD, region.getChunkDataInputStream(POS).readAllBytes());
            assertFalse(region.hasChunk(new ChunkPos(-32, 64)));
        }
    }

    @Test
    void readsUpstreamLayoutsWithoutRelabelingLegacyPayload() throws Exception {
        for (int version : new int[]{1, 2, 3}) {
            var payload = new ByteArrayOutputStream();
            try (var data = new DataOutputStream(payload)) {
                if (version < 3) {
                    for (int i = 0; i < 1024; i++) {
                        data.writeInt(i == INDEX ? PAYLOAD.length : 0);
                        data.writeInt(123);
                    }
                    data.write(PAYLOAD);
                } else {
                    // grid_size=1: x-major bucket traversal, unlike the legacy table.
                    for (int x = 0; x < 32; x++) {
                        for (int z = 0; z < 32; z++) {
                            boolean present = x + z * 32 == INDEX;
                            data.writeInt(present ? PAYLOAD.length + 8 : 0);
                            data.writeLong(123);
                            if (present) data.write(PAYLOAD);
                        }
                    }
                }
            }
            byte[] compressed = compress(payload.toByteArray());
            Path path = directory.resolve("r.-1.2.linear");
            try (var file = new DataOutputStream(Files.newOutputStream(path))) {
                file.writeLong(SIGNATURE);
                file.writeByte(version);
                file.writeLong(123);
                file.writeByte(1); // Compression level (legacy) or grid size (v2).
                if (version < 3) {
                    file.writeShort(1);
                    file.writeInt(compressed.length);
                    file.writeLong(0);
                } else {
                    file.writeInt(-1);
                    file.writeInt(2);
                    byte[] bitmap = new byte[128];
                    bitmap[INDEX / 8] = (byte) (1 << (7 - INDEX % 8));
                    file.write(bitmap);
                    file.writeByte(3);
                    file.writeBytes("foo");
                    file.writeInt(42);
                    file.writeByte(0);
                    file.writeInt(compressed.length);
                    file.writeByte(1);
                    file.writeLong(LongHashFunction.xx().hashBytes(compressed));
                }
                file.write(compressed);
                file.writeLong(SIGNATURE);
            }
            try (var region = open(path)) {
                assertArrayEquals(PAYLOAD, region.getChunkDataInputStream(POS).readAllBytes(), "version " + version);
                assertTrue(region.hasChunk(POS));
                region.write(new ChunkPos(-15, 87), ByteBuffer.wrap(PAYLOAD));
            }
            assertBitmap(path, POS, new ChunkPos(-15, 87));
            assertEquals(3, Files.readAllBytes(path)[8]);
            try (var region = open(path)) {
                assertArrayEquals(PAYLOAD, region.getChunkDataInputStream(POS).readAllBytes());
            }
        }
    }

    private static void assertBitmap(Path path, ChunkPos... positions) throws Exception {
        byte[] expected = new byte[128];
        for (ChunkPos pos : positions) {
            int index = (pos.x() & 31) + (pos.z() & 31) * 32;
            expected[index / 8] |= (byte) (1 << (7 - index % 8));
        }
        ByteBuffer file = ByteBuffer.wrap(Files.readAllBytes(path));
        assertEquals(3, file.get(8));
        file.position(26);
        byte[] actual = new byte[128];
        file.get(actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    void unreadBucketSurvivesFlushAndDeletion() throws Exception {
        Path path = directory.resolve("r.-1.2.linear");
        ChunkPos other = new ChunkPos(-15, 87);
        ChunkPos neighbor = new ChunkPos(-30, 65);
        try (var region = open(path)) {
            region.write(POS, ByteBuffer.wrap(PAYLOAD));
            region.write(neighbor, ByteBuffer.wrap(PAYLOAD));
            region.write(other, ByteBuffer.wrap(PAYLOAD));
        }
        assertBitmap(path, POS, neighbor, other);
        try (var region = open(path)) {
            region.write(POS, ByteBuffer.wrap(new byte[]{42}));
            region.flush();
            assertBitmap(path, POS, neighbor, other);
            region.clear(POS);
            assertFalse(region.hasChunk(POS));
            assertNull(region.getChunkDataInputStream(POS));
            region.flush();
            assertBitmap(path, neighbor, other);
        }
        try (var region = open(path)) {
            assertArrayEquals(PAYLOAD, region.getChunkDataInputStream(other).readAllBytes());
        }
        try (var region = open(path)) {
            assertFalse(region.hasChunk(POS));
            assertTrue(region.hasChunk(neighbor));
            assertArrayEquals(PAYLOAD, region.getChunkDataInputStream(neighbor).readAllBytes());
            // Delete directly from a still-compressed bucket.
            region.clear(other);
            region.flush();
            assertBitmap(path, neighbor);
        }
        try (var region = open(path)) {
            assertFalse(region.hasChunk(other));
            assertNull(region.getChunkDataInputStream(other));
            assertArrayEquals(PAYLOAD, region.getChunkDataInputStream(neighbor).readAllBytes());
            region.write(POS, ByteBuffer.wrap(PAYLOAD));
            region.flush();
            assertBitmap(path, POS, neighbor);
            region.write(POS, ByteBuffer.wrap(new byte[0]));
            region.flush();
            assertBitmap(path, neighbor);
        }
    }

    @Test
    void rejectsUnsupportedVersionBytes() throws Exception {
        for (int version : new int[]{0, 4, 127, 255}) {
            Path path = directory.resolve("r.0.0.linear");
            byte[] bytes = ByteBuffer.allocate(9).putLong(SIGNATURE).put((byte) version).array();
            Files.write(path, bytes);
            try (var region = open(path)) {
                var error = assertThrows(RuntimeException.class, () -> region.hasChunk(POS));
                assertTrue(error.getMessage().startsWith("Invalid version:"));
            }
            assertArrayEquals(bytes, Files.readAllBytes(path));
        }
    }
}

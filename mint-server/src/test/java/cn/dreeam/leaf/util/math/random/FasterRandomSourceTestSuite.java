package cn.dreeam.leaf.util.math.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FasterRandomSourceTestSuite {

    @Test
    public void testSetSeedChangesOutput() {
        FasterRandomSource randomSource = new FasterRandomSource(12345L);
        long initialNextLong = randomSource.nextLong();

        // Change seed
        randomSource.setSeed(54321L);
        long newNextLong = randomSource.nextLong();

        // Given different seeds, the nextLong() values should be different (highly likely)
        assertNotEquals(initialNextLong, newNextLong, "Output should change after setting a new seed");
    }

    @Test
    public void testSetSeedResetsStream() {
        FasterRandomSource randomSource1 = new FasterRandomSource(12345L);
        FasterRandomSource randomSource2 = new FasterRandomSource(12345L);

        // Advance randomSource1 stream a bit
        randomSource1.nextLong();
        randomSource1.nextLong();

        // Now set the seed of randomSource1 back to the original seed
        randomSource1.setSeed(12345L);

        // Verify that randomSource1 and randomSource2 now produce the exact same sequence
        for (int i = 0; i < 100; i++) {
            assertEquals(randomSource1.nextLong(), randomSource2.nextLong(), "Stream sequence should be identical after resetting the seed");
            assertEquals(randomSource1.nextInt(), randomSource2.nextInt(), "Stream sequence should be identical after resetting the seed");
            assertEquals(randomSource1.nextDouble(), randomSource2.nextDouble(), "Stream sequence should be identical after resetting the seed");
        }
    }
}

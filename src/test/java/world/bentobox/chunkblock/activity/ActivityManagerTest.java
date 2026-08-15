package world.bentobox.chunkblock.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableSet;

import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.chunkblock.ChunkBlock;
import world.bentobox.chunkblock.CommonTestSetup;
import world.bentobox.chunkblock.Settings;
import world.bentobox.chunkblock.dataobjects.IslandActivity;

/**
 * The spec for the activity counters: attribution, time windows, retention, and the
 * distinct-claim rule that stops re-locked chunks from being counted twice.
 *
 * @author tastybento
 */
class ActivityManagerTest extends CommonTestSetup {

    @Mock
    private ChunkBlock addon;

    private AbstractDatabaseHandler<Object> h;
    private MockedStatic<DatabaseSetup> mockDb;
    private Settings settings;
    private ActivityManager am;
    /** The controllable "today" */
    private long day = 20000;

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // Database
        h = mock(AbstractDatabaseHandler.class);
        mockDb = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockDb.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(h.saveObjectNow(any())).thenReturn(CompletableFuture.completedFuture(true));

        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);

        when(island.getUniqueId()).thenReturn("island-id");

        am = new ActivityManager(addon);
        am.setDaySupplier(() -> day);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        mockDb.closeOnDemand();
        super.tearDown();
    }

    @Test
    void testRecordAndLifetime() {
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 5);
        assertEquals(5, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 0));
        // Island total includes the member's contribution
        assertEquals(5, am.getCount(island, null, CounterType.MAGIC_BLOCKS, 0));
        // Other counters unaffected
        assertEquals(0, am.getCount(island, uuid, CounterType.CHUNKS_CLAIMED, 0));
    }

    @Test
    void testNonMemberIsNotCounted() {
        UUID stranger = UUID.randomUUID();
        am.recordActivity(island, stranger, CounterType.MAGIC_BLOCKS, 5);
        assertEquals(0, am.getCount(island, stranger, CounterType.MAGIC_BLOCKS, 0));
        assertEquals(0, am.getCount(island, null, CounterType.MAGIC_BLOCKS, 0));
    }

    @Test
    void testNonPositiveAmountIgnored() {
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 0);
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, -3);
        assertEquals(0, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 0));
    }

    @Test
    void testIslandScopeRecording() {
        am.recordActivity(island, null, CounterType.LEVELS_EARNED, 10);
        assertEquals(10, am.getCount(island, null, CounterType.LEVELS_EARNED, 0));
        // Nothing was attributed to the member
        assertEquals(0, am.getCount(island, uuid, CounterType.LEVELS_EARNED, 0));
    }

    @Test
    void testTimeWindows() {
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 3);
        day += 5;
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 2);
        // Today only
        assertEquals(2, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 1));
        // Last 3 days miss the older record
        assertEquals(2, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 3));
        // Last 7 days include both, and so does lifetime
        assertEquals(5, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 7));
        assertEquals(5, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 0));
        // Island totals window the same way
        assertEquals(2, am.getCount(island, null, CounterType.MAGIC_BLOCKS, 1));
        assertEquals(5, am.getCount(island, null, CounterType.MAGIC_BLOCKS, 7));
    }

    @Test
    void testRetentionPrunesDailyButNotLifetime() {
        settings.setActivityRetentionDays(7);
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 3);
        day += 10;
        // Recording again prunes the now-too-old bucket
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 1);
        assertEquals(1, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 30));
        // Lifetime is never pruned
        assertEquals(4, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 0));
    }

    @Test
    void testRecordClaimDistinguishesFirstClaimFromRecovery() {
        assertTrue(am.recordClaim(island, 1, 0, uuid));
        assertEquals(1, am.getCount(island, uuid, CounterType.CHUNKS_CLAIMED, 0));
        assertEquals(0, am.getCount(island, uuid, CounterType.CHUNKS_RECLAIMED, 0));
        // The chunk re-locks and is claimed back: a recovery, never a second claim
        assertFalse(am.recordClaim(island, 1, 0, uuid));
        assertEquals(1, am.getCount(island, uuid, CounterType.CHUNKS_CLAIMED, 0));
        assertEquals(1, am.getCount(island, uuid, CounterType.CHUNKS_RECLAIMED, 0));
        // A different chunk is a first claim again
        assertTrue(am.recordClaim(island, 0, 1, uuid));
        assertEquals(2, am.getCount(island, uuid, CounterType.CHUNKS_CLAIMED, 0));
    }

    @Test
    void testMembersKeepSeparateCounts() {
        UUID mate = UUID.randomUUID();
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid, mate));
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 3);
        am.recordActivity(island, mate, CounterType.MAGIC_BLOCKS, 4);
        assertEquals(3, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 0));
        assertEquals(4, am.getCount(island, mate, CounterType.MAGIC_BLOCKS, 0));
        assertEquals(7, am.getCount(island, null, CounterType.MAGIC_BLOCKS, 0));
        assertEquals(Set.of(uuid, mate), am.getContributors(island));
    }

    @Test
    void testContributorsExcludeIslandScope() {
        am.recordActivity(island, null, CounterType.LEVELS_EARNED, 10);
        assertTrue(am.getContributors(island).isEmpty());
    }

    @Test
    void testResetIslandClearsEverything() {
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 5);
        assertTrue(am.recordClaim(island, 1, 0, uuid));
        am.resetIsland("island-id");
        assertEquals(0, am.getCount(island, uuid, CounterType.MAGIC_BLOCKS, 0));
        // The claimed-ever set was cleared too: the same chunk is a first claim again
        assertTrue(am.recordClaim(island, 1, 0, uuid));
    }

    @Test
    void testFrequentCounterSavesAreThrottled() throws Exception {
        for (int i = 0; i < 19; i++) {
            am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 1);
        }
        verify(h, never()).saveObject(any());
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 1);
        verify(h).saveObject(any());
    }

    @Test
    void testRareCountersSaveImmediately() throws Exception {
        am.recordActivity(island, uuid, CounterType.CHUNKS_CLAIMED, 1);
        verify(h).saveObject(any());
    }

    @Test
    void testSaveCacheNowWritesDirectly() throws Exception {
        am.recordActivity(island, uuid, CounterType.MAGIC_BLOCKS, 1);
        am.saveCacheNow();
        verify(h).saveObjectNow(any());
    }

    @Test
    void testTrophyCheckRunsOnRecord() {
        world.bentobox.chunkblock.trophies.TrophyManager tm = mock(
                world.bentobox.chunkblock.trophies.TrophyManager.class);
        when(addon.getTrophyManager()).thenReturn(tm);
        am.record(island, uuid, CounterType.MAGIC_BLOCKS, 1);
        verify(tm).check(island);
        // A dropped record still checks nothing new but must not blow up
        am.record(island, UUID.randomUUID(), CounterType.MAGIC_BLOCKS, 1);
        verify(tm, times(1)).check(island);
    }

    @Test
    void testDataSurvivesSerializationShape() {
        // The flat map shape is the storage contract: composite keys, plain longs
        IslandActivity data = new IslandActivity("x");
        data.add(uuid.toString(), CounterType.MAGIC_BLOCKS.name(), 100, 2);
        assertEquals(2, data.getLifetime(uuid.toString(), "MAGIC_BLOCKS"));
        assertEquals(2, data.sumWindow(uuid.toString(), "MAGIC_BLOCKS", 100, 100));
        assertEquals(2, data.getLifetimeTotal("MAGIC_BLOCKS"));
        data.prune(101);
        assertEquals(0, data.sumWindow(uuid.toString(), "MAGIC_BLOCKS", 0, 200));
        assertEquals(2, data.getLifetime(uuid.toString(), "MAGIC_BLOCKS"));
    }
}

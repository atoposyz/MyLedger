package com.example.myledger.util

import org.junit.Assert.*
import org.junit.Test

class StatisticsFormatterTest {
    @Test fun percentagesRoundWithoutOverflowAndDescribeTinyNonzeroShares() {
        assertEquals("33.3%", StatisticsFormatter.percentage(1, 3))
        assertEquals("66.7%", StatisticsFormatter.percentage(2, 3))
        assertEquals("100.0%", StatisticsFormatter.percentage(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals("<0.1%", StatisticsFormatter.percentage(1, Long.MAX_VALUE))
    }
    @Test fun zeroTotalsAndZeroAmountsDoNotProduceNanOrInventShares() {
        assertEquals("0.0%", StatisticsFormatter.percentage(0, 0)); assertEquals(0f, StatisticsFormatter.share(0, 0), 0f)
        assertEquals("0.0%", StatisticsFormatter.percentage(0, Long.MAX_VALUE)); assertEquals(0f, StatisticsFormatter.share(0, Long.MAX_VALUE), 0f)
    }
    @Test fun chartRatiosHandleMaximumMinorUnitsAndStayWithinDrawingBounds() {
        assertEquals(1f, StatisticsFormatter.share(Long.MAX_VALUE, Long.MAX_VALUE), 0f)
        assertEquals(0.5f, StatisticsFormatter.share(Long.MAX_VALUE / 2, Long.MAX_VALUE), 0.000001f)
        assertEquals(1f, StatisticsFormatter.share(200, 100), 0f)
    }
}

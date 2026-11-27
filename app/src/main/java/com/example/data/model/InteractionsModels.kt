package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class PageVisitRecord(
    val id: String = "",
    val page: String = "/",
    val visitorId: String = "",
    val dateKey: String = "",
    val createdAtMillis: Long = 0L
)

enum class VisitorRangeUnit(val label: String) {
    DAYS("Days"),
    WEEKS("Weeks"),
    YEARS("Years")
}

enum class VisitorTimeRange(
    val id: String,
    val label: String,
    val shortLabel: String,
    val unit: VisitorRangeUnit,
    val durationDays: Int
) {
    ONE_DAY("1d", "1 Day", "1D", VisitorRangeUnit.DAYS, 1),
    THREE_DAYS("3d", "3 Days", "3D", VisitorRangeUnit.DAYS, 3),
    SEVEN_DAYS("7d", "7 Days", "7D", VisitorRangeUnit.DAYS, 7),
    FOURTEEN_DAYS("14d", "14 Days", "14D", VisitorRangeUnit.DAYS, 14),
    THIRTY_DAYS("30d", "30 Days", "30D", VisitorRangeUnit.DAYS, 30),

    ONE_WEEK("1w", "1 Week", "1W", VisitorRangeUnit.WEEKS, 7),
    TWO_WEEKS("2w", "2 Weeks", "2W", VisitorRangeUnit.WEEKS, 14),
    FOUR_WEEKS("4w", "4 Weeks", "4W", VisitorRangeUnit.WEEKS, 28),
    TWELVE_WEEKS("12w", "12 Weeks", "12W", VisitorRangeUnit.WEEKS, 84),

    ONE_YEAR("1y", "1 Year", "1Y", VisitorRangeUnit.YEARS, 365),
    TWO_YEARS("2y", "2 Years", "2Y", VisitorRangeUnit.YEARS, 730),
    FIVE_YEARS("5y", "5 Years", "5Y", VisitorRangeUnit.YEARS, 1825);

    companion object {
        fun optionsForUnitExcluding(
            unit: VisitorRangeUnit,
            selected: VisitorTimeRange
        ): List<VisitorTimeRange> = entries.filter { it.unit == unit && it != selected }
    }
}

data class TrafficChartBucket(
    val label: String,
    val fullLabel: String,
    val visits: Long,
    val uniqueVisitors: Long
)

data class TopVisitedPageStat(
    val rawPage: String,
    val displayName: String,
    val visits: Long,
    val shareFraction: Float
)

data class InteractionsAnalyticsReport(
    val range: VisitorTimeRange,
    val periodVisits: Long,
    val previousPeriodVisits: Long,
    val growthPercent: Int?,
    val uniqueVisitors: Long,
    val returningVisits: Long,
    val averagePerSubUnit: Double,
    val averageSubUnitLabel: String,
    val allTimeTotalVisits: Long,
    val periodInquiries: Long,
    val engagementRatePercent: Double,
    val peakHourLabel: String,
    val peakDayOfWeekLabel: String,
    val buckets: List<TrafficChartBucket>,
    val topPages: List<TopVisitedPageStat>,
    val recentVisits: List<PageVisitRecord>,
    val lastVisitAtMillis: Long
)

object InteractionsAnalyticsEngine {
    private const val HOUR_MILLIS = 60L * 60L * 1000L
    private const val DAY_MILLIS = 24L * HOUR_MILLIS

    fun computeReport(
        stats: WebsiteVisitorStats,
        inquiries: List<CustomerInquiry>,
        range: VisitorTimeRange,
        nowMillis: Long = System.currentTimeMillis()
    ): InteractionsAnalyticsReport {
        val allRecords = stats.recentVisitRecords
        val windowMillis = range.durationDays.toLong() * DAY_MILLIS
        val periodStartMillis = nowMillis - windowMillis
        val previousStartMillis = periodStartMillis - windowMillis

        val periodRecords = allRecords
            .filter { it.createdAtMillis in periodStartMillis..nowMillis }
            .sortedByDescending { it.createdAtMillis }

        val previousRecords = allRecords
            .filter { it.createdAtMillis in previousStartMillis until periodStartMillis }

        val periodInquiriesCount = inquiries.count { inquiry ->
            val createdMs = inquiry.createdAt?.toDate()?.time ?: 0L
            createdMs in periodStartMillis..nowMillis
        }.toLong()

        val allTimeTotal = maxOf(stats.totalVisits, allRecords.size.toLong())
        val rawPeriodCount = periodRecords.size.toLong()
        val periodVisits = when {
            range == VisitorTimeRange.ONE_DAY -> maxOf(rawPeriodCount, stats.todayVisits).coerceAtMost(allTimeTotal)
            range.durationDays == 7 -> maxOf(rawPeriodCount, stats.last7DaysVisits).coerceAtMost(allTimeTotal)
            else -> rawPeriodCount
        }
        val previousPeriodVisits = previousRecords.size.toLong()

        val growthPercent: Int? = when {
            previousPeriodVisits > 0L -> (((periodVisits - previousPeriodVisits).toDouble() / previousPeriodVisits.toDouble()) * 100.0).roundToInt()
            periodVisits == 0L -> 0
            else -> null // indicates "New" activity vs 0 prior visits
        }

        val uniqueSet = HashSet<String>()
        periodRecords.forEach { rec ->
            uniqueSet.add(rec.visitorId.ifBlank { rec.id })
        }
        val uniqueVisitors = if (uniqueSet.isEmpty() && periodVisits > 0L) {
            minOf(periodVisits, stats.uniqueVisitors.coerceAtLeast(1L))
        } else {
            uniqueSet.size.toLong().coerceAtMost(periodVisits)
        }
        val returningVisits = (periodVisits - uniqueVisitors).coerceAtLeast(0L)

        val (averageValue, averageLabel) = when {
            range == VisitorTimeRange.ONE_DAY -> (periodVisits.toDouble() / 24.0) to "Avg / hr"
            range.unit == VisitorRangeUnit.YEARS -> {
                val months = (range.durationDays / 30.0).coerceAtLeast(1.0)
                (periodVisits.toDouble() / months) to "Avg / mo"
            }
            else -> (periodVisits.toDouble() / range.durationDays.toDouble().coerceAtLeast(1.0)) to "Avg / day"
        }

        val engagementRate = if (periodVisits > 0L) {
            ((periodInquiriesCount.toDouble() / periodVisits.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        val buckets = buildChartBuckets(
            records = periodRecords,
            range = range,
            nowMillis = nowMillis,
            fallbackCount = if (periodRecords.isEmpty()) periodVisits else 0L
        )

        val peakHourLabel = computePeakHourLabel(periodRecords)
        val peakDayLabel = computePeakDayOfWeekLabel(periodRecords)
        val topPages = computeTopPages(periodRecords)

        val lastVisitAt = maxOf(
            periodRecords.firstOrNull()?.createdAtMillis ?: 0L,
            stats.lastVisitAtMillis
        )

        return InteractionsAnalyticsReport(
            range = range,
            periodVisits = periodVisits,
            previousPeriodVisits = previousPeriodVisits,
            growthPercent = growthPercent,
            uniqueVisitors = uniqueVisitors,
            returningVisits = returningVisits,
            averagePerSubUnit = averageValue,
            averageSubUnitLabel = averageLabel,
            allTimeTotalVisits = allTimeTotal,
            periodInquiries = periodInquiriesCount,
            engagementRatePercent = engagementRate,
            peakHourLabel = peakHourLabel,
            peakDayOfWeekLabel = peakDayLabel,
            buckets = buckets,
            topPages = topPages,
            recentVisits = periodRecords.take(10),
            lastVisitAtMillis = lastVisitAt
        )
    }

    fun formatFriendlyPageName(rawPage: String): String {
        val cleaned = rawPage.trim().lowercase(Locale.US)
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .ifEmpty { "/" }
        return when {
            cleaned == "/" || cleaned == "/home" || cleaned == "/dashboard" -> "Home Dashboard"
            cleaned.startsWith("/inventory") || cleaned.startsWith("/cars") || cleaned.startsWith("/vehicles") ->
                if (cleaned.count { it == '/' } >= 2) "Vehicle Details" else "Vehicle Inventory"
            cleaned.startsWith("/tiktok") || cleaned.startsWith("/videos") -> "TikTok Walkarounds"
            cleaned.startsWith("/contact") || cleaned.startsWith("/location") -> "Contact & Location"
            cleaned.startsWith("/about") -> "About Showroom"
            else -> rawPage.trim().ifBlank { "Home Dashboard" }
        }
    }

    private fun buildChartBuckets(
        records: List<PageVisitRecord>,
        range: VisitorTimeRange,
        nowMillis: Long,
        fallbackCount: Long
    ): List<TrafficChartBucket> {
        return when (range) {
            VisitorTimeRange.ONE_DAY -> buildHourlySlots(records, nowMillis, fallbackCount)
            VisitorTimeRange.THREE_DAYS -> buildDailyBuckets(records, nowMillis, days = 3, labelPattern = "EEE", fullPattern = "EEE, MMM d")
            VisitorTimeRange.SEVEN_DAYS, VisitorTimeRange.ONE_WEEK ->
                buildDailyBuckets(records, nowMillis, days = 7, labelPattern = "EEE", fullPattern = "EEE, MMM d")
            VisitorTimeRange.FOURTEEN_DAYS, VisitorTimeRange.TWO_WEEKS ->
                buildMultiDayBuckets(records, nowMillis, totalDays = 14, bucketCount = 7)
            VisitorTimeRange.THIRTY_DAYS ->
                buildMultiDayBuckets(records, nowMillis, totalDays = 30, bucketCount = 6)
            VisitorTimeRange.FOUR_WEEKS ->
                buildWeeklyBuckets(records, nowMillis, weeks = 4)
            VisitorTimeRange.TWELVE_WEEKS ->
                buildWeeklyBuckets(records, nowMillis, weeks = 12)
            VisitorTimeRange.ONE_YEAR ->
                buildMonthlyBuckets(records, nowMillis, months = 12)
            VisitorTimeRange.TWO_YEARS ->
                buildQuarterOrBiMonthBuckets(records, nowMillis, totalMonths = 24, stepMonths = 3)
            VisitorTimeRange.FIVE_YEARS ->
                buildYearlyBuckets(records, nowMillis, years = 5)
        }
    }

    private fun buildHourlySlots(
        records: List<PageVisitRecord>,
        nowMillis: Long,
        fallbackCount: Long
    ): List<TrafficChartBucket> {
        val slotCount = 6 // 6 x 4-hour slots across the past 24 hours
        val slotDuration = 4L * HOUR_MILLIS
        val start = nowMillis - (24L * HOUR_MILLIS)
        val shortFmt = SimpleDateFormat("ha", Locale.US)
        val fullFmt = SimpleDateFormat("h:mm a", Locale.US)

        return (0 until slotCount).map { idx ->
            val bucketStart = start + idx * slotDuration
            val bucketEnd = if (idx == slotCount - 1) nowMillis + 1L else bucketStart + slotDuration
            val inBucket = records.filter { it.createdAtMillis in bucketStart until bucketEnd }
            val unique = inBucket.mapTo(HashSet()) { it.visitorId.ifBlank { it.id } }.size.toLong()
            val visits = if (idx == slotCount - 1 && inBucket.isEmpty() && fallbackCount > 0L) {
                fallbackCount
            } else {
                inBucket.size.toLong()
            }
            val startLabel = shortFmt.format(Date(bucketStart)).lowercase(Locale.US)
            val fullLabel = "${fullFmt.format(Date(bucketStart))} – ${fullFmt.format(Date(bucketEnd - 1L))}"
            TrafficChartBucket(
                label = startLabel,
                fullLabel = fullLabel,
                visits = visits,
                uniqueVisitors = maxOf(unique, if (visits > 0L && unique == 0L) 1L else 0L)
            )
        }
    }

    private fun buildDailyBuckets(
        records: List<PageVisitRecord>,
        nowMillis: Long,
        days: Int,
        labelPattern: String,
        fullPattern: String
    ): List<TrafficChartBucket> {
        val shortFmt = SimpleDateFormat(labelPattern, Locale.US)
        val fullFmt = SimpleDateFormat(fullPattern, Locale.US)
        val todayCal = startOfDayCalendar(nowMillis)

        return (days - 1 downTo 0).map { offset ->
            val calStart = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val startMs = calStart.timeInMillis
            val endMs = if (offset == 0) nowMillis + 1L else startMs + DAY_MILLIS
            val inBucket = records.filter { it.createdAtMillis in startMs until endMs }
            val unique = inBucket.mapTo(HashSet()) { it.visitorId.ifBlank { it.id } }.size.toLong()
            TrafficChartBucket(
                label = shortFmt.format(Date(startMs)),
                fullLabel = fullFmt.format(Date(startMs)),
                visits = inBucket.size.toLong(),
                uniqueVisitors = unique
            )
        }
    }

    private fun buildMultiDayBuckets(
        records: List<PageVisitRecord>,
        nowMillis: Long,
        totalDays: Int,
        bucketCount: Int
    ): List<TrafficChartBucket> {
        val start = nowMillis - (totalDays.toLong() * DAY_MILLIS)
        val stepMs = (totalDays.toLong() * DAY_MILLIS) / bucketCount
        val fmt = SimpleDateFormat("MMM d", Locale.US)
        return (0 until bucketCount).map { idx ->
            val bStart = start + idx * stepMs
            val bEnd = if (idx == bucketCount - 1) nowMillis + 1L else bStart + stepMs
            val inBucket = records.filter { it.createdAtMillis in bStart until bEnd }
            val unique = inBucket.mapTo(HashSet()) { it.visitorId.ifBlank { it.id } }.size.toLong()
            val label = fmt.format(Date(bStart))
            val fullLabel = "${fmt.format(Date(bStart))} – ${fmt.format(Date(bEnd - 1L))}"
            TrafficChartBucket(
                label = label,
                fullLabel = fullLabel,
                visits = inBucket.size.toLong(),
                uniqueVisitors = unique
            )
        }
    }

    private fun buildWeeklyBuckets(
        records: List<PageVisitRecord>,
        nowMillis: Long,
        weeks: Int
    ): List<TrafficChartBucket> {
        val weekMs = 7L * DAY_MILLIS
        val start = nowMillis - (weeks.toLong() * weekMs)
        val fmt = SimpleDateFormat("MMM d", Locale.US)
        return (0 until weeks).map { idx ->
            val bStart = start + idx * weekMs
            val bEnd = if (idx == weeks - 1) nowMillis + 1L else bStart + weekMs
            val inBucket = records.filter { it.createdAtMillis in bStart until bEnd }
            val unique = inBucket.mapTo(HashSet()) { it.visitorId.ifBlank { it.id } }.size.toLong()
            val label = if (weeks <= 4) "W${idx + 1}" else if (idx % 2 == 0) "W${idx + 1}" else ""
            val fullLabel = "Week ${idx + 1} (${fmt.format(Date(bStart))} – ${fmt.format(Date(bEnd - 1L))})"
            TrafficChartBucket(
                label = label,
                fullLabel = fullLabel,
                visits = inBucket.size.toLong(),
                uniqueVisitors = unique
            )
        }
    }

    private fun buildMonthlyBuckets(
        records: List<PageVisitRecord>,
        nowMillis: Long,
        months: Int
    ): List<TrafficChartBucket> {
        val shortFmt = SimpleDateFormat("MMM", Locale.US)
        val fullFmt = SimpleDateFormat("MMMM yyyy", Locale.US)
        val baseCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (months - 1 downTo 0).map { offset ->
            val calStart = (baseCal.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
            val calEnd = (calStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            val startMs = calStart.timeInMillis
            val endMs = if (offset == 0) nowMillis + 1L else calEnd.timeInMillis
            val inBucket = records.filter { it.createdAtMillis in startMs until endMs }
            val unique = inBucket.mapTo(HashSet()) { it.visitorId.ifBlank { it.id } }.size.toLong()
            val rawMonth = shortFmt.format(Date(startMs))
            val label = if (offset % 2 == 0 || offset == 0) rawMonth else ""
            TrafficChartBucket(
                label = label,
                fullLabel = fullFmt.format(Date(startMs)),
                visits = inBucket.size.toLong(),
                uniqueVisitors = unique
            )
        }
    }

    private fun buildQuarterOrBiMonthBuckets(
        records: List<PageVisitRecord>,
        nowMillis: Long,
        totalMonths: Int,
        stepMonths: Int
    ): List<TrafficChartBucket> {
        val count = totalMonths / stepMonths
        val shortFmt = SimpleDateFormat("MMM ''yy", Locale.US)
        val baseCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (count - 1 downTo 0).map { idx ->
            val calStart = (baseCal.clone() as Calendar).apply {
                add(Calendar.MONTH, -(idx + 1) * stepMonths + 1)
            }
            val calEnd = (calStart.clone() as Calendar).apply { add(Calendar.MONTH, stepMonths) }
            val startMs = calStart.timeInMillis
            val endMs = if (idx == 0) nowMillis + 1L else calEnd.timeInMillis
            val inBucket = records.filter { it.createdAtMillis in startMs until endMs }
            val unique = inBucket.mapTo(HashSet()) { it.visitorId.ifBlank { it.id } }.size.toLong()
            val label = if (idx % 2 == 0) shortFmt.format(Date(startMs)) else ""
            val fullLabel = "${shortFmt.format(Date(startMs))} – ${shortFmt.format(Date(endMs - 1L))}"
            TrafficChartBucket(
                label = label,
                fullLabel = fullLabel,
                visits = inBucket.size.toLong(),
                uniqueVisitors = unique
            )
        }
    }

    private fun buildYearlyBuckets(
        records: List<PageVisitRecord>,
        nowMillis: Long,
        years: Int
    ): List<TrafficChartBucket> {
        val currentYear = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
        return (years - 1 downTo 0).map { offset ->
            val year = currentYear - offset
            val calStart = Calendar.getInstance().apply {
                clear()
                set(year, Calendar.JANUARY, 1, 0, 0, 0)
            }
            val calEnd = Calendar.getInstance().apply {
                clear()
                set(year + 1, Calendar.JANUARY, 1, 0, 0, 0)
            }
            val startMs = calStart.timeInMillis
            val endMs = if (offset == 0) nowMillis + 1L else calEnd.timeInMillis
            val inBucket = records.filter { it.createdAtMillis in startMs until endMs }
            val unique = inBucket.mapTo(HashSet()) { it.visitorId.ifBlank { it.id } }.size.toLong()
            TrafficChartBucket(
                label = year.toString(),
                fullLabel = "Year $year",
                visits = inBucket.size.toLong(),
                uniqueVisitors = unique
            )
        }
    }

    private fun computePeakHourLabel(records: List<PageVisitRecord>): String {
        if (records.isEmpty()) return "No data yet"
        val counts = IntArray(24)
        val cal = Calendar.getInstance()
        records.forEach { rec ->
            if (rec.createdAtMillis > 0L) {
                cal.timeInMillis = rec.createdAtMillis
                counts[cal.get(Calendar.HOUR_OF_DAY)]++
            }
        }
        val bestHour = counts.indices.maxByOrNull { counts[it] } ?: return "No data yet"
        if (counts[bestHour] == 0) return "No data yet"
        return "${formatHour12(bestHour)} – ${formatHour12((bestHour + 1) % 24)}"
    }

    private fun computePeakDayOfWeekLabel(records: List<PageVisitRecord>): String {
        if (records.isEmpty()) return "No data yet"
        val counts = mutableMapOf<Int, Int>()
        val cal = Calendar.getInstance()
        records.forEach { rec ->
            if (rec.createdAtMillis > 0L) {
                cal.timeInMillis = rec.createdAtMillis
                val day = cal.get(Calendar.DAY_OF_WEEK)
                counts[day] = (counts[day] ?: 0) + 1
            }
        }
        val bestDay = counts.maxByOrNull { it.value }?.key ?: return "No data yet"
        return when (bestDay) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "No data yet"
        }
    }

    private fun computeTopPages(records: List<PageVisitRecord>): List<TopVisitedPageStat> {
        if (records.isEmpty()) return emptyList()
        val total = records.size.toFloat().coerceAtLeast(1f)
        return records
            .groupBy { formatFriendlyPageName(it.page) }
            .entries
            .map { (friendly, list) ->
                TopVisitedPageStat(
                    rawPage = list.firstOrNull()?.page.orEmpty().ifBlank { "/" },
                    displayName = friendly,
                    visits = list.size.toLong(),
                    shareFraction = (list.size.toFloat() / total).coerceIn(0f, 1f)
                )
            }
            .sortedByDescending { it.visits }
            .take(5)
    }

    private fun startOfDayCalendar(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun formatHour12(hour24: Int): String {
        val h = hour24 % 24
        val period = if (h < 12) "AM" else "PM"
        val hour12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return "$hour12 $period"
    }
}

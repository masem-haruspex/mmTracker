package com.mtracker

import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object ReceiptParser {

    private const val TAG = "ReceiptParser"

    private val PRICE_PATTERN = Pattern.compile(
        """(\d{1,3}(?:[.,]\d{3})*[.,]\d{2})\s*[А-Яа-яA-Za-z]?\s*.{0,5}$"""
    )
    
    private val PRICE_ENDING = Regex(
        """[.,](\d{2})\s*[А-Яа-яA-Za-z]?\s*.{0,5}$"""
    )

    private val DATE_REGEX = Regex("""(\d{1,2}[./-]\d{1,2}[./-]\d{2,4})""")
    private val TIME_REGEX = Regex("""(\d{1,2}:\d{2}(?::\d{2})?)""")

    private val END_MARKERS = listOf(
        "промет", "вкупно", "vkupno", "ддв", "ddv", "фискална", "сметка",
        "данок", "пазар", "задолжување", "картица", "картичка", "готовина", "кеш"
    )

    data class ParsedItem(val name: String, val price: Int, val isTotal: Boolean = false)
    data class Result(val items: List<ParsedItem>, val receiptDate: String? = null, val receiptTime: String? = null)

    fun parse(rawText: String): Result {
        val allLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        Log.d(TAG, "=== RAW OCR (${allLines.size} lines) ===")
        allLines.forEachIndexed { i, line -> Log.d(TAG, "[$i] '$line'") }

        val isSplitLayout = detectSplitLayout(allLines)
        
        val items = if (isSplitLayout) {
            Log.d(TAG, "Detected SPLIT LAYOUT - using alternative parsing")
            parseSplitLayout(allLines)
        } else {
            Log.d(TAG, "Detected NORMAL LAYOUT - using standard parsing")
            parseNormalLayout(allLines)
        }

        val dateStr = allLines.firstNotNullOfOrNull { DATE_REGEX.find(it)?.value }
        val timeStr = allLines.firstNotNullOfOrNull { TIME_REGEX.find(it)?.value }

        Log.d(TAG, "=== RESULT: ${items.size} items, date=$dateStr, time=$timeStr ===")
        return Result(items, dateStr, timeStr)
    }

    private fun detectSplitLayout(lines: List<String>): Boolean {
        val linesWithPrices = lines.count { isPriceLine(it) && !isMetadataLine(it) }
        
        val productNameLines = lines.count { line ->
            !isMetadataLine(line) && 
            !isPriceLine(line) &&
            line.any { it in 'А'..'Ш' || it in 'а'..'ш' } &&
            line.length > 5
        }
        
        return productNameLines >= 3 && linesWithPrices <= 2
    }

    private fun parseNormalLayout(allLines: List<String>): List<ParsedItem> {
        val productStart = findProductStart(allLines)
        val productEnd = findProductEnd(allLines, productStart)

        val productLines = if (productStart != -1 && productEnd != -1 && productEnd > productStart) {
            allLines.subList(productStart, productEnd)
        } else if (productStart != -1) {
            allLines.subList(productStart, allLines.size)
        } else {
            allLines
        }

        return parseItems(productLines)
    }

    private fun parseSplitLayout(allLines: List<String>): List<ParsedItem> {
        val productNames = mutableListOf<String>()
        var currentName = StringBuilder()
        
        for (line in allLines) {
            if (isMetadataLine(line)) continue
            if (isPriceLine(line)) continue
            
            val lowerLine = line.lowercase()
            if (END_MARKERS.any { lowerLine.contains(it) }) {
                if (currentName.isNotEmpty()) {
                    productNames.add(cleanProductName(currentName.toString()))
                    currentName.clear()
                }
                continue
            }
            
            if (line.any { it in 'А'..'Ш' || it in 'а'..'ш' } && line.length > 5) {
                if (currentName.isNotEmpty()) {
                    currentName.append(" ")
                }
                currentName.append(line)
            } else if (currentName.isNotEmpty()) {
                productNames.add(cleanProductName(currentName.toString()))
                currentName.clear()
            }
        }
        
        if (currentName.isNotEmpty()) {
            productNames.add(cleanProductName(currentName.toString()))
        }
        
        Log.d(TAG, "Found ${productNames.size} product names: $productNames")
        
        val prices = mutableListOf<Int>()
        for (line in allLines) {
            if (isMetadataLine(line)) continue
            if (isPriceLine(line)) {
                val extracted = extractPrice(line)
                if (extracted != null) {
                    val (rawPriceStr, price) = extracted
                    val priceStart = line.lastIndexOf(rawPriceStr)
                    val beforePrice = line.substring(0, priceStart).trim()
                    if (beforePrice.length < 5) { 
                        prices.add(price)
                        Log.d(TAG, "Found standalone price: $price from line '$line'")
                    }
                }
            }
        }
        
        Log.d(TAG, "Found ${prices.size} standalone prices: $prices")
        
        val items = mutableListOf<ParsedItem>()
        val count = minOf(productNames.size, prices.size)
        
        for (i in 0 until count) {
            items.add(ParsedItem(productNames[i], prices[i]))
            Log.d(TAG, "Matched: '${productNames[i]}' -> ${prices[i]}")
        }
        
        return items
    }

    private fun findProductStart(lines: List<String>): Int {
        for ((idx, line) in lines.withIndex()) {
            if (isPriceLine(line) && !isMetadataLine(line) && !isQuantityLine(line)) {
                Log.d(TAG, "Product start at $idx: '$line'")
                return idx
            }
        }
        return -1
    }

    private fun findProductEnd(lines: List<String>, startIdx: Int): Int {
        if (startIdx == -1) return -1
        for (idx in startIdx + 1 until lines.size) {
            val line = lines[idx].lowercase()
            if (END_MARKERS.any { line.contains(it) }) {
                Log.d(TAG, "Product end at $idx: '$line'")
                return idx
            }
        }
        return lines.size
    }

    private fun isPriceLine(line: String): Boolean {
        val ending = PRICE_ENDING.find(line)?.groupValues?.get(1) ?: return false
        return ending in listOf("00", "08", "80", "88")
    }

    private fun isQuantityLine(line: String): Boolean {
        return line.matches(Regex("""^\d+\s*[xXхХ]\s*\d+[.,]\d{2}.*$"""))
    }

    private fun isMetadataLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("индустриска") ||
               lower.contains("продавница") ||
               lower.contains("мин-екс") ||
               lower.contains("мин екс") ||
               lower.contains("дан.број") ||
               lower.contains("дан број") ||
               lower.contains("едб") ||
               lower.contains("пав број") ||
               line.matches(Regex(""".*\d{10,}.*"""))
    }

    private fun parseItems(lines: List<String>): List<ParsedItem> {
        val items = mutableListOf<ParsedItem>()
        var pendingName: String? = null

        for ((idx, line) in lines.withIndex()) {
            if (isMetadataLine(line)) {
                Log.d(TAG, "[$idx] SKIP metadata: '$line'")
                pendingName = null
                continue
            }

            if (isQuantityLine(line)) {
                Log.d(TAG, "[$idx] SKIP quantity line: '$line'")
                continue
            }

            if (!isPriceLine(line)) {
                if (line.length > 3 && !line.matches(Regex("""^\d+$""")) && line != "-") {
                    pendingName = if (pendingName == null) line else "$pendingName $line"
                    Log.d(TAG, "[$idx] PENDING: '$line' -> pendingName='$pendingName'")
                } else {
                    Log.d(TAG, "[$idx] DISCARD (no price, too short or just digits): '$line'")
                }
                continue
            }

            val extracted = extractPrice(line)
            if (extracted == null) {
                Log.d(TAG, "[$idx] SKIP (price extraction failed): '$line'")
                continue
            }
            val (rawPriceStr, price) = extracted

            val priceStart = line.lastIndexOf(rawPriceStr)
            if (priceStart == -1) {
                Log.d(TAG, "[$idx] SKIP (price position not found): '$line' raw='$rawPriceStr'")
                continue
            }

            var currentName = line.substring(0, priceStart).trim()
                .replace(Regex("""\d+[\s]*[xXхХ]\s*\d+[.,]?\d*"""), "")
                .trim()

            val fullName = if (pendingName != null) {
                "$pendingName $currentName".trim()
            } else {
                currentName
            }
            pendingName = null

            if (fullName.isBlank()) {
                Log.d(TAG, "[$idx] SKIP (blank name): '$line'")
                continue
            }

            val lowerName = fullName.lowercase()
            if (END_MARKERS.any { lowerName.contains(it) }) {
                Log.d(TAG, "[$idx] SKIP (end marker in name): '$fullName'")
                continue
            }

            val cleanedName = cleanProductName(fullName)
            
            Log.d(TAG, "[$idx] KEEP: name='$cleanedName' price=$price")
            items.add(ParsedItem(cleanedName, price))
        }

        return items
    }

    private fun cleanProductName(name: String): String {
        var cleaned = name.trim()
        cleaned = cleaned.replaceFirst(Regex("""^\|\s*"""), "")
        cleaned = cleaned.replaceFirst(Regex("""^[А-Яа-я]\s+"""), "")
        cleaned = cleaned.replaceFirst(Regex("""^\d\s+"""), "")
        cleaned = cleaned.replaceFirst(Regex("""^[!?\-]\s*"""), "")
        cleaned = cleaned.replace(Regex("""\s+\d+/\d+$"""), "")
        return cleaned.trim()
    }

    private fun extractPrice(line: String): Pair<String, Int>? {
        val matcher = PRICE_PATTERN.matcher(line)
        val matches = mutableListOf<Pair<String, Int>>()
        
        while (matcher.find()) {
            val raw = matcher.group(1)!!
            
            val lastSep = maxOf(raw.lastIndexOf(','), raw.lastIndexOf('.'))
            
            val priceInt = if (lastSep == -1) {
                raw.replace(".", "").replace(",", "").toIntOrNull()
            } else {
                val intPart = raw.substring(0, lastSep).replace(".", "").replace(",", "")
                val decPart = raw.substring(lastSep + 1)
                "$intPart.$decPart".toDoubleOrNull()?.toInt()
            }
            
            if (priceInt != null) {
                matches.add(raw to priceInt)
            }
        }
        return matches.lastOrNull()
    }

    fun toJson(items: List<ParsedItem>): List<JSONObject> {
        return items.map {
            JSONObject().apply {
                put("item", it.name)
                put("price", it.price)
                put("isTotal", it.isTotal)
            }
        }
    }

    fun parseReceiptTimestamp(date: String?, time: String?): Long? {
        if (date == null) return null
        val combined = if (time != null) "$date $time" else date
        val formats = listOf(
            "dd.MM.yyyy HH:mm", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy",
            "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "dd-MM-yyyy HH:mm", "dd-MM-yyyy"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.isLenient = false
                return sdf.parse(combined)?.time
            } catch (_: Exception) {}
        }
        return null
    }
}

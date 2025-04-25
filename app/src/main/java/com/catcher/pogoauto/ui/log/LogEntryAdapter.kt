package com.catcher.pogoauto.ui.log

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.catcher.pogoauto.R
import java.util.regex.Pattern

/**
 * Adapter for displaying log entries in a RecyclerView with syntax highlighting
 */
class LogEntryAdapter : RecyclerView.Adapter<LogEntryAdapter.LogViewHolder>() {

    // List of log entries
    private val logEntries = mutableListOf<String>()

    // Filtered list of log entries
    private var filteredEntries = mutableListOf<String>()

    // Active filters
    private val activeFilters = mutableSetOf<String>()

    // Category colors
    private val categoryColors = mapOf(
        "ENCOUNTER" to Color.parseColor("#FF9800"),  // Orange
        "CAPTURE" to Color.parseColor("#4CAF50"),    // Green
        "MOVEMENT" to Color.parseColor("#2196F3"),   // Blue
        "ITEM" to Color.parseColor("#9C27B0"),       // Purple
        "NETWORK" to Color.parseColor("#F44336"),    // Red
        "GYM" to Color.parseColor("#795548"),        // Brown
        "POKESTOP" to Color.parseColor("#00BCD4"),   // Cyan
        "FRIEND" to Color.parseColor("#FFEB3B"),     // Yellow
        "COLLECTION" to Color.parseColor("#FF5722"), // Deep Orange
        "RAID" to Color.parseColor("#E91E63"),       // Pink
        "FRIDA" to Color.parseColor("#607D8B"),      // Blue Grey
        "INIT" to Color.parseColor("#8BC34A"),       // Light Green
        "AR" to Color.parseColor("#3F51B5"),         // Indigo
        "UNITY" to Color.parseColor("#CDDC39"),      // Lime
        "FIREBASE" to Color.parseColor("#FF4081"),   // Pink Accent
        "PHYSICS" to Color.parseColor("#009688"),    // Teal
        "AUTH" to Color.parseColor("#673AB7"),       // Deep Purple
        "D" to Color.parseColor("#2196F3"),          // Blue
        "I" to Color.parseColor("#4CAF50"),          // Green
        "W" to Color.parseColor("#FF9800"),          // Orange
        "E" to Color.parseColor("#F44336")           // Red
    )

    // Patterns for matching different log formats
    private val tracePattern = Pattern.compile("\\[TRACE\\]\\[[^\\]]*\\]\\[([^\\]]+)\\]")
    private val logPattern = Pattern.compile("\\[[^\\]]*\\] \\[([^/]+)/[^\\]]*\\]")

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.textView_log_entry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = filteredEntries[position]

        // Apply syntax highlighting
        val spannableString = SpannableString(entry)

        // Check if it's a trace message
        val traceMatcher = tracePattern.matcher(entry)
        if (traceMatcher.find()) {
            val category = traceMatcher.group(1) ?: ""
            val color = categoryColors[category] ?: Color.WHITE

            // Highlight the category
            spannableString.setSpan(
                ForegroundColorSpan(color),
                traceMatcher.start(0),
                traceMatcher.end(0),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            // Check if it's a standard log message
            val logMatcher = logPattern.matcher(entry)
            if (logMatcher.find()) {
                val level = logMatcher.group(1) ?: ""
                val color = categoryColors[level] ?: Color.WHITE

                // Highlight the level
                spannableString.setSpan(
                    ForegroundColorSpan(color),
                    logMatcher.start(0),
                    logMatcher.end(0),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        holder.textView.text = spannableString
    }

    override fun getItemCount() = filteredEntries.size

    /**
     * Update the log entries
     */
    fun updateLogs(logs: String) {
        // Split the logs into individual entries
        val entries = logs.split("\n").filter { it.isNotEmpty() }

        // Update the log entries
        logEntries.clear()
        logEntries.addAll(entries)

        // Apply filters
        applyFilters()

        // Notify the adapter
        notifyDataSetChanged()
    }

    /**
     * Apply filters to the log entries
     */
    private fun applyFilters() {
        if (activeFilters.isEmpty()) {
            // No filters, show all entries
            filteredEntries = logEntries.toMutableList()
        } else {
            // Apply filters
            filteredEntries = logEntries.filter { entry ->
                // Check if the entry matches any of the active filters
                activeFilters.any { filter ->
                    // Check if it's a trace message with the specified category
                    val traceMatcher = tracePattern.matcher(entry)
                    if (traceMatcher.find()) {
                        val category = traceMatcher.group(1) ?: ""
                        return@any category == filter
                    }

                    // Check if it's a standard log message with the specified level
                    val logMatcher = logPattern.matcher(entry)
                    if (logMatcher.find()) {
                        val level = logMatcher.group(1) ?: ""
                        return@any level == filter
                    }

                    // No match
                    false
                }
            }.toMutableList()
        }
    }

    /**
     * Toggle a filter
     */
    fun toggleFilter(category: String) {
        if (activeFilters.contains(category)) {
            activeFilters.remove(category)
        } else {
            activeFilters.add(category)
        }

        // Apply filters
        applyFilters()

        // Notify the adapter
        notifyDataSetChanged()
    }

    /**
     * Clear all filters
     */
    fun clearFilters() {
        activeFilters.clear()

        // Apply filters
        applyFilters()

        // Notify the adapter
        notifyDataSetChanged()
    }

    /**
     * Get all available categories
     */
    fun getCategories(): Set<String> {
        return categoryColors.keys
    }

    /**
     * Check if a category is filtered
     */
    fun isFiltered(category: String): Boolean {
        return activeFilters.contains(category)
    }

    /**
     * Get the number of active filters
     */
    fun getFilterCount(): Int {
        return activeFilters.size
    }
}

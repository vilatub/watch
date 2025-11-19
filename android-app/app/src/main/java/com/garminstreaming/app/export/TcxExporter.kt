package com.garminstreaming.app.export

import com.garminstreaming.app.data.ActivitySession
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Exports activity sessions to TCX (Training Center XML) format
 * Compatible with Garmin Connect and other fitness platforms
 */
object TcxExporter {

    private const val TCX_NAMESPACE = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
    private const val ACTIVITY_EXTENSION_NAMESPACE = "http://www.garmin.com/xmlschemas/ActivityExtension/v2"

    /**
     * Export session to TCX format
     */
    fun export(session: ActivitySession): String {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.newDocument()

        // Root element
        val tcx = doc.createElement("TrainingCenterDatabase")
        tcx.setAttribute("xmlns", TCX_NAMESPACE)
        tcx.setAttribute("xmlns:ax2", ACTIVITY_EXTENSION_NAMESPACE)
        doc.appendChild(tcx)

        // Activities container
        val activities = doc.createElement("Activities")
        tcx.appendChild(activities)

        // Activity
        val activity = doc.createElement("Activity")
        activity.setAttribute("Sport", mapActivityType(session.activityType))
        activities.appendChild(activity)

        // Activity Id (start time in ISO format)
        val id = doc.createElement("Id")
        id.textContent = formatIsoTime(session.startTime)
        activity.appendChild(id)

        // Lap (we create one lap for the entire activity)
        val lap = doc.createElement("Lap")
        lap.setAttribute("StartTime", formatIsoTime(session.startTime))
        activity.appendChild(lap)

        // Lap summary data
        val totalTimeSeconds = doc.createElement("TotalTimeSeconds")
        totalTimeSeconds.textContent = "%.1f".format(session.durationMs / 1000.0)
        lap.appendChild(totalTimeSeconds)

        val distanceMeters = doc.createElement("DistanceMeters")
        distanceMeters.textContent = "%.1f".format(session.distanceMeters)
        lap.appendChild(distanceMeters)

        val calories = doc.createElement("Calories")
        calories.textContent = "0" // We don't track calories yet
        lap.appendChild(calories)

        // Average heart rate
        if (session.avgHeartRate > 0) {
            val avgHr = doc.createElement("AverageHeartRateBpm")
            val avgHrValue = doc.createElement("Value")
            avgHrValue.textContent = session.avgHeartRate.toString()
            avgHr.appendChild(avgHrValue)
            lap.appendChild(avgHr)
        }

        // Max heart rate
        if (session.maxHeartRate > 0) {
            val maxHr = doc.createElement("MaximumHeartRateBpm")
            val maxHrValue = doc.createElement("Value")
            maxHrValue.textContent = session.maxHeartRate.toString()
            maxHr.appendChild(maxHrValue)
            lap.appendChild(maxHr)
        }

        val intensity = doc.createElement("Intensity")
        intensity.textContent = "Active"
        lap.appendChild(intensity)

        val triggerMethod = doc.createElement("TriggerMethod")
        triggerMethod.textContent = "Manual"
        lap.appendChild(triggerMethod)

        // Track
        val track = doc.createElement("Track")
        lap.appendChild(track)

        // Track points
        val trackPoints = session.trackPoints

        for (point in trackPoints) {
            val trackpoint = doc.createElement("Trackpoint")
            track.appendChild(trackpoint)

            // Time
            val time = doc.createElement("Time")
            time.textContent = formatIsoTime(point.timestamp)
            trackpoint.appendChild(time)

            // Position
            if (point.latitude != 0.0 && point.longitude != 0.0) {
                val position = doc.createElement("Position")
                trackpoint.appendChild(position)

                val latDeg = doc.createElement("LatitudeDegrees")
                latDeg.textContent = point.latitude.toString()
                position.appendChild(latDeg)

                val lonDeg = doc.createElement("LongitudeDegrees")
                lonDeg.textContent = point.longitude.toString()
                position.appendChild(lonDeg)
            }

            // Altitude
            if (point.altitude != 0.0) {
                val alt = doc.createElement("AltitudeMeters")
                alt.textContent = "%.1f".format(point.altitude)
                trackpoint.appendChild(alt)
            }

            // Distance (cumulative - we approximate based on track point index)
            // Note: TCX expects cumulative distance at each point
            // We don't store this, so we'll skip it for now

            // Heart rate
            if (point.heartRate > 0) {
                val hr = doc.createElement("HeartRateBpm")
                val hrValue = doc.createElement("Value")
                hrValue.textContent = point.heartRate.toString()
                hr.appendChild(hrValue)
                trackpoint.appendChild(hr)
            }

            // Cadence (TCX has native cadence support)
            if (point.cadence > 0) {
                val cadence = doc.createElement("Cadence")
                cadence.textContent = point.cadence.toString()
                trackpoint.appendChild(cadence)
            }

            // Extensions for power
            if (point.power > 0) {
                val extensions = doc.createElement("Extensions")
                trackpoint.appendChild(extensions)

                val tpx = doc.createElement("ax2:TPX")
                extensions.appendChild(tpx)

                val watts = doc.createElement("ax2:Watts")
                watts.textContent = point.power.toString()
                tpx.appendChild(watts)
            }
        }

        // Lap extensions (average cadence, power)
        if (session.avgCadence > 0 || session.avgPower > 0) {
            val lapExtensions = doc.createElement("Extensions")
            lap.appendChild(lapExtensions)

            val lx = doc.createElement("ax2:LX")
            lapExtensions.appendChild(lx)

            if (session.avgCadence > 0) {
                val avgCad = doc.createElement("ax2:AvgRunCadence")
                avgCad.textContent = session.avgCadence.toString()
                lx.appendChild(avgCad)
            }

            if (session.avgPower > 0) {
                val avgPower = doc.createElement("ax2:AvgWatts")
                avgPower.textContent = session.avgPower.toString()
                lx.appendChild(avgPower)
            }
        }

        // Author
        val author = doc.createElement("Author")
        author.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        author.setAttribute("xsi:type", "Application_t")
        tcx.appendChild(author)

        val name = doc.createElement("Name")
        name.textContent = "Garmin Activity Streaming"
        author.appendChild(name)

        val build = doc.createElement("Build")
        author.appendChild(build)

        val version = doc.createElement("Version")
        build.appendChild(version)

        val versionMajor = doc.createElement("VersionMajor")
        versionMajor.textContent = "1"
        version.appendChild(versionMajor)

        val versionMinor = doc.createElement("VersionMinor")
        versionMinor.textContent = "0"
        version.appendChild(versionMinor)

        val langId = doc.createElement("LangID")
        langId.textContent = "en"
        author.appendChild(langId)

        val partNumber = doc.createElement("PartNumber")
        partNumber.textContent = "000-00000-00"
        author.appendChild(partNumber)

        // Convert to string
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))

        return writer.toString()
    }

    /**
     * Generate filename for export
     */
    fun generateFilename(session: ActivitySession): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
        val date = dateFormat.format(Date(session.startTime))
        return "${session.activityType}_$date.tcx"
    }

    private fun formatIsoTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }

    private fun mapActivityType(type: String): String {
        return when (type.lowercase()) {
            "running" -> "Running"
            "cycling" -> "Biking"
            "walking" -> "Walking"
            "hiking" -> "Hiking"
            "swimming" -> "Other" // TCX doesn't have native swimming support
            else -> "Other"
        }
    }
}

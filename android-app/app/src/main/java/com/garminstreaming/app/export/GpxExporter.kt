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
 * Exports activity sessions to GPX (GPS Exchange Format)
 */
object GpxExporter {

    private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
    private const val GPXTPX_NAMESPACE = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"

    /**
     * Export session to GPX format
     */
    fun export(session: ActivitySession): String {
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.newDocument()

        // Root element
        val gpx = doc.createElement("gpx")
        gpx.setAttribute("version", "1.1")
        gpx.setAttribute("creator", "Garmin Activity Streaming")
        gpx.setAttribute("xmlns", GPX_NAMESPACE)
        gpx.setAttribute("xmlns:gpxtpx", GPXTPX_NAMESPACE)
        doc.appendChild(gpx)

        // Metadata
        val metadata = doc.createElement("metadata")
        gpx.appendChild(metadata)

        val name = doc.createElement("name")
        name.textContent = "${session.activityType.replaceFirstChar { it.uppercase() }} - ${formatDate(session.startTime)}"
        metadata.appendChild(name)

        val time = doc.createElement("time")
        time.textContent = formatIsoTime(session.startTime)
        metadata.appendChild(time)

        // Track
        val trk = doc.createElement("trk")
        gpx.appendChild(trk)

        val trkName = doc.createElement("name")
        trkName.textContent = session.activityType.replaceFirstChar { it.uppercase() }
        trk.appendChild(trkName)

        val trkType = doc.createElement("type")
        trkType.textContent = mapActivityType(session.activityType)
        trk.appendChild(trkType)

        // Track segment
        val trkseg = doc.createElement("trkseg")
        trk.appendChild(trkseg)

        // Track points
        val trackPoints = session.trackPoints
        val heartRateData = session.heartRateData.toMap()

        for (point in trackPoints) {
            val trkpt = doc.createElement("trkpt")
            trkpt.setAttribute("lat", point.latitude.toString())
            trkpt.setAttribute("lon", point.longitude.toString())
            trkseg.appendChild(trkpt)

            // Elevation
            if (point.altitude != 0.0) {
                val ele = doc.createElement("ele")
                ele.textContent = "%.1f".format(point.altitude)
                trkpt.appendChild(ele)
            }

            // Time
            val trkptTime = doc.createElement("time")
            trkptTime.textContent = formatIsoTime(point.timestamp)
            trkpt.appendChild(trkptTime)

            // Extensions (heart rate, cadence, power)
            if (point.heartRate > 0 || point.cadence > 0 || point.power > 0) {
                val extensions = doc.createElement("extensions")
                trkpt.appendChild(extensions)

                val tpExtension = doc.createElement("gpxtpx:TrackPointExtension")
                extensions.appendChild(tpExtension)

                if (point.heartRate > 0) {
                    val hr = doc.createElement("gpxtpx:hr")
                    hr.textContent = point.heartRate.toString()
                    tpExtension.appendChild(hr)
                }

                if (point.cadence > 0) {
                    val cad = doc.createElement("gpxtpx:cad")
                    cad.textContent = point.cadence.toString()
                    tpExtension.appendChild(cad)
                }

                if (point.power > 0) {
                    val power = doc.createElement("gpxtpx:power")
                    power.textContent = point.power.toString()
                    tpExtension.appendChild(power)
                }
            }
        }

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
        return "${session.activityType}_$date.gpx"
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatIsoTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }

    private fun mapActivityType(type: String): String {
        return when (type.lowercase()) {
            "running" -> "9" // Running
            "cycling" -> "1" // Biking
            "walking" -> "17" // Walking
            "hiking" -> "16" // Hiking
            "swimming" -> "26" // Swimming
            else -> "0" // Generic
        }
    }
}

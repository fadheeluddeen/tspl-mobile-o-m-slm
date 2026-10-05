package com.technavious.om15.data.schema

/** Fixed ELV verification checklists copied verbatim from the web app (ELVGapAssessmentTables.tsx). "#" marks a heading row. */
private val ELV_SOURCE: List<Pair<String, String>> = listOf(
    "Addressable Fire Alarm System" to """
#1|Control Panel Status
1.1|The fire alarm control panel (FACP) should be in normal condition (no fault indications).
1.2|Indicators for Power ON (mains + battery backup) must be healthy.
1.3|No trouble, supervisory, or disabled zones should be displayed.
#2|Power Supply Health
2.1|Main power supply and battery backup should be functional.
2.2|Batteries must be tested regularly for capacity and charge/discharge condition.
2.3|Charger status (no fault) must be monitored.
#3|Field Device Condition
3.1|Random check of Smoke detectors, manual call points (MCP), and sounders must be functional
3.2|No contamination, dust, or paint covering detectors.
#4|Alarm Notification
4.1|Sounders, hooters, and visual indicators (strobes) must activate properly during testing.
4.2|PA/voice evacuation integration (if available) should be tested.
#5|Integration
5.1|Integration with BMS, suppression system, and access control should be checked.
5.2|Fire panel must correctly send signals to all connected systems.
#6|Event & Fault Logging
6.1|System must record all fire, fault, and supervisory signals in its event log.
#7|Preventive Maintenance & Testing
7.1|Routine inspections (daily, weekly, monthly, quarterly, annually as per NFPA 72 / IS 2189).
7.2|Functional testing of detectors and MCPs.
""",
    "Public Address System" to """
#1|Power Supply Status
1.1|Main power and UPS/backup power should be available.
1.3|Amplifiers should have no fault indications.
#2|Equipment Healthiness
2.1|Central rack (controller, audio matrix, amplifiers) should be operational without fault alarms.
2.2|No overheating or loose connections in amplifiers and racks.
#3|Mic & Paging Console
3.1|Microphones (desktop, gooseneck, and handheld) should be functional without distortion.
3.2|Priority paging console must override background music during announcements.
#4|Speaker Condition
4.1|All speakers (wall-mounted, ceiling, horn, column) should be audible and distortion-free.
4.2|Balanced sound coverage in all designated areas (no dead zones).
#5|Integration
5.1|PA system must integrate properly with Fire Alarm System for emergency evacuation.
5.2|Automatic pre-recorded announcements should trigger during fire alarms.
#6|Preventive Maintenance & Testing
6.1|Daily: Check system ON status, fault indicators, and console health.
6.2|Weekly: Test microphones and random speaker zones.
6.3|Monthly: Full zone paging test with emergency message.
""",
    "VESDA System" to """
#1|Power Supply Status
1.1|Mains power availability.
1.2|Battery backup condition (voltage, charging, and discharging cycles).
#2|Airflow Integrity
2.1|Sampling pipe-No leaks (or) breakages
2.2|No Airflow fault alarms (high/low flow conditions)
#3|False Alarms
3.1|No false alarms or nuisance signals.
3.2|All pre-set alarm thresholds (Alert, Action, Fire 1, Fire 2) correctly configured and operational.
#4|Network
4.1|VESDA panel communication with Fire Alarm Control Panel (FACP).
4.2|Network/monitoring software connection (if used).
#5|Maintenance & Testing
5.1|Regular cleaning and preventive maintenance carried out.
5.2|Last service/test date within schedule carried out
""",
    "WLD (Water Leakage Detection  System)" to """
#1|Panel Status
1.1|WLD Control Panel should be ON and functional.
1.2|Both primary power supply and UPS/backup power supply should be healthy.
1.3|No fault or trouble indication on the panel display.
#2|Cables
2.1|Sensing cables (for zone-type or addressable systems) must be intact, clean, and dry.
2.2|Leader and jumper cables should be free from physical damage.
#3|Alarm & Monitoring
3.1|Alarm indication (visual and audible) should work.
3.2|System should be integrated with BMS / Fire Panel for remote monitoring.
#4|Maintenance
4.1|Periodic functional testing by simulating water presence on the sensing cable or sensor.
4.2|Regular preventive maintenance to clean sensing cables and check connectors.
""",
    "RRS" to """
#1|Control Panel Status
1.1|Check for normal operation status (no fault/alarm indications).
#2|Transducers
2.1|Confirm all ultrasonic devices are active and emitting signals.
2.2|No damage or loose connections.
#3|Self Testing
3.1|System self-test features (if available) working properly.
#4|Maintenance
4.1|Records maintained in O&M logbook.
""",
    "Fire Extinguisher" to """
#1|Physical Conditon
1.1|Extinguisher body free from dents, corrosion, cracks, or leakage
1.2|Hose/nozzle intact, not blocked or cracked
1.3|Operating instructions legible and facing outward
#2|Pressure Status
2.1|Pressure gauge needle in the green/operable zone
2.2|No signs of pressure loss (for stored-pressure types)
#3|Safety Seal & Pin
3.1|Safety pin in place and sealed
3.2|Tamper seal intact (not broken or missing)
#4|Accessibility & Location
4.1|Easily accessible and unobstructed
4.2|Mounted at correct height
4.3|Fire extinguisher signage visible
#5|Maintenance, Expiry & Compliance
5.1|Annual servicing done by authorized agency
5.2|Monthly visual inspection completed and recorded
5.3|Service tag updated with date and signature
5.4|Extinguisher not expired
5.5|Refilling date valid
""",
    "Access Control System" to """
#1|System Hardware Health
1.1|Door controllers powered ON and functioning
1.2|Power supplies healthy (no alarms / LED normal)
1.3|Backup batteries present and in good condition
1.4|System connected with UPS
1.5|Cabinet locks secured and tamper-proof
#2|Reader & Door Device health
2.1|Card readers (RFID/Biometric) responsive
2.2|LED indicators functioning properly
2.3|Door contacts (magnetic sensors) working
2.4|Exit push buttons / REX sensors operational
2.5|Electric locks / maglocks energize & release correctly
2.6|Door closer operation smooth (no slamming)
#3|Network & Communication
3.1|Controllers communicating with ACS server
3.2|Network switches and ports healthy
3.3|Time synchronization (NTP) accurate
3.4|Controller firmware up to date
#4|Software & Database Health
4.1|Access control software running without errors
4.2|License validity checked
4.3|Access levels configured correctly
4.4|Anti-passback rules functioning (if enabled)
4.5|Event logs recording accurately
4.6|Alarm notifications received at monitoring station
#5|Credential Management
5.1|Active cards/badges issued as per approval
5.2|Deactivated cards removed immediately
5.3|Biometric enrolment authorized and documented
5.4|Visitor cards time-bound and auto-expiry enabled
#6|Testing & Functional Verification
6.1|Authorized access grants entry correctly
6.2|Unauthorized access denied and logged
6.3|Door forced open alarm working
6.4|Door held open alarm working
6.5|Emergency unlock/fire alarm integration verified
""",
    "CCTV" to """
#1|Camera Health, Coverage & Performance
1.1|All cameras are physically intact (no damage / tampering)
1.2|Camera housings, Lenses are clean and dust-free
1.3|No blind spots in critical areas
1.4|Live video is clear
1.5|Infrared (IR) illumination functioning
1.6|Motion detection working (if applicable)
1.7|No blind spots in critical areas
#2|Recording System
2.1|NVR/DVR powered ON and stable
2.2|Recording status active for all cameras
2.3|No recording gaps or missing footage
2.4|Time & date synchronization correct
2.5|Hard disk health status normal
2.6|Storage meets defined retention period (e.g., 30 / 60 / 90 days)
2.7|Old footage auto-deleted as per policy
#3|Network & Connectivity
3.1|Cameras reachable on network
3.2|Network switches healthy
3.3|No packet loss or latency issues
#4|Power, Redundancy, Access Control
4.1|PoE switches functioning properly
4.2|UPS backup available and tested
4.3|Authorized users list reviewed
#5|Monitoring & Operations
5.1|CCTV monitors functional
5.2|No dead screens
5.3|SOPs available for monitoring & incident handling
5.4|Incident playback tested
#6|Maintenance & Documentation
6.1|Preventive maintenance carried out as per schedule
6.2|AMC / warranty valid
6.3|Firmware up to date
6.4|Asset inventory updated
6.5|Maintenance logs maintained
""",
    "GBFSS" to """
#1|General System Status
1.1|Detection system is powered ON and healthy
1.2|No fault / trouble / supervisory alarms present
1.3|System status displayed correctly on Fire Alarm Control Panel (FACP)
1.4|Detectors mounted as per approved drawings
1.5|Detector LED indications functioning
#2|Cross Zone / Double - Knock Logic
2.1|Detection logic configured as per design (Zone-1 & Zone-2)
2.2|Cross-zone dependency tested and verified
2.3|No unintended single-zone discharge
#3|Interface with GBFSS Control Panel
3.1|Detection signals correctly received by GBFSS panel
3.2|Time delay initiated correctly
3.3|Manual abort enabled during discharge countdown
3.4|Audio-visual alarms activated upon detection
3.5|Alarm sound levels adequate inside protected area
3.6|Alarm reset functionality working
#4|Power Supply & Cabling
4.1|Primary power supply healthy
4.2|Battery backup available and charged
4.3|Field cabling intact and fire-rated
4.4|No loose terminations
#5|Integration & Monitoring
5.1|Detection alarms reported to BMS / DCIM / EMS
5.2|Alarms transmitted to security control room
5.3|Remote monitoring signals received correctly
#6|Interlocks & Shutdowns
6.1|HVAC shutdown interlock functional
6.2|Dampers closure verified
6.3|Door hold-open release working
6.4|Integration with BMS verified
#7|Testing & Maintenance
7.1|Detector functional testing completed as per schedule
7.2|Smoke / aerosol test conducted without discharge
7.3|Test records maintained and signed
#8|System Documentation & Compliance
8.1|Cause & Effect matrix available and approved
8.2|OEM manuals and test certificates available
8.3|Approved system design drawings (As-built)
8.4|Room integrity test (Door Fan Test) report available & valid
8.5|Hazard classification and gas concentration calculations approved
#9|Cylinders & Storage Area
9.1|Cylinders secured properly with brackets/chains
9.2|Cylinder quantity as per design
9.3|Cylinder pressure within normal range
9.4|Pressure gauges intact and readable
9.5|Hydrostatic test date valid
9.6|Cylinder labels and gas identification visible
9.7|No physical damage, corrosion, or leakage
9.8|Storage room clean, dry, and ventilated
#10|Release Mechanism & Control Valves
10.1|Solenoid valves physically secure
10.2|Manual release station accessible and labeled
10.3|Manual abort switch installed and functional
10.4|Control valves free from obstruction
#11|Piping & Nozzles
11.1|Piping properly supported and painted
11.2|No visible leakage or corrosion
11.3|Pipe sizes as per approved drawings
11.4|Nozzle orientation and coverage correct
11.5|Discharge paths unobstructed
""",
    "BMS (Building Management System)" to """
#1|System Architecture & Server Health
1.1|BMS servers (Primary / Secondary) are operational
1.2|Redundancy (Failover / Hot-standby) tested and working
1.3|Server CPU, RAM, disk usage within acceptable limits
1.4|Time synchronization (NTP) enabled and correct
1.5|Antivirus and OS security patches up to date
1.6|System backups scheduled and verified
#2|Network & Communication Health
2.1|BMS network switches operational
2.2|IP addressing properly documented
2.3|Field controllers online and communicating
2.4|Protocols functioning correctly (BACnet / Modbus / SNMP / OPC)
2.5|Firewall rules allow required BMS communication
2.6|LAN/Fiber connectivity healthy and redundant
#3|Field Controllers & Panels
3.1|DDC / PLC controllers powered and healthy
3.2|Panel power supplies and fuses intact
3.3|Controller firmware up to date
3.4|Panels properly labeled and locked
3.5|Earth grounding properly connected
3.6|No overheating or physical damage
#4|Sensors & Field Devices
4.1|Temperature sensors accurate and calibrated
4.2|Humidity sensors accurate and calibrated
4.3|Pressure sensors functioning correctly
4.4|Energy meters integrated and logging data
4.5|Actuators (valves, dampers) responding properly
#5|Equipment Monitoring & Control
5.1|HVAC units visible and controllable
5.2|Chillers / AHUs / CRACs monitored
5.3|Pumps and fans start/stop via BMS
5.4|Electrical systems (UPS, DG, PDU) integrated
5.5|Fire alarm interface status visible (monitoring only)
5.6|Setpoints configured as per design
5.7|Manual override functions working
#6|Alarm Management
6.1|Alarm priorities correctly configured
6.2|Critical alarms tested and verified
6.3|Alarm notifications (SMS / Email / BMS pop-up) working
6.4|Alarm acknowledgment and reset working
6.5|Alarm history retained as per policy
#7|Graphics & User interface
7.1|Graphics match site as-built drawings
7.2|Equipment status changes correctly reflected
7.3|Navigation between screens smooth
7.4|Operator permissions configured correctly
7.5|Trend and alarm views accessible
#8|Trending, Reports & Analytics
8.1|Trends enabled for critical parameters
8.2|Trend interval and storage optimized
8.3|Historical data retrievable
8.4|Energy consumption reports generated
8.5|Export of reports functioning
8.6|System uptime and performance reports available
#9|Integration with other systems
9.1|DCIM / CEO integration healthy (if applicable)
9.2|Access Control interface status visible
9.3|Fire Alarm system monitoring interface healthy
9.4|Third-party system tags updating correctly
#10|Documentation & Compliance
10.1|BMS architecture diagrams available
10.2|IO list updated and approved
10.3|Cause & Effect matrix available
10.4|Maintenance logs maintained
10.5|SOPs for operation and alarms available
#11|Preventive Maintenance & Testing
11.1|Daily system health check performed
11.2|Weekly alarm verification completed
11.3|Monthly sensor accuracy check conducted
11.4|Annual BMS audit completed
11.5|Vendor CEO active
"""
)

val ELV_CHECKLIST_ROWS: List<Map<String, String>> = ELV_SOURCE.flatMap { (system, text) ->
    text.trim().lines().filter { it.isNotBlank() }.map { line ->
        val header = line.startsWith("#")
        val (no, desc) = line.removePrefix("#").split("|", limit = 2)
        buildMap {
            put("system", system)
            put("no", no)
            put("description", desc)
            if (header) put(ROW_HEADER, "1")
        }
    }
}

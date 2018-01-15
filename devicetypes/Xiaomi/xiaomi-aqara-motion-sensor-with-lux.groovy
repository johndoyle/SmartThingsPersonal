/**
 *  Xiaomi Aqara Motion Sensor with light sensor
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Based on original DH by Eric Maycock 2015
 * modified 29/12/2016 a4refillpad 
 * Added fingerprinting
 * Added heartbeat/lastcheckin for monitoring
 * Added battery and refresh 
 * Motion background colours consistent with latest DH
 * Fixed max battery percentage to be 100%
 * Added Last update to main tile
 * Added last motion tile
 * Heartdeat icon plus improved localisation of date
 * removed non working tiles and changed layout and incorporated latest colours
 * added experimental health check as worked out by rolled54.Why
 * Added capability of "Illuminance Measurement"
 * Messed with layout 
 * Added Better Battery reporting
 *
 */

metadata {
	definition (name: "Xiaomi Aqara Motion Sensor", namespace: "johndoyle", author: "John Doyle") {
		capability "Motion Sensor"
		capability "Configuration"
		capability "Battery"
		capability "Illuminance Measurement"
		capability "Sensor"
		capability "Refresh"
        capability "Health Check" 
        
        attribute "lastCheckin", "String"
        attribute "lastMotion", "String"
        attribute "Light", "number"

        fingerprint profileId: "0104", deviceId: "0104", inClusters: "0000,FFFF,0406,0400", outClusters: "0000,0019", manufacturer: "LUMI", model: "lumi.sensor_motion.aq2", deviceJoinName: "Xiaomi Aqara Motion Sensor"
		
        fingerprint endpointId: "01", profileId: "0104", deviceId: "0107", inClusters: "0000,FFFF,0406,0400,0500,0001,0003", outClusters: "0000,0019", manufacturer: "LUMI", model: "lumi.sensor_motion.aq2", deviceJoinName: "Xiaomi Aqara Motion Sensor"
    	fingerprint profileId: "0104", deviceId: "0104", inClusters: "0000, 0003, FFFF, 0019", outClusters: "0000, 0004, 0003, 0006, 0008, 0005, 0019", manufacturer: "LUMI", model: "lumi.sensor_motion", deviceJoinName: "Xiaomi Motion"
    	fingerprint profileId: "0104", inClusters: "0000, FFFF, 0406, 0400", outClusters: "0000, 0019", manufacturer: "LUMI", model: "lumi.sensor_motion.aq2", deviceJoinName: "Xiaomi Aqara Motion Sensor"

//		"deviceId": "db643ba2-21ad-4673-883f-e8c8295cb29b" -> 00158D00016569FA
//		Raw Description 01 0104 0104 01 04 0000 FFFF 0406 0400 02 0000 0019
//		0000 - Basic reporting
//		FFFF
//		0406 - Occupancy Sensing
//		0400 - Illuminance Measurement
//		02 - id
//		0000  - Basic reporting
//		0019
//zbjoin: {"dni":"A8C6","d":"00158D00016569FA","capabilities":"80","endpoints":[{"simple":"01 0104 0104 01 04 0000 FFFF 0406 0400 02 0000 0019","application":"03","manufacturer":"LUMI","model":"lumi.sensor_motion.aq2"}],"parent":"005E","joinType":1}
//zbjoin: {"dni":"3C99","d":"00158D00016569FA","capabilities":"80","endpoints":[{"simple":"01 0104 0104 01 04 0000 FFFF 0406 0400 02 0000 0019","application":"03","manufacturer":"LUMI","model":"lumi.sensor_motion.aq2"}],"parent":"0000","joinType":1}

        command "reset"
        command "Refresh"
        
	}

	simulator {
	}

    preferences {
		input "motionReset", "number", 
        	title: "Number of seconds after the last reported activity to report that motion is inactive (in seconds). \n\n(The device will always remain blind to motion for 60seconds following first detected motion. This value just clears the 'active' status after the number of seconds you set here but the device will still remain blind for 60seconds in normal operation.)", 
            description: "", 
            value:120, 
            displayDuringSetup: false
		input "debugOutput", "boolean", 
			title: "Enable debug logging?",
			defaultValue: false,
			displayDuringSetup: true
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"main", type: "generic", width: 6, height: 4){
			tileAttribute("device.illuminance", key: "PRIMARY_CONTROL") {
            	attributeState "illuminance",label:'${currentValue}', backgroundColors:[
                    [value: 0, color: "#000000"],
                    [value: 1, color: "#060053"],
                    [value: 3, color: "#3E3900"],
                    [value: 12, color: "#8E8400"],
                    [value: 24, color: "#C5C08B"],
                    [value: 36, color: "#DAD7B6"],
                    [value: 128, color: "#F3F2E9"],
                    [value: 1000, color: "#FFFFFF"]
				]       
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
    			attributeState("default", label:'Last Update: ${currentValue}',icon: "st.secondary.activity")
            }
		}
        standardTile("motion","device.motion", width: 2, height: 2) {
            state "active",label:'motion',icon:"st.motion.motion.active",backgroundColor:"#53a7c0"
            state "inactive",label:'no motion',icon:"st.motion.motion.inactive",backgroundColor:"#ffffff"
		}
        valueTile("illuminance","device.illuminance", width: 2, height: 2) {
            	state "illuminance",label:'${currentValue} lx', backgroundColors:[
                	[value: 0, color: "#000000"],
                    [value: 1, color: "#060053"],
                    [value: 3, color: "#3E3900"],
                    [value: 12, color: "#8E8400"],
					[value: 24, color: "#C5C08B"],
					[value: 36, color: "#DAD7B6"],
					[value: 128, color: "#F3F2E9"],
                    [value: 1000, color: "#FFFFFF"]
				]
			}
        standardTile("configure", "device.configure", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
	    }       
	    standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
		standardTile("reset", "device.reset", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action:"reset", label: "Reset Motion", icon: "st.motion.motion.active"
		}
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:"",
			backgroundColors:[
				[value: 0,  color: "#c0392b"],
				[value: 25, color: "#f1c40f"],
				[value: 50, color: "#e67e22"],
				[value: 75, color: "#27ae60"]
			]
        }     
        valueTile("lastmotion", "device.lastMotion", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "default", label:'${currentValue}', icon:"st.Entertainment.entertainment15"
        }
        standardTile("refresh", "command.refresh", inactiveLabel: false) {
			state "default", label:'refresh', action:"refresh.refresh", icon:"st.secondary.refresh-icon"
	    }
		standardTile("batteryRuntime", "device.batteryRuntime", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
			state "batteryRuntime", label:'Battery Changed: ${currentValue} - Tap To Reset Date', unit:"", action:"resetBatteryRuntime"
		}
		main(["motion"])
		details(["main", "lastmotion", "motion", "battery",  "refresh", "reset", "configure"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	def value = zigbee.parse(description)?.text
	if (state.debug) log.debug "${device.displayName} Parsing: '${description}'"
	Map map = [:]
   
	if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
    else if (description?.startsWith('illuminance:')) {
		map = parseIlluminanceMessage(description)
	}
	def results = map ? createEvent(map) : null

    def now = new Date().format("yyyy-MM-dd H:mm:ss", location.timeZone)
    sendEvent(name: "lastCheckin", value: now)
    
    if (description?.startsWith('enroll request')) {
    	List cmds = enrollResponse()
        if (state.debug) log.debug "${device.displayName} enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }

	if (state.debug) log.debug "device: '${device}' @ '${now}'"
	return results;
}

private Map parseIlluminanceMessage(String description)
{
	def result = [
		name: 'illuminance',
		value: '--'
	]
	def value = ((description - "illuminance: ").trim()) as Float
	result.value = value
	result.descriptionText = "${device.displayName} ${result.name} was ${result.value}"
	return result;
}


private Map getBatteryResult(rawValue) {
	if (state.debug) log.debug 'Battery'
	if (state.debug) log.debug rawValue
	def result = [
		name: 'battery',
		value: '--',
        unit: "%",
        translatable: true
	]
	def rawVolts = rawValue / 1000
    def maxBattery = state.maxBattery ?: 0
    def minBattery = state.minBattery ?: 0
	
    if (maxBattery == 0 || rawVolts > minBattery)
        state.maxBattery = maxBattery = rawVolts

    if (minBattery == 0 || rawVolts < minBattery)
        state.minBattery = minBattery = rawVolts

    def volts = (maxBattery + minBattery) / 2
    def minVolts = 2.7
    def maxVolts = 3.0
    def pct = (volts - minVolts) / (maxVolts - minVolts)
    def roundedPct = Math.round(pct * 100)
    result.value = Math.min(100, roundedPct)
    result.descriptionText = "${device.displayName}: raw battery is ${rawVolts}v, state: ${volts}v, ${minBattery}v - ${maxBattery}v"
	return result
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
    def i
	if (state.debug) log.debug cluster
	if (shouldProcessMessage(cluster)) {
        switch(cluster.clusterId) {
            case 0x0000:
            	def MsgLength = cluster.data.size();
                for (i = 0; i < (MsgLength-3); i++)
                {
                    if ((cluster.data.get(i) == 0x01) && (cluster.data.get(i+1) == 0x21))  // check the data ID and data type
                    {
                        // next two bytes are the battery voltage.
                        resultMap = getBatteryResult((cluster.data.get(i+3)<<8) + cluster.data.get(i+2))
                    }
                }
            	break
			case 0xFC02:
			if (state.debug) log.debug '${device.displayName}: ACCELERATION'
			break

			case 0x0402:
			if (state.debug) log.debug '${device.displayName}: TEMP'
				// temp is last 2 data values. reverse to swap endian
				String temp = cluster.data[-2..-1].reverse().collect { cluster.hex1(it) }.join()
				def value = getTemperature(temp)
				resultMap = getTemperatureResult(value)
				break
		}
	}
	return resultMap
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	// 0x07 is bind message
	boolean ignoredMessage = cluster.profileId != 0x0104 ||
	cluster.command == 0x0B ||
	cluster.command == 0x07 ||
	(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return !ignoredMessage
}


def configure() {
	state.battery = 0
    if (state.debug) log.debug "${device.displayName}: configuring"
    return zigbee.readAttribute(0x0001, 0x0020) + zigbee.configureReporting(0x0001, 0x0020, 0x21, 600, 21600, 0x01)
}

def refresh() {
    if (state.debug) log.debug "${device.displayName}: refreshing"
    return zigbee.readAttribute(0x0001, 0x0020) 
}

def enrollResponse() {
    if (state.debug) log.debug "${device.displayName}: Enrolling device into the IAS Zone"
    [
        // Enrolling device into the IAS Zone
        "raw 0x500 {01 23 00 00 00}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1"
    ]
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}

	Map resultMap = [:]
    def now = new Date().format("H:mm", location.timeZone)
   
	if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
	}
    else if (descMap.cluster == "0406" && descMap.attrId == "0000") {
    	def value = descMap.value.endsWith("01") ? "active" : "inactive"
	    sendEvent(name: "lastMotion", value: now)
        if (settings.motionReset == null || settings.motionReset == "" ) settings.motionReset = 120
        if (value == "active") runIn(settings.motionReset, stopMotion)
    	resultMap = getMotionResult(value)
    } 
	return resultMap
}
 
private Map parseCustomMessage(String description) {
	Map resultMap = [:]
	return resultMap
}

private Map parseIasMessage(String description) {
    List parsedMsg = description.split(' ')
    String msgCode = parsedMsg[2]
    
    Map resultMap = [:]
    switch(msgCode) {
        case '0x0020': // Closed/No Motion/Dry
        	resultMap = getMotionResult('inactive')
            break

        case '0x0021': // Open/Motion/Wet
        	resultMap = getMotionResult('active')
            break

        case '0x0022': // Tamper Alarm
        	if (state.debug) log.debug '${device.displayName}: motion with tamper alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0023': // Battery Alarm
            break

        case '0x0024': // Supervision Report
        	if (state.debug) log.debug '${device.displayName}: no motion with tamper alarm'
        	resultMap = getMotionResult('inactive')
            break

        case '0x0025': // Restore Report
            break

        case '0x0026': // Trouble/Failure
        	if (state.debug) log.debug '${device.displayName}: motion with failure alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0028': // Test Mode
            break
    }
    return resultMap
}


private Map getMotionResult(value) {
	String descriptionText = value == 'active' ? "${device.displayName} detected motion" : "${device.displayName} motion has stopped"
	def commands = [
		name: 'motion',
		value: value,
		descriptionText: descriptionText
	] 
    return commands
}

private byte[] reverseArray(byte[] array) {
    byte tmp;
    tmp = array[1];
    array[1] = array[0];
    array[0] = tmp;
    return array
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

def stopMotion() {
   sendEvent(name:"motion", value:"inactive")
}
def reset() {
	sendEvent(name:"motion", value:"inactive")
}
def resetBatteryRuntime() {
   	def now = new Date().format("yyyy-MM-dd H:mm:ss", location.timeZone)
    sendEvent(name: "batteryRuntime", value: now)
}
def installed() {
    checkIntervalEvent("installed");
}

def updated() {
    checkIntervalEvent("updated");
}

private checkIntervalEvent(text) {
    // Device wakes up every 1 hours, this interval allows us to miss one wakeup notification before marking offline
    if (state.debug) log.debug "${device.displayName}: Configured health checkInterval when ${text}()"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}
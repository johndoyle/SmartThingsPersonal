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

    	fingerprint profileId: "0104", deviceId: "0104", inClusters: "0000, 0003, FFFF, 0019", outClusters: "0000, 0004, 0003, 0006, 0008, 0005, 0019", manufacturer: "LUMI", model: "lumi.sensor_motion", deviceJoinName: "Xiaomi Motion"
        
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
			state "battery", label:'${currentValue}% battery', unit:""
		}
              
        valueTile("lastmotion", "device.lastMotion", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "default", label:'${currentValue}', icon:"st.Entertainment.entertainment15"
        }
        standardTile("refresh", "command.refresh", inactiveLabel: false) {
			state "default", label:'refresh', action:"refresh.refresh", icon:"st.secondary.refresh-icon"
	   }

		main(["motion"])
		details(["main", "lastmotion", "motion", "battery",  "refresh", "reset", "configure"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	def value = zigbee.parse(description)?.text
	if (state.debug) log.debug "Parsing: '${description}'"
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
        if (state.debug) log.debug "${linkText} enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }

	if (state.debug) log.debug "device: '${device}' @ '${now}'"
	return results;
}

private Map parseIlluminanceMessage(String description)
{
	def linkText = getLinkText(device)
	def result = [
		name: 'illuminance',
		value: '--'
	]
	def value = ((description - "illuminance: ").trim()) as Float
	result.value = value
	result.descriptionText = "${linkText} ${result.name} was ${result.value}"
	return result;
}


private Map getBatteryResult(rawValue) {
	if (state.debug) log.debug 'Battery'
	def linkText = getLinkText(device)
	if (state.debug) log.debug rawValue
	def result = [
		name: 'battery',
		value: '--'
	]
	def volts = rawValue / 1
    def maxVolts = 100
	if (volts > maxVolts) {
		volts = maxVolts
    }
    result.value = volts
	result.descriptionText = "${linkText} battery was ${result.value}%"
	return result
}

private Map parseCatchAllMessage(String description) {
    def linkText = getLinkText(device)
    
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	if (state.debug) log.debug cluster
	if (shouldProcessMessage(cluster)) {
		switch(cluster.clusterId) {
			case 0x0000:
			resultMap = getBatteryResult(cluster.data.get(30))
			break

			case 0xFC02:
			if (state.debug) log.debug '${linkText}: ACCELERATION'
			break

			case 0x0402:
			if (state.debug) log.debug '${linkText}: TEMP'
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
	def linkText = getLinkText(device)
	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
	if (state.debug) log.debug "${linkText}: ${device.deviceNetworkId}"
    def endpointId = 1
    if (state.debug) log.debug "${linkText}: ${device.zigbeeId}"
    if (state.debug) log.debug "${linkText}: ${zigbeeEui}"
	def configCmds = [
			//battery reporting and heartbeat
			"zdo bind 0x${device.deviceNetworkId} 1 ${endpointId} 1 {${device.zigbeeId}} {}", "delay 200",
			"zcl global send-me-a-report 1 0x20 0x20 600 3600 {01}", "delay 200",
			"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1500",

			// Writes CIE attribute on end device to direct reports to the hub's EUID
			"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
			"send 0x${device.deviceNetworkId} 1 1", "delay 500",
	]

	if (state.debug) log.debug "${linkText} configure: Write IAS CIE"
	return configCmds
}

def enrollResponse() {
    def linkText = getLinkText(device)
	if (device.endpointId == null) device.endpointId = 0x01
    log.debug "${linkText}: Enrolling device into the IAS Zone"
	[
			// Enrolling device into the IAS Zone
			"raw 0x500 {01 23 00 00 00}", "delay 200",
			"send 0x${device.deviceNetworkId} 1 1"
	]
}

def refresh() {
	def linkText = getLinkText(device)
	state.debug = ("true" == debugOutput)
    if (state.debug) log.debug "${linkText}: Refreshing Battery"
//    def endpointId = 0x01
//	[
//	    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0000 0x0000", "delay 200"
//	    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0000", "delay 200"
//	] //+ enrollResponse()

	if (device.endpointId == null) device.endpointId = 0x01
    zigbee.configureReporting(0x0001, 0x0021, 0x20, 300, 600, 0x01)
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	//log.debug "Desc Map: $descMap"
 
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
    def linkText = getLinkText(device)
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
        	log.debug '${linkText}: motion with tamper alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0023': // Battery Alarm
            break

        case '0x0024': // Supervision Report
        	log.debug '${linkText}: no motion with tamper alarm'
        	resultMap = getMotionResult('inactive')
            break

        case '0x0025': // Restore Report
            break

        case '0x0026': // Trouble/Failure
        	log.debug '${linkText}: motion with failure alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0028': // Test Mode
            break
    }
    return resultMap
}


private Map getMotionResult(value) {
	def linkText = getLinkText(device)
    //log.debug "${linkText}: motion"
	String descriptionText = value == 'active' ? "${linkText} detected motion" : "${linkText} motion has stopped"
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
def installed() {
	// Device wakes up every 1 hour, this interval allows us to miss one wakeup notification before marking offline
	def linkText = getLinkText(device)
    if (state.debug) log.debug "${linkText}: Configured health checkInterval when installed()"
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}
def updated() {
	// Device wakes up every 1 hours, this interval allows us to miss one wakeup notification before marking offline
	def linkText = getLinkText(device)
    if (state.debug) log.debug "${linkText}: Configured health checkInterval when updated()"
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}
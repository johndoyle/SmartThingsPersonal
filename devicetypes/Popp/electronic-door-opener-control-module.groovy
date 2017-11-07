/**
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
 */
metadata {
	definition (name: "Z-Wave Popp Electronic Door Opener Control Module", namespace: "johndoyle", author: "John Doyle") {
		capability "Actuator"
		capability "Lock"
		capability "Refresh"
		capability "Sensor"
		capability "Battery"
		capability "Health Check"

		command "unlockwtimeout"

		attribute "lastCheckin", "string"
        
       /**
        * zw:Ls type:4001 mfr:0154 prod:0005 model:0001 ver:1.05 zwv:4.38 lib:03 cc:5E,7A,73,5A,98,86,72 sec:30,71,70,59,85,62 role:05 ff:8300 ui:8300
        * Secure Features:
        * 	30: Sensor Binary
        * 	71: Alarm
        * 	70: Configuration
        * 	59: Association Grp Info
        * 	85: Association
        * 	62: Door Lock
        * Insecure Features: 
        * 	5E: 
        * 	7A: Firmware Update Md
        *	73: Powerlevel
        *	5A: Device Reset Locally
        *	98: Security
        *	86: Version
        *	72: Manufacturer Specific
		* Other:
		* 	Zwaveplus Info
		* 	Battery
		*/
        fingerprint mfr: "0154", prod: "0005", model: "0001"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"toggle", type: "generic", width: 6, height: 4){
			tileAttribute ("device.lock", key: "PRIMARY_CONTROL") {
				attributeState "locked", label:'Push', action:"lock.unlock", icon:"st.security.alarm.clear", backgroundColor:"#00A0DC", nextState:"unlocking"
				attributeState "unlocked", label:'unlocked', action:"lock.lock", icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff", nextState:"locking"
				attributeState "unknown", label:"unknown", action:"lock.lock", icon:"st.locks.lock.unknown", backgroundColor:"#ffffff", nextState:"locking"
				attributeState "locking", label:'locking', icon:"st.locks.lock.locked", backgroundColor:"#00A0DC"
				attributeState "unlocking", label:'Ding Dong', icon:"st.security.alarm.alarm", backgroundColor:"#e86d13"
			}
            tileAttribute ("device.battery", key: "SECONDARY_CONTROL") {
                attributeState "battery", label:'Battery: ${currentValue}%', unit:""
            }
		}
    	valueTile("status", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "locked", label:'Ready', icon:"st.security.alarm.clear"
            state "unlocking", label:'Ding Dong', icon:"st.security.alarm.alarm"
		}
		standardTile("refresh", "device.lock", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		valueTile("lastUpdate", "device.lastUpdate", decoration: "flat", width: 2, height: 2){
			state "lastUpdate", label:'Settings\nUpdated\n\n${currentValue}', unit:""
		}
		
		main("status")
		details(["toggle", "battery", "refresh", "lastUpdate"])
	}
    preferences {
        input "lockTimeoutSeconds", "number", title: "Timeout", description: "Lock Timeout in Seconds",
              range: "1..59", displayDuringSetup: false, defaultValue: 1, required: true
		input "wakeUpInterval", "number",
			title: "Minimum Check-in Interval [1-167]\n(1 = 1 Hour)\n(167 = 7 Days)",
			defaultValue: checkinIntervalSetting,
			range: "1..167",
			displayDuringSetup: true, 
			required: false
		input "batteryReportingInterval", "number",
			title: "Battery Reporting Interval [1-24]\n(1 Hour - 24 Hours)",
			defaultValue: batteryReportingIntervalSetting,
			required: false,
			displayDuringSetup: true,
			range: "1..24"
		input "debugOutput", "bool", 
			title: "Enable debug logging?", 
			defaultValue: true, 
			required: false    }
}

import physicalgraph.zwave.commands.doorlockv1.*

def parse(String description) {
	def result = null

	if (description.startsWith("Err 106")) {
		if (state.sec) {
			result = createEvent(descriptionText:description, displayed:false)
		} else {
			result = createEvent(
				descriptionText: "This lock failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.",
				eventType: "ALERT",
				name: "secureInclusion",
				value: "failed",
				displayed: true,
			)
		}
	} else if (description == "updated") {
		return null
	} else {
		def cmd = zwave.parse(description, [ 0x98: 1, 0x72: 2, 0x85: 2, 0x86: 1 ])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	log.debug "\"$description\" parsed to ${result.inspect()}"
	result
}

def lockAndCheck(doorLockMode) {
	secureSequence([
		zwave.doorLockV1.doorLockOperationSet(doorLockMode: doorLockMode),
     ], 4200)
}

def lock() {
	unlockwtimeout()
//    sendEvent(name: "lock", value: "locked", isStateChange: "true", descriptionText: "The Bell was Rung (lock)" )
//	lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_SECURED)
}

def unlock() {
	unlockwtimeout()
//	lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED)
}

def unlockwtimeout() {
	lockAndCheck(DoorLockOperationSet.DOOR_LOCK_MODE_DOOR_UNSECURED_WITH_TIMEOUT)
}

def stateCheck() {
	log.debug "stateCheck()"
	sendHubCommand(new physicalgraph.device.HubAction(secure(zwave.doorLockV1.doorLockOperationGet())))
}

private initializeCheckin() {
	// Set the Health Check interval so that it can be skipped once plus 2 minutes.
	def checkInterval = ((checkinIntervalSettingSeconds * 2) + (2 * 60))
	
	sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}
/**
 * PING is used by Device-Watch in attempt to reach the Device
 * Required for HealthCheck Capability, but doesn't actually do anything because this device sleeps.
 * */
def ping() {
	logDebug "ping()"	
	secure(zwave.batteryV1.batteryGet())
}

// Forces the configuration to be resent to the device the next time it wakes up.
def refresh() {	
	logForceWakeupMessage "The sensor data will be refreshed the next time the device wakes up."
	state.pendingRefresh = true
}

private logForceWakeupMessage(msg) {
	logDebug "${msg}  You can force the device to wake up immediately by pressing the connect button once."
}

/*
def refresh() {
	def cmds = [secure(zwave.doorLockV1.doorLockOperationGet())]
	if (state.assoc == zwaveHubNodeId) {
		log.debug "$device.displayName is associated to ${state.assoc}"
	} else if (!state.associationQuery) {
		log.debug "checking association"
		cmds << "delay 4200"
		cmds << zwave.associationV1.associationGet(groupingIdentifier:2).format()  // old Schlage locks use group 2 and don't secure the Association CC
		cmds << secure(zwave.associationV1.associationGet(groupingIdentifier:1))
		state.associationQuery = now()
	} else if (secondsPast(state.associationQuery, 9)) {
		cmds << "delay 6000"
		cmds << zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
		cmds << secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId))
		cmds << zwave.associationV1.associationGet(groupingIdentifier:2).format()
		cmds << secure(zwave.associationV1.associationGet(groupingIdentifier:1))
		state.associationQuery = now()
	}
	log.debug "refresh sending ${cmds.inspect()}"
	cmds << zwave.batteryV1.batteryGet().format()
    cmds << secure(zwave.doorLockV1.doorLockConfigurationSet(insideDoorHandlesState: 0, lockTimeoutMinutes: 0, lockTimeoutSeconds: lockTimeoutSeconds.toInteger(), operationType: 2, outsideDoorHandlesState: 0))
	cmds
}

def poll() {
	log.debug "poll()"
	def cmds = []
	// Only check lock state if it changed recently or we haven't had an update in an hour
	def latest = device.currentState("lock")?.date?.time
	if (!latest || !secondsPast(latest, 6 * 60) || secondsPast(state.lastPoll, 55 * 60)) {
		cmds << secure(zwave.doorLockV1.doorLockOperationGet())
		state.lastPoll = now()
	} else if (!state.lastbatt || now() - state.lastbatt > 53*60*60*1000) {
		cmds << secure(zwave.batteryV1.batteryGet())
		state.lastbatt = now()  //inside-214
	}
	if (cmds) {
		log.debug "poll is sending ${cmds.inspect()}"
		cmds
	} else {
		// workaround to keep polling from stopping due to lack of activity
		sendEvent(descriptionText: "skipping poll", isStateChange: true, displayed: true)
		null
	}
}
*/

def updated() {	
	// This method always gets called twice when preferences are saved.
	if (!isDuplicateCommand(state.lastUpdated, 3000)) {		
		state.lastUpdated = new Date().time
		logTrace "updated()"

		logForceWakeupMessage "The configuration will be updated the next time the device wakes up."
		state.pendingChanges = true
	}		
}
//def updated() {
	// Device-Watch: Device wakes up every 1 hour, this interval allows us to miss one wakeup notification before marking offline)
//	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    // Set the configuration
//	configure()    
//}

def installed() {
	// Device-Watch: Device wakes up every 1 hour, this interval allows us to miss one wakeup notification before marking offline)
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    // Set the configuration
//	configure()    
}

def configure() {
	logTrace "configure()"
	def cmds = []
	def refreshAll = (!state.isConfigured || state.pendingRefresh )
	
	if (!state.isConfigured) {
		logTrace "Waiting 1 second because this is the first time being configured"		
		sendEvent(getEventMap("lock", "locked", false))		
		cmds << "delay 1000"
	}
	
	configData.sort { it.paramNum }.each { 
		cmds += updateConfigVal(it.paramNum, it.value, refreshAll)	
	}
	
	if (refreshAll || canReportBattery()) {
		cmds << batteryGetCmd()
	}
	
	sendEvent(name: "lastUpdate", value: convertToLocalTimeString(new Date()), displayed: false)
	
	initializeCheckin()
	cmds << wakeUpIntervalSetCmd(checkinIntervalSettingSeconds)
		
	if (cmds) {
		logDebug "Sending configuration to device."
		return delayBetween(cmds, 1000)
	}
	else {
		return cmds
	}	
}
//def configure() {
//    def cmds = []
//        cmds << zwave.doorLockV1.doorLockConfigurationSet(insideDoorHandlesState: 0, lockTimeoutMinutes: 0, lockTimeoutSeconds: lockTimeoutSeconds.toInteger(), operationType: 2, outsideDoorHandlesState: 0).format()
//	delayBetween(cmds, 4200)
//}

private updateConfigVal(paramNum, val, refreshAll) {
	def result = []
	def configVal = state["configVal${paramNum}"]
	
	if (refreshAll || (configVal != val)) {
		result << configSetCmd(paramNum, val)
		result << configGetCmd(paramNum)
	}	
	return result
}

private wakeUpIntervalSetCmd(val) {
	logDebug "wakeUpIntervalSetCmd($val)"
	return zwave.wakeUpV2.wakeUpIntervalSet(seconds:val, nodeid:zwaveHubNodeId).format()
}

private wakeUpNoMoreInfoCmd() {
	return zwave.wakeUpV2.wakeUpNoMoreInformation().format()
}

private batteryGetCmd() {
	return zwave.batteryV1.batteryGet().format()
}

private configGetCmd(paramNum) {
	return zwave.configurationV1.configurationGet(parameterNumber: paramNum).format()
}

private configSetCmd(paramNum, val) {
	return zwave.configurationV1.configurationSet(parameterNumber: paramNum, size: 1, scaledConfigurationValue: val).format()
}

private secure(physicalgraph.zwave.Command cmd) {
//	log.debug "CMD: $cmd"
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private secureSequence(commands, delay=4200) {
	delayBetween(commands.collect{ secure(it) }, delay)
}


private getCheckinIntervalSetting() {
	return safeToInt(settings?.wakeUpInterval, 12)
}

private getCheckinIntervalSettingSeconds() {
	return (checkinIntervalSetting * 60 * 60)
}

private getBatteryReportingIntervalSetting() {
	return safeToInt(settings?.batteryReportingInterval, 12)
}

private getBatteryReportingIntervalSettingSeconds() {
	return (batteryReportingIntervalSetting * 60 * 60)
}

private getDebugOutputSetting() {
	return (settings?.debugOutput == null) ? true : settings?.debugOutput
}

private getEventMap(name, value, displayed=null, desc=null, unit=null) {	
	def isStateChange = (device.currentValue(name) != value)
	displayed = (displayed == null ? isStateChange : displayed)
	def eventMap = [
		name: name,
		value: value,
		displayed: displayed,
		isStateChange: isStateChange
	]
	if (desc) {
		eventMap.descriptionText = desc
	}
	if (unit) {
		eventMap.unit = unit
	}	
	logTrace "Creating Event: ${eventMap}"
	return eventMap
}

private safeToInt(val, defaultVal=-1) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	if (timeZoneId) {
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
	}
	else {
		return "$dt"
	}	
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time) 
}

private logDebug(msg) {
	if (debugOutputSetting) {
		log.debug "$msg"
	}
}
private logTrace(msg) {
	 log.trace "$msg"
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x62: 1, 0x71: 2, 0x80: 1, 0x85: 2, 0x63: 1, 0x98: 1, 0x86: 1])
	// log.debug "encapsulated: $encapsulatedCommand"
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(DoorLockOperationReport cmd) {
	def result = []

	unschedule("followupStateCheck")
	unschedule("stateCheck")
    def map = [ name: "lock" ]
	if (cmd.doorLockMode == 0xFF) {
		map.value = "locked"
    	map.isStateChange = true
    	map.displayed = true
		map.descriptionText = "was Rung"
	} else if (cmd.doorLockMode >= 0x40) {
		map.value = "unknown"
	} else if (cmd.doorLockMode & 1) {
		map.value = "unlocked with timeout"
    	map.isStateChange = true
	} else {
		map.value = "unlocked"
		if (state.assoc != zwaveHubNodeId) {
			log.debug "setting association"
			result << response(secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)))
			result << response(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId))
			result << response(secure(zwave.associationV1.associationGet(groupingIdentifier:1)))
		}
	}
	result ? [createEvent(map), *result] : createEvent(map)
}


def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
	log.debug "AssociationReport"
	def result = []
	if (cmd.nodeId.any { it == zwaveHubNodeId }) {
		state.remove("associationQuery")
		log.debug "$device.displayName is associated to $zwaveHubNodeId"
		result << createEvent(descriptionText: "$device.displayName is associated")
		state.assoc = zwaveHubNodeId
		if (cmd.groupingIdentifier == 2) {
			result << response(zwave.associationV1.associationRemove(groupingIdentifier:1, nodeId:zwaveHubNodeId))
		}
	} else if (cmd.groupingIdentifier == 1) {
		result << response(secure(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)))
	} else if (cmd.groupingIdentifier == 2) {
		result << response(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId))
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	log.debug "Basic Set ${cmd}"

	def result = [ createEvent(name: "lock", value: cmd.value ? "unlocked" : "locked") ]
	def cmds = [
			zwave.associationV1.associationRemove(groupingIdentifier:1, nodeId:zwaveHubNodeId).format(),
			"delay 1200",
			zwave.associationV1.associationGet(groupingIdentifier:2).format()
	]
	[result, response(cmds)]
	log.debug "BasicSet parsed to ${result.inspect()}"
}


def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {	
	def name = configData.find { it.paramNum == cmd.parameterNumber }?.name
	if (name) {	
		def val = cmd.configurationValue[0]
	
		logDebug "${name} = ${val}"
	
		state."configVal${cmd.parameterNumber}" = val
	}
	else {
		logDebug "Parameter ${cmd.parameterNumber}: ${cmd.configurationValue}"
	}
	state.isConfigured = true
	state.pendingRefresh = false	
	state.pendingChanges = false
	return []
}

// Battery powered devices can be configured to periodically wake up and
// check in. They send this command and stay awake long enough to receive
// commands, or until they get a WakeUpNoMoreInformation command that
// instructs them that there are no more commands to receive and they can
// stop listening.
def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
	log.debug "WakeUpNotification"
        def result = [createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)]

        // Only ask for battery if we haven't had a BatteryReport in a while
        if (!state.lastbatt || (new Date().time) - state.lastbatt > 2*60*60*1000) {
                result << response(zwave.batteryV1.batteryGet())
                result << response("delay 1200")  // leave time for device to respond to batteryGet
        }
        result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())
        result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "BatteryReport"
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "$device.displayName has a low battery"
	} else {
		map.value = cmd.batteryLevel
	}
	state.lastbatt = now()
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.debug "ManufacturerSpecificReport"
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
	updateDataValue("MSR", msr)

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
	result
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
	log.debug "VersionReport"
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	if (state.MSR == "003B-6341-5044") {
		updateDataValue("ver", "${cmd.applicationVersion >> 4}.${cmd.applicationVersion & 0xF}")
	}
	def text = "$device.displayName: firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
	createEvent(descriptionText: text, isStateChange: false)
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	logDebug "Unhandled Command: $cmd"
	createEvent(displayed: false, descriptionText: "$device.displayName: $cmd")
	return []
}

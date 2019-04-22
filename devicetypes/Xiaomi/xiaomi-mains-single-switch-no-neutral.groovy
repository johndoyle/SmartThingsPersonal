/**
 *  Copyright 2017 johndoyle
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
 *  20190422 - Updates Based on feedback from battikh - Thanks
 */

metadata {
	definition (name: "Xiaomi Zigbee Mains Toggle Switch", namespace: "johndoyle", author: "John Doyle") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Momentary"
        capability "Switch"
        capability "Temperature Measurement"

		fingerprint profileId: "0104", inClusters: "0000, 0002, 0006"
		fingerprint profileId:"0104", deviceId:"5C23", manufacturer:"LUMI", model:"lumi.ctrl_neutral1"

	}

    // simulator metadata
    simulator {
        // status messages
        status "on": "on/off: 1"
        status "off": "on/off: 0"

        // reply messages
        reply "zcl on-off on": "on/off: 1"
        reply "zcl on-off off": "on/off: 0"
    }
	
    preferences {
        input "tempOffset", "number", title: "Temperature Offset", description: "Adjust temperature in degrees",
              range: "*..*", displayDuringSetup: false
		input title: "", description: "Use to correct any temperature variations by selecting an offset.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		input "debugOutput", "boolean", 
			title: "Enable debug logging?",
            description: "Use to display messages in Live Logging.",
			defaultValue: false,
			displayDuringSetup: true
	}

 // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"rich-control", type: "switch", canChangeIcon: false){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                 attributeState "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
                 attributeState "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
                 attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "offline", label:'${name}', icon:"st.Home.home30", backgroundColor:"#cccccc"
 			}
        }

        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: false) {
            state "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
            state "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
            state "turningOn", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
            state "turningOff", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
            state "offline", label:'${name}', icon:"st.Home.home30", backgroundColor:"#cccccc"
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°', unit:"C",
				backgroundColors:[
					[value: 0, color: "#153591"],
					[value: 5, color: "#1e9cbb"],
					[value: 10, color: "#90d2a7"],
					[value: 15, color: "#44b621"],
					[value: 20, color: "#f1d801"],
					[value: 25, color: "#d04e00"],
					[value: 30, color: "#bc2323"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]  
				]
			)
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main(["switch"])
        details(["rich-control", "temperature", "refresh"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
	def value = zigbee.parse(description)?.text
	Map map = [:]
   
	if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
    else if (description?.startsWith('on/off: ')){
    	def resultMap = zigbee.getKnownDescription(description)
        map = parseCustomMessage(description) 
    }
	def results = map ? createEvent(map) : null
	return results;
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
    
    if (cluster.clusterId == 0x0006 && cluster.command == 0x01){
    	def onoff = cluster.data[-1]
        if (onoff == 1)
        	resultMap = createEvent(name: "switch", value: "on")
        else if (onoff == 0)
            resultMap = createEvent(name: "switch", value: "off")
    }
    else if (cluster.clusterId == 0x0000 && cluster.command == 0x0a){
    	if (state.debug) log.info "Periodic Update"
    }
	return resultMap
}
private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}

	Map resultMap = [:]

    if (descMap.cluster == "0002" && descMap.attrId == "0000") {
    	def temp = convertHexToInt(descMap.value)
        if (tempOffset) {
			temp = (int) temp + (int) tempOffset
		}
    	if (state.debug) log.info "Reported Temp of " + temp + "°" + getTemperatureScale()
		resultMap = createEvent(name: "temperature", value: zigbee.parseHATemperatureValue("temperature: " + temp, "temperature: ", getTemperatureScale()), unit: getTemperatureScale())
	}
    else if (descMap.cluster == "0006" && descMap.endpoint == "02" && descMap.value[15] == "0") {
    	if (state.debug) log.info "Reported Switch Off"
    	resultMap = createEvent(name: "switch", value: "off")
    } 
    else if (descMap.cluster == "0006" && descMap.endpoint == "02" &&  descMap.value[15] == "1") {
    	if (state.debug) log.info "Reported Switch On"
    	resultMap = createEvent(name: "switch", value: "on")
    } 
    else if (descMap.cluster == "0008" && descMap.attrId == "0000") {
    	if (state.debug) log.info "Reported Switch Off"
    	resultMap = createEvent(name: "switch", value: "off")
    } 
	return resultMap
}
private Map parseCustomMessage(String description) {
	def result
	if (!isDuplicateCommand(state.lastUpdated, 2000)) {
        state.lastUpdated = new Date().time
        if (description?.startsWith('on/off: ')) {
            if (description == 'on/off: 0')
                result = createEvent(name: "switch", value: "off")
            else if (description == 'on/off: 1')
                result = createEvent(name: "switch", value: "on")
        }
	}
    return result
}
private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}

def off() {
	if (device.endpointId == null) device.endpointId = 2
   	if (state.debug) log.info "Off()"
    "st cmd 0x${device.deviceNetworkId} 2 6 0 {}"
}

def on() {
	if (device.endpointId == null) device.endpointId = 2
   	if (state.debug) log.info "On()"
    "st cmd 0x${device.deviceNetworkId} 2 6 1 {}"
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
   	if (state.debug) log.info "ping()"
	if (device.endpointId == null) device.endpointId = 2
    return zigbee.readAttribute(0x0006, 0x0000) +
        zigbee.readAttribute(0x0008, 0x0000) +
        zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 600, null) +
        zigbee.configureReporting(0x0008, 0x0000, 0x20, 1, 3600, 0x01)
}

def push() {
   	if (state.debug) log.info "push()"
	if (device.endpointId == null) device.endpointId = 2
	sendEvent(name: "switch", value: "on", isStateChange: true, displayed: false)
	sendEvent(name: "switch", value: "off", isStateChange: true, displayed: false)
	sendEvent(name: "momentary", value: "pushed", isStateChange: true)
}

def refresh() {
	state.debug = ("true" == debugOutput)
	if (state.debug) log.info "Refresh..."
    [
        "st rattr 0x${device.deviceNetworkId} 0x01 0x0002 0x0000", "delay 250", // CurrentTemperature
		"st rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000", "delay 500", // On/Off
		"st rattr 0x${device.deviceNetworkId} 0x02 0x0008 0x0000", "delay 250",  // On/Off
		"st rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000 0x10 0 600 null", "delay 500",
		"st rattr 0x${device.deviceNetworkId} 0x02 0x0008 0x0000 0x20 1 3600 0x01"
    ]
/**
 * 	Capability Returns
 *	 if (device.endpointId == null) device.endpointId = 2
 *   [
 *     	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0000", "delay 250", // ZCLVersion
 *		"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0001", "delay 250", // ApplicationVersion
 *   	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0002", "delay 250", // StackVersion
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0003", "delay 250", // HWVersion
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0004", "delay 250", // ManufacturerName
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0005", "delay 250", // ModelIdentifier
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0006", "delay 250", // DateCode
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0007", "delay 250", // PowerSource
 *   	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0010", "delay 250", // LocationDescription
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 0x0011", "delay 250", // PhysicalEnvironment
 *
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0001 0x0000", "delay 250", // MainsVoltage
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0001 0x0010", "delay 250", // MainsFrequency
 *    	"st rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0001 0x0020", "delay 250", // BatteryVoltage
 *
 *      "st rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000", "delay 500", // On/Off
 *      "st rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000", "delay 250", // On/Off
 *      "st rattr 0x${device.deviceNetworkId} 0x01 0x0002 0x0000", "delay 250" // CurrentTemperature
 *    ]
 * */
}

def configure() {
    if (state.debug) log.info "Configuring Reporting and Bindings."
	if (device.endpointId == null) device.endpointId = 2
    return zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 600, null) +
        zigbee.configureReporting(0x0008, 0x0000, 0x20, 1, 3600, 0x01) +
        zigbee.readAttribute(0x0006, 0x0000) +
        zigbee.readAttribute(0x0008, 0x0000)
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}
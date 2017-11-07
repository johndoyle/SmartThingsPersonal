/**
 *  Copyright 2015 johndoyle
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
 *  18/01/2017 corrected the temperature reading a4refillpad
 */

metadata {
	definition (name: "Xiaomi Zigbee Mains Toggle Switch", namespace: "johndoyle", author: "John Doyle") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Momentary"
        capability "Switch"
        capability "Temperature Measurement"
        capability "Health Check"

		fingerprint profileId: "0104", inClusters: "0000, 0002, 0006"
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
		input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 2 degrees too warm, you'd enter \"-2\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
	}

 // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"rich-control", type: "switch", canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                 attributeState "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
                 attributeState "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
                 attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "offline", label:'${name}', icon:"st.Home.home30", backgroundColor:"#cccccc"
 			}
        }

        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
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
					[value: 12, color: "#153591"],
					[value: 15, color: "#1e9cbb"],
					[value: 18, color: "#90d2a7"],
					[value: 20, color: "#44b621"],
					[value: 22, color: "#f1d801"],
					[value: 24, color: "#d04e00"],
					[value: 26, color: "#bc2323"]
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
//   		log.debug "${resultMap}"
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
    	log.debug "Periodic Update"
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
    	log.debug "Reported Temp of " + temp + "°" + getTemperatureScale()
		resultMap = createEvent(name: "temperature", value: zigbee.parseHATemperatureValue("temperature: " + temp, "temperature: ", getTemperatureScale()), unit: getTemperatureScale())
	}
    else if (descMap.cluster == "0008" && descMap.attrId == "0000") {
    	log.debug "Reported Switch Off"
    	resultMap = createEvent(name: "switch", value: "off")
    } 
	return resultMap
}

def off() {
	if (device.endpointId == null) device.endpointId = 2
    zigbee.off()
}

def on() {
	if (device.endpointId == null) device.endpointId = 2
    zigbee.on()
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	if (device.endpointId == null) device.endpointId = 2
    return zigbee.onOffRefresh()
}

def push() {
   	log.debug "push()"
	if (device.endpointId == null) device.endpointId = 2
	sendEvent(name: "switch", value: "on", isStateChange: true, displayed: false)
	sendEvent(name: "switch", value: "off", isStateChange: true, displayed: false)
	sendEvent(name: "momentary", value: "pushed", isStateChange: true)
}

def refresh() {
	log.debug "Refreshing..."
	if (device.endpointId == null) device.endpointId = 1
    [
/**
 * 	Capability Returns
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
 * */
        "st rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000", "delay 500", // On/Off
        "st rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000", "delay 250", // On/Off
        "st rattr 0x${device.deviceNetworkId} 0x01 0x0002 0x0000", "delay 250" // CurrentTemperature
    ]
}

private Map parseCustomMessage(String description) {
	def result
	if (description?.startsWith('on/off: ')) {
    	if (description == 'on/off: 0')
    		result = createEvent(name: "switch", value: "off")
    	else if (description == 'on/off: 1')
    		result = createEvent(name: "switch", value: "on")
	}
    
    return result
}


private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

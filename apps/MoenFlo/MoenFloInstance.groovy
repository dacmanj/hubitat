/*
    FLO by Moen Device Manager for Hubitat by David Manuel is licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
    Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
    ANY KIND, either express or implied. See the License for the specific language governing permissions and
    limitations under the License.
*/

definition(
	parent: "dacmanj:FLO by Moen Device Manager",
    name: "FLO by Moen Device Instance",
    namespace: "dacmanj",
    author: "David Manuel",
    description: "Child app for managing Moen Flo devices created",
    category: "General",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

import groovy.transform.Field
@Field final String childNamespace = "dacmanj" // namespace of child device drivers
@Field final String baseUrl = 'https://api-gw.meetflo.com/api/v2'


preferences {
	page(name: "mainPage", title: "", install: true, uninstall: true) {
		section(getFormat("title", "Device")) {
            input(
                name: "deviceId", 
                type: "enum", 
                required: true, 
                title: "Device", 
                multiple: false, 
                options: deviceOptions()
            )
            input(
                name: "revertMode", 
                type: "enum", 
                title: "Revert Mode (after Sleep)", 
                options: ["home","away","sleep"], 
                defaultValue: "home"
            )
            input(
                name: "pollingInterval", 
                type: "enum", 
                title: "Polling Interval (in Minutes)",
                options: 5..59,
                defaultValue: 5
            )
            input(
                name: "revertMinutes",
                type: "number",
                title: "Revert Time in Minutes (after Sleep)", 
                defaultValue: 120)
            input (
                name: "logEnable", 
                type: "bool",
                title: "Enable debug logging",
                defaultValue: true
            )
		}
	}
}


def installed() {
	log.info "Installed with settings: ${settings}"
	createDevice()
	initialize()
}


def uninstalled() {
  log.info "uninstalled"
  childDevices.each {
    log.info "Deleting child device: ${it.displayName}"
    deleteChildDevice(it.deviceNetworkId)
  }
}


def updated() {
	log.info "Updated with settings: ${settings}"
  def device = getChildDevice("${deviceId}")
  device.updated()
  if (deviceId && !device) {
    createDevice()
  }
  if (logEnable) runIn(1800,logsOff)

	unsubscribe()
	initialize()
}

def logsOff() {
  logEnable = false
}

def initialize() {
  log.info "initialize()"
	def device = parent?.state?.devicesCache[deviceId]
  def locationName = parent?.state?.locationsCache[device?.location.id].nickname
  log.debug "${locationName} - ${device?.nickname}"
	app.updateLabel("${locationName} - ${device?.nickname}")
	/*
	if (autoRefreshOption == "30") {
		runEvery30Minutes(refreshWrappedLock)
	}
	else if (autoRefreshOption == "10") {
		runEvery10Minutes(refreshWrappedLock)
	}
	else if (autoRefreshOption == "5") {
		runEvery5Minutes(refreshWrappedLock)
	}
	else if (autoRefreshOption == "1") {
		runEvery1Minute(refreshWrappedLock)
	}
	else {
		unschedule(refreshWrappedLock)	
	}*/
}

def deviceOptions() {

  log.info "devicesOptions()"
  def locations = parent?.state?.userData?.locations
  def deviceOptions = [:]
  locations.each { location ->
      location.devices.each { dev ->
          def value = "${location.nickname} - ${dev.nickname}"
          def key = "${dev.id}"
          deviceOptions["${key}"] = value
      }
  }
  deviceOptions = deviceOptions.sort { it.value }
  return deviceOptions
}

void createDevice() {
  log.info "createDevice()"
  def childDevice = getChildDevice(deviceId)
  def driverMap = parent.getDriverMap()
  device = parent?.state?.devicesCache[deviceId]
  if (!childDevice) {
    if (device) {
      try {
        log.debug "Creating new device for ${device.deviceType} ${device.nickname}"
        String devDriver = driverMap[device.deviceType] ?: driverMap["DEFAULT"]
        log.debug "Driver: ${devDriver}"
        String devDNI = "${device?.id}"
        Map devProps = [
          name: (device?.nickname), label: (device?.nickname)
        ]
        //addChildDevice("joelwetzel", "Reliable Lock Virtual Device", "Reliable-${wrappedLock.displayName}", null, [name: "Reliable-${wrappedLock.displayName}", label: "Reliable ${wrappedLock.displayName}", completedSetup: true, isComponent: true])
        childDevice = addChildDevice(childNamespace, devDriver, devDNI, devProps)
      } catch (Exception ex) {
        log.error("Unable to create device for ${device.id}: $ex")
      }
    }
    else {
      log.error("Unable to create device for ${device.nickname} ${device.id}")
    }
  }

  if (!childDevice) {
    log.debug("Failed to setup device ${deviceId}")
  }

}


def getFormat(type, myText=""){
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
  if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


def makeAPIGet(uri, requesType, success_status = [200, 202]) {
  return parent.makeAPIGet(uri, requesType, success_status)
}

def makeAPIPost(uri, body, requestType, successStatus = [200, 202]) {
  return parent.makeAPIPost(uri, body, requestType, successStatus)
}

def getDeviceData(deviceId) {
  return parent.getDeviceData(deviceId)
}

def getDevicesCache() {
  return parent.state.devicesCache
}

def getLocationsCache() {
  return parent.state.locationsCache
}

def getLastDeviceAlert(deviceId) {
  def uri = "${baseUrl}/alerts?isInternalAlarm=false&deviceId=${deviceId}"
  def response = makeAPIGet(uri, "Get Alerts")
  return response.data.items
}

def log(msg) {
	if (enableDebugLogging) {
		log.debug(msg)	
	}
}


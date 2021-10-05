/*
    FLO by Moen Device Manager for Hubitat by David Manuel is licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
    Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
    ANY KIND, either express or implied. See the License for the specific language governing permissions and
    limitations under the License.
*/

definition(
	parent: "dacmanj:Moen FLO Device Manager",
    name: "Moen FLO Smart Water Detector Instance",
    namespace: "dacmanj",
    author: "David Manuel",
    description: "Child app for managing Moen FLO Smart Water Detectors",
    category: "General",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

import groovy.transform.Field
@Field final String childNamespace = "dacmanj" // namespace of child device drivers
@Field final String baseUrl = 'https://api-gw.meetflo.com/api/v2'

preferences {
  page(name: "mainPage")
  page(name: "settingsPage")
}


def deviceSelector() {
    def label = getApp().label ? getApp().label : "Location Setup"
  	dynamicPage(name: "mainPage", title: "", nextPage: 'settingsPage', install: false, uninstall: true) {
		  section(getFormat("title", label)) {
        input(
          name: "deviceId", 
          type: "enum", 
          required: true, 
          title: "Device", 
          multiple: false, 
          options: deviceOptions()
        )
      }
    }
}

def mainPage() {
  if (!deviceId) {
    deviceSelector()
  } else {
    settingsPage()
  }
}

def settingsPage() {
  initialize()
  def label = getApp().label ? getApp().label : "Location Setup"
	dynamicPage(name: "settingsPage", title: "", install: true, uninstall: true) {
		section(getFormat("title", label)) {
      paragraph("<b>Device Information</b><ul><li><b>Device Type:</b> ${state.deviceInfo?.deviceType}</li><li><b>Device Model: </b>${state.deviceInfo?.deviceModel}</li></ul>")
      input(
        name: "pollingInterval", 
        type: "enum", 
        title: "Polling Interval (in Minutes)",
        options: 5..59,
        defaultValue: 5
      )
      input (
        name: "logEnable", 
        type: "bool",
        title: "Enable Device Debug Logging",
        defaultValue: true
      )
		}
    if (getChildDevices().size() > 0) {
      section("Linked Device") {
        paragraph(displayListOfInstalledDevices())
        paragraph("To remove the device, click Remove below.")
      }
    }
	}
}


def installed() {
	log.info "Installed with settings: ${settings}"
	initialize()
	createDevice()
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
  initialize()
  def childDevice = getChildDevice("${deviceId}-${getApp().id}")
  if (childDevice) {
    childDevice.updated()
  } else {
    createDevice()
  }
  if (logEnable) runIn(1800,logsOff)
	unsubscribe()
}

def logsOff() {
  logEnable = false
}

def initialize() {
  log.info "initialize()"
  def deviceInfo = parent?.state?.devicesCache[deviceId]
  def locationsCache = parent?.state?.locationsCache
  def location = locationsCache[deviceInfo?.location?.id]
  def locationName = location?.nickname
  if (deviceInfo) {
    log.debug "${locationName} - ${deviceInfo?.nickname}"
    app.updateLabel("${locationName} - ${deviceInfo?.nickname} (${deviceInfo?.deviceType})")
    state.deviceInfo = deviceInfo
    state.location = location
  }
}

def displayListOfInstalledDevices() {
    return "<ul>"+getChildDevices().collect {
        "<li><a href='/device/edit/$it.id'>$it.label</a></li>"
    }.join("\n")+"</ul>"
}

def deviceOptions() {
  def locations = parent?.state?.userData?.locations
  def deviceOptions = [:]
  locations.each { location ->
      location.devices.each { dev ->
        if (dev?.deviceType == "puck_oem") {
          def value = "${location.nickname} - ${dev.nickname} (${dev.deviceType})"
          def key = "${dev.id}"
          deviceOptions["${key}"] = value
        }
      }
  }
  deviceOptions = deviceOptions.sort { it.value }
  return deviceOptions
}

def createDevice() {
  if (deviceId) {
    log.info "createDevice()"
    def appId = getApp().id
    String devDNI = "${deviceId}-${appId}"
    def childDevice = getChildDevice(devDNI)
    def driverMap = parent.getDriverMap()
    def deviceType = state.deviceInfo?.deviceType
    def locationId = state.deviceInfo?.location?.id
    def nickname = state.deviceInfo?.nickname
    if (!childDevice) {
      try {
        log.debug "Creating new device for ${deviceType} ${nickname}"
        deviceType = "puck_oem" //temp
        String devDriver = driverMap[deviceType] ?: driverMap["puck_oem"]
        log.debug "Driver: ${devDriver}"
        Map devProps = [
          name: (nickname), 
          label: (nickname),
          isComponent: true
        ]
        childDevice = addChildDevice(childNamespace, devDriver, devDNI, devProps)
        return childDevice
      } catch (Exception ex) {
        log.error("Unable to create device for ${deviceId}: $ex")
      }
    }
  }

  if (!childDevice) {
    if (logEnable) log.debug("Failed to setup device ${deviceId}")
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


def getLocationsCache() {
  return parent.state.locationsCache
}

def getLocationData(locationId) {
  return parent.getLocationData(locationId)
}

def getDeviceData(deviceId) {
  return parent.getDeviceData(deviceId)
}

def getDevicesCache() {
  return parent.state.devicesCache
}


/**
 * Moen Flo Manager for Hubitat By David Manuel
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and 
 * limitations under the License.
 */

definition(
	parent: "dacmanj:Moen FLO Device Manager",
    name: "Moen FLO Smart Shutoff Instance",
    namespace: "dacmanj",
    author: "David Manuel",
    description: "Child app for managing Moen FLO devices created",
    category: "General",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

import groovy.transform.Field
@Field final String DEFAULT_NAME_TEMPLATE = '${location} - ${nickname} - ${deviceType} - ${deviceModel} - fw ${fwVersion}'

preferences {
  page(name: "mainPage")
  page(name: "settingsPage")
}


def deviceSelector() {
    def label = getApp().label ? getApp().label : "Device Setup"
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
  def label = getApp().label ? getApp().label : "Device Setup"
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
      paragraph("<b>Polling Start Minute:</b>&nbsp;<span>${state.startMinute}</span>")
      input (
        name: "logEnable", 
        type: "bool",
        title: "Enable Device Debug Logging",
        defaultValue: true
      )
      input (
        name: "deviceNameTemplate", 
        type: "text",
        title: "Device Name Template",
        defaultValue: DEFAULT_NAME_TEMPLATE
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
	if (logEnable) log.info "Installed with settings: ${settings}"
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


def poll() {
  def childDevice = getChildDevice("${deviceId}-${getApp().id}")
  if (childDevice) {
    childDevice.poll()
  }
}


def updated() {
  log.info "Updated with settings: ${settings}"
  initialize()
  unschedule()
  def childDevice = getDevice()
  if (childDevice) {
    updateDeviceAndAppName()
    childDevice.updated()
  } else {
    createDevice()
  }
  if (logEnable) runIn(1800,logsOff)
	unsubscribe()
}

def logsOff() {
  app.updateSetting("logEnable", false)
}

def deviceName() {
  def template = deviceNameTemplate ?: DEFAULT_NAME_TEMPLATE
  if (state.deviceInfo && state.location){
    def binding = state.deviceInfo?.clone()
    binding['location'] = state.location?.nickname
    def deviceName = template.replaceAll(/\$\{(\w+)\}/) { k -> binding[k[1]] ?: k[0] }
    return deviceName
  }
}

def deviceDNI() {
  return "${deviceId}-${getApp().id}"
}

def getDevice() {
  def childDevice = getChildDevice(deviceDNI())
  return childDevice
}

def updateDeviceAndAppName() {
  def childDevice = getDevice()
  if (childDevice && deviceName()) {
    childDevice.setName(deviceName()) 
    app.updateLabel(deviceName())
  }
}

def initialize() {
  log.info "initialize()"
  state.startMinute = parent.getStartMinute(state.startMinute, pollingInterval)
  def deviceInfo = parent?.state?.devicesCache[deviceId]
  def locationsCache = parent?.state?.locationsCache
  def location = locationsCache[deviceInfo?.location?.id]
  def locationName = location?.nickname
  if (deviceInfo) {
    log.debug "${locationName} - ${deviceInfo?.nickname}"
    app.updateLabel(deviceName())
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
        if (dev?.deviceType == "flo_device_v2") {
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
    def childDevice = getDevice()
    def driverMap = parent.getDriverMap()
    def deviceType = state.deviceInfo?.deviceType
    def locationId = state.deviceInfo?.location?.id
    def nickname = state.deviceInfo?.nickname
    if (!childDevice) {
      try {
        log.debug "Creating new device for ${deviceType} ${nickname}"
        String devDriver = driverMap[deviceType] ?: driverMap["flo_device_v2"]
        log.debug "Driver: ${devDriver}"        
        Map devProps = [
          name: (deviceName()), 
          label: (nickname),
          isComponent: true
        ]
        String devDNI = "${deviceId}-${appId}"
        childDevice = addChildDevice(getParent().getChildNamespace(), devDriver, devDNI, devProps)
        return childDevice
      } catch (Exception ex) {
        log.error("Unable to create device for ${deviceId}: $ex")
      }
    }

    if (!childDevice) {
      if (logEnable) log.debug("Failed to setup device ${deviceId}")
    }
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

def getCronString() {
  if (logEnable) log.debug("calling cronstring with ${state.startMinute} ${pollingInterval}")
  return parent.getCronString(state.startMinute, pollingInterval)
}

def getUserData() {
  return parent.state.userData
}

def getUnits() {
  return parent.getUnits()
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


def getLocationData(locationId) {
  return parent.getLocationData(locationId)
}

def getLastDeviceAlert(deviceId) {
  def uri = "/alerts?isInternalAlarm=false&deviceId=${deviceId}"
  def response = makeAPIGet(uri, "Get Alerts")
  return response.data.items
}


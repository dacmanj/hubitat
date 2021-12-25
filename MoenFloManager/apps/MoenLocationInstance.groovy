/**
 * Moen Flo Manager for Hubitat 
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and 
 * limitations under the License. 
 */

definition(
	parent: "dacmanj:Moen FLO Device Manager",
    name: "Moen FLO Location Instance",
    namespace: "dacmanj",
    author: "David Manuel",
    description: "Child app for managing Moen FLO locations",
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


def locationSelector() {
    def label = getApp().label ? getApp().label : "Location Setup"
  	dynamicPage(name: "mainPage", title: "", nextPage: 'settingsPage', install: false, uninstall: true) {
		  section(getFormat("title", label)) {
        input(
          name: "locationId", 
          type: "enum", 
          required: true, 
          title: "Location", 
          multiple: false, 
          options: locationOptions()
        )
      }
    }
}

def mainPage() {
  if (!locationId) {
    locationSelector()
  } else {
    settingsPage()
  }
}

def settingsPage() {
  initialize()
  def label = getApp().label ? getApp().label : "Location Setup"
	dynamicPage(name: "settingsPage", title: "", install: true, uninstall: true) {
		section(getFormat("title", label)) {
      paragraph("<b>Location Information</b><ul><li><b>Nickname:</b> ${state.location?.nickname}</li><li><b>Address: </b>${state.location?.address}</li></ul>")
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
      input(
        name: "subscribeHSMAway", 
        type: "bool",
        title: "Set this FLO Location to Away when Hubitat Safety Montor set to Armed Away (and revert to home when HSM not away)?",
        defaultValue: false
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
        paragraph(displayListOfChildDevices())
        paragraph("To remove the device, click Remove below.")
      }
    }
	}
}


def installed() {
	log.info "Installed with settings: ${settings}"
  if (locationId) {
	  updated()
  }
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
  def childDevice = getChildDevice("${locationId}-${getApp().id}")
  if (childDevice) {
    childDevice.updated()
  } else {
    createDevice()
  }
  if (logEnable) runIn(1800,logsOff)
	unsubscribe()
  if (subscribeHSMAway) {
      subscribe (location, "hsmStatus", handleHSMStatusUpdate)
  }
}

def handleHSMStatusUpdate(evt) {
  if (logEnabled) {
    log.info "HSM Alert: $evt.value"
  }
  if (subscribeHSMAway) {
    switch (evt.value){
      case "armedAway":
        getChildDevices().collect { it.away() }
      break 
      default:
        getChildDevices().collect { it.home() }
    }
  }
}

def logsOff() {
  app.updateSetting("logEnable", false)
}

def initialize() {
  log.info "initialize()"
  if (locationId) {
    def locationsCache = parent?.state?.locationsCache
    def location = locationsCache[locationId]
    if (!location && locationId) parent.getLocationData(locationId)
    def locationName = location?.nickname
    if (location) {
      def label = "${locationName}"
      app.updateLabel(label)
    } else {
      log.error "Invalid locationid: ${locationId}"
    }
    state.location = location
  }
}

def displayListOfChildDevices() {
    return "<ul>"+getChildDevices().collect {
        "<li><a href='/device/edit/$it.id'>$it.label</a></li>"
    }.join("\n")+"</ul>"
}

def locationOptions() {
  def locations = parent?.state?.userData?.locations
  def locationOptions = [:]
  locations.each { location ->
    locationOptions["${location.id}"] = "${location.nickname}"
  }
  locationOptions = locationOptions.sort { it.value }
  return locationOptions
}

def createDevice() {
  log.info "createDevice()"
  def appId = getApp().id
  def devDNI = "${locationId}-${appId}"
  def childDevice = getChildDevice(devDNI)
  def driverMap = parent.getDriverMap()
  def deviceType = "location"
  def nickname = state.location?.nickname
  if (!childDevice) {
    try {
      log.debug "Creating new device for ${deviceType} ${nickname}"
      String devDriver = driverMap[deviceType]
      log.debug "Driver: ${devDriver}"
      Map devProps = [
        name: (nickname), 
        label: (nickname),
        isComponent: true
      ]
      childDevice = addChildDevice(childNamespace, devDriver, devDNI, devProps)
      return childDevice
    } catch (Exception ex) {
      log.error("Unable to create device for ${locationId}: $ex")
    }
  }

  if (!childDevice) {
    if (logEnable) log.debug("Failed to setup device ${locationId}")
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

def getUserData() {
  return parent.state.userData
}

def getUnits() {
  return parent.getUnits()
}

def getLocationsCache() {
  return parent.state.locationsCache
}

def getLocationData(locationId) {
  return parent.getLocationData(locationId)
}
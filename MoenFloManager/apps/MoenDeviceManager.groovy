/**
 * Moen Flo Manager for Hubitat By David Manuel
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 *  Revision History
 *  2021-10-06    Initial Release of App - Forked standalone driver moved API and configuration to App
 *
 * 
 */

definition(
    name: "Moen FLO Device Manager",
    namespace: "dacmanj",
    author: "David Manuel",
    description: "Moen FLO Device Manager",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true
)

preferences {
  page(name: "mainPage")
  page(name: "deviceInstaller")
}

import groovy.transform.Field
@Field final String childNamespace = "dacmanj" // namespace of child device drivers
@Field final Map driverMap = [
   "flo_device_v2":       "Moen FLO Smart Shutoff",
   "puck_oem":            "Moen FLO Smart Water Detector",
   "location":            "Moen FLO Location",
   "DEFAULT":             "Moen FLO Smart Shutoff"
]

@Field final Map appMap = [
   "flo_device_v2":       "Moen FLO Smart Shutoff Instance",
   "puck_oem":            "Moen FLO Smart Water Detector Instance",
   "location":            "Moen FLO Location Instance",
   "DEFAULT":             "Moen FLO Smart Shutoff Instance"
]

@Field final String baseUrl = 'https://api-gw.meetflo.com/api/v2'
@Field final String authUrl = 'https://api.meetflo.com/api/v1/users/auth'

def mainPage() {
  initialize()
  loginPage()
}

def loginPage() {
    if (state.authenticated) {
        return deviceInstaller()
    }
    dynamicPage(name: "mainPage", title: "Manage Your Moen Flo Devices", install: false, uninstall: true) {
        section("<b>Credentials<b>") {
            preferences {        
              input(name: "username", type: "string", title:"User Name", description: "User Name", required: true, displayDuringSetup: true)
              input(name: "password", type: "password", title:"Password", description: "Password", required: true, displayDuringSetup: true)
              input(name: "btnLogin", type: "button", title: "Login")
            }
        }
    }
}

def deviceInstaller() {
  if (!state.authenticated) {
    return loginPage()
  }

  dynamicPage(name: "deviceInstaller", title: "Manage Your Moen Flo Devices", install: true, uninstall: true) {
    section("") {
      input(name: "btnSetupAllDevices", type: "button", title: "Setup All Devices")
      paragraph("<i>Creates devices for all Devices that don't already have devices installed.</i>")
      paragraph(displayUnrecognizedDevices())
      paragraph("<b>Make sure to click DONE when your devices have been configured.</b>")
    }
    section("<h3>Smart Shutoff Valves</h3>") {
      app(name: "shutoffApps", appName: appMap["flo_device_v2"], namespace: "dacmanj", title: "<b>Add Shutoff Valve</b>", multiple: true)
    }
    section("<h3>Water Detectors</h3>") {
      app(name: "waterDetectorApps", appName: appMap["puck_oem"], namespace: "dacmanj", title: "<b>Add Water Detector</b>", multiple: true)
    }
    section("<h3>Locations</h3>") {
      app(name: "locationApps", appName: appMap["location"], namespace: "dacmanj", title: "<b>Add Location</b>", multiple: true)
    }
    section("<b>Settings</b>") {
      input(name: 'logEnable', type: "bool", title: "Enable App (and API) Debug Logging?", required: false, defaultValue: true, submitOnChange: true)
      input(name: "btnLogout", type: "button", title: "Logout")
    }
  }
}

def installed() {
  log.debug "installed"
  initialize()
}

def updated() {
  log.debug "updated"
  initialize()
}

def logsOff() {
  logEnable = false
}

def initialize() {
  log.debug "initialize"
  if (!state.authenticated) {
    authenticate()
  }
  if (state.token) {
    getUserInfo()
    discoverDevices()
  }
  if (logEnable) {
    log.info "There are ${childApps.size()} child apps"
    childApps.each { child ->
      log.info "Child app: ${child.label}"
      log.info "Child app: ${child.id}"
    }
  }
  unschedule()
  runIn(1800, logsOff)
}

def logout() {
  state.token = null
  state.authenticated = false
  state.authenticationFailures = 0
  state.devicesCache = null
  state.locationsCache = null
  state.userData = null
  log.debug "logout()"
  loginPage()
}

def uninstalled() {
  log.debug "uninstalled"
  childApps.each { child ->
    log.info "Deleting child app: ${child.label}"
    deleteChildApp(child.id)
  }
}

def getDriverMap() {
    return driverMap;
}

def authenticate() {
    if (logEnable) log.debug("authenticate()")
    if (logEnable) log.debug("failure count: ${state.authenticationFailures}")
    def uri = authUrl
    if (!password) {
        log.error("Login Failed: No password")
    }
    else {
        if (state.authenticationFailures > 3) {
            log.error("Failed to authenticate after three tries. Giving up. Log out and back in to retry.")
        }
        
        def body = [username:username, password:password]
        def headers = [:]
        headers.put("Content-Type", "application/json")

        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { response -> def msg = response?.status
                if (logEnable) log.debug("Login received response code ${response?.status}")
                    if (response?.status == 200) {
                        msg = "Success"
                        state.token = response.data.token
                        state.userId = response.data.tokenPayload.user.user_id
                        state.authenticated = true
                        state.authenticationFailures = 0
                    }
                    else {
                        log.error "Login Failed: (${response.status}) ${response.data}"
                        state.authenticated = false
                        state.authentcationFailures += 1
                    }
              }
        }
        catch (Exception e) {
            log.error "Login exception: ${e}"
            log.error "Login Failed: Please confirm your Flo Credentials"
            state.authenticated = false
            state.authentcationFailures += 1
        }
    }
}

def getUserInfo() {
  def userId = state.userId
  def uri = "${baseUrl}/users/${userId}?expand=locations,alarmSettings"
  def response = makeAPIGet(uri, "Get User Info")
  state.userData = response.data
}

def discoverDevices() {
  def locations = []
  Map devicesCache = [:]
  Map locationsCache = [:]
  getUserInfo()
  def userLocations = state.userData?.locations
  if(userLocations) {
    userLocations.each { location ->
      def locationDetail = getLocationData(location.id)
      def devices = locationDetail?.devices
      if (devices) {
        locations.add(locationDetail)
        devices.each{ d ->
          devicesCache << [(d.id): (d)]
        }
        locationsCache << [(locationDetail.id): (locationDetail)]
      }
    }
  } else {
    if (logEnable) log.debug "No locations in user data"
  }
  state.userData?.locations = locations
  state.devicesCache = devicesCache
  state.locationsCache = locationsCache
}

def getLocationData(locationId) {
  def uri = "${baseUrl}/locations/${locationId}?expand=devices"
  def response = makeAPIGet(uri, "Get Location Info")
  return response.data
}

def getDeviceData(deviceId) {
  def uri = "${baseUrl}/devices/${deviceId}"
  def response = makeAPIGet(uri, "Get Device")
  return response.data
}

def makeAPIGet(uri, request_type, success_status = [200, 202]) {
    if (logEnable) log.debug "makeAPIGet: ${request_type} ${uri}"
    def token = state.token
    if (!token || token == "") authenticate();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:]
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", token)

        try {
            httpGet([headers: headers, uri: uri]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.error "${request_type} Failed (${response.status}): ${response.data}"
                }
              }
        }
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage()?.contains("Forbidden") || e.getMessage()?.contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception..."
            } else {
                log.error "${request_type} Failed ${e}"
            }
            state.token = null
            authenticate()

        }
        tries++

    }
    return response
}

def makeAPIPost(uri, body, request_type, success_status = [200, 202]) {
    if (logEnable) log.debug "makeAPIGet: ${request_type} ${uri}"
    def token = state.token
    if (!token || token == "") authenticate();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:]
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", token)

        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.debug "${request_type} Failed (${resp.status}): ${resp.data}"
                }
            }
        }
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception... Refreshing token..."
                authenticate()
            }
        }
        tries++

    }
    return response
}

def setupAllDevices() {
  if (logEnable) log.debug("setupAllDevices()")
  installedDevices = []
  getChildApps().each { app ->
    app.getChildDevices().each { dev ->
      if (dev.deviceNetworkId && dev.deviceNetworkId.length() >= 36) {
        installedDevices.add(dev?.deviceNetworkId?.substring(0,36))
      }
    }
  }
  
  if (logEnable && installedDevices) {
    log.debug("Found Installed Devices: ${installedDevices}")
  }
  
  state.devicesCache.each{ did, dev ->
    def isInstalled = (installedDevices.find({d -> d == did}))
    log.debug "checking ${did}"
    if(isInstalled) {
      if (logEnable) log.debug "Skipping Install of ${dev.nickname} (already installed)"
    } else {
      if (logEnable) log.debug "Installing ${dev.nickname} ${dev.deviceType}"
      def appInstanceType = appMap[dev.deviceType]
      if (logEnable) log.debug "Creating ${appInstanceType} app for ${dev.nickname}"
      def locationName = state.locationsCache[dev?.location?.id]?.nickname
      def appLabel = "${locationName} - ${dev.nickname} (${dev.deviceType})"
      try {
        childApp = addChildApp("dacmanj", appInstanceType, appLabel)
      } catch(e) {
        log.error "Install error ${e.getMessage()}"
      }
      childApp.updateSetting("deviceId", did)
      childApp.updateSetting("pollingInterval", 10)
      childApp.updated()
    }
  }

  state.locationsCache.each { lid, loc ->
    def isInstalled = (installedDevices.find({d -> d == lid}))
    log.debug "checking ${lid} got ${isInstalled}"

    if(isInstalled) {
      if (logEnable) log.debug "Skipping Install of ${loc.nickname} (already installed)"
      log.debug(isInstalled)
    } else {
      if (logEnable) log.debug "Installing ${loc.nickname} (location)"
      def appInstanceType = appMap["location"]
      if (logEnable) log.debug "Creating ${appInstanceType} app for ${loc.nickname}"
      def appLabel = "${loc.nickname} (Location)"
      try {
        childApp = addChildApp("dacmanj", appInstanceType, appLabel)
      } catch(e) {
        log.error "Install error ${e.getMessage()}"
      }
      childApp.updateSetting("locationId", lid)
      childApp.updateSetting("pollingInterval", 10)
      childApp.updated()
    }

  }
}

def displayUnrecognizedDevices() {
  if (logEnable) log.debug "displayUnrecognizedDevices()"
  def unrecognizedDevices = []
  state.devicesCache.each { id, d ->
    if (!driverMap.containsKey(d.deviceType)) {
      unrecognizedDevices.add(d)
    } 
  }
  if (unrecognizedDevices) {
    def unrecognizedDevicesError = "<p style=\"color: red;\">Unrecognized devices detected, please send these to <a href=\"mailto:david@dcmanjr.com\">David Manuel</a>:</p>"
    return unrecognizedDevicesError + "<ul>"+unrecognizedDevices.collect {
    "<li>${it.nickname} (${it.deviceType}) </a></li>"
    }.join("\n")+"</ul>"
  } else {
    return ""
  }  
}

void appButtonHandler(btn) {
  switch(btn) {
    case "btnLogout":
      logout()
      break
    case "btnLogin":
      authenticate()
      discoverDevices()
      deviceInstaller()
      break
    case "btnSetupAllDevices":
      setupAllDevices()
      break
    default:
      log.warn "Unhandled app button press: $btn"
  }
}
